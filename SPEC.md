# SPEC — Replaybox V1

Spécification technique du MVP. Le produit : PRD.md. Le vocabulaire : CONTEXT.md (obligatoire).
Les décisions structurantes : docs/adr/.

## Stack

Kotlin, Jetpack Compose for TV, Media3/ExoPlayer, Room, OkHttp + kotlinx.serialization.
minSdk 26 (Android TV 8.0) — appareil de référence : Bbox Android TV 11 (build CALIFORNIE).
Un seul module Gradle, packages sous `com.replaybox.*`.

## Architecture

```
app.ui          Compose for TV (home en rangées, catalogue, player, recherche)
core.model      types du glossaire: Channel, Show, Season, Episode, Stream, Progress, Genre
core.data       Room: cache catalogue (TTL 12 h) + Progress + Favorites
provider.api    interface Provider (la seam de test unique)
provider.tf1    implémentation TF1+
provider.francetv  implémentation France TV
provider.m6     implémentation M6+ (listée seulement avec un compte M6+)
```

```kotlin
interface Provider {
    val channel: Channel
    suspend fun shows(genre: Genre?): List<Show>
    suspend fun seasons(show: ShowId): List<Season>
    suspend fun episodes(season: SeasonId?): List<Episode>   // null = tous
    suspend fun search(query: String): List<Show>
    suspend fun stream(episode: EpisodeId): Stream           // manifest + sous-titres
}
```

## Provider TF1+

Listing — graphql web à persisted IDs (GET, headers : `referer: https://www.tf1.fr/programmes-tv`
+ UA navigateur). Mesuré le 2026-11-05 : les préfixes courts d'ID sont acceptés ; le client web
envoie aussi la forme complète `id=sha256:<hex>` :
- `id=909c68c0` variables `{}` → catégories racine (GT_SERIES_AND_FICTIONS, GT_MOVIES,
  GT_ENTERTAINMENT, GT_INFOS_MAG_AND_SPORT, GT_YOUTH ; `event: null` avec erreur
  « category MAIN_EVENT not found » est une réponse normale)
- `id=483ce0f` variables `{"context":{"persona":"PERSONA_2","application":"WEB","device":"DESKTOP",
  "os":"WINDOWS"},"filter":{"channel":"tf1"},"offset":N,"limit":N}` → `programs.items[]` (listing
  catalogue, découvert/mesuré 2026-11-05). **Sans `filter`** → le catalogue complet, toutes chaînes
  (mesuré 2026-11-07 : `data.programs.total` = 4685). Comme pour `a6f9cf0e`, **`offset` est un
  index de page** : `limit=500` → pages 0..8 pleines, page 9 = 185 items, page 10 vide (~3,3 Mo
  décompressé par page). Fixture : `tf1_programs_index_page.json`
- `id=e78b188` variables `{"query":…}` → searchPrograms
- `id=b2dc9439` variables `{"query":…}` → searchVideos
- vidéos d'un programme : `id=a6f9cf0e` (mesuré 2026-11-05) variables `{"programSlug":…,"offset":N,
  "limit":20,"types":["REPLAY"],"sort":{"type":"DATE","order":"DESC"}}` → `programBySlug.videos`
  {items, total} (pagination par 20). **`offset` est un index de page, pas d'item** (mesuré
  2026-11-06 sur `profilage`, total 102 : `offset=1,limit=20` → items 20..39 ; `offset=20` → vide).
  Fixture : `tf1_program_by_slug_profilage_last_page.json` (`offset=5`, 2 items)
- détail d'une vidéo + id média interne : hash yt-dlp
  `9b80783950b85247541dd1d851f9cc7fa36574af015621f853ab111a679ce26f` variables
  `{"programSlug":…,"slug":…}` → `videoBySlug.streamId` (id numérique wat)

Fraîcheur des Épisodes (mesuré le 2026-11-07 sur `a6f9cf0e` : jt-20h, les-feux-de-l-amour,
demain-nous-appartient, koh-lanta, profilage, the-voice, sept-a-huit) — `date` (ISO UTC) = début
de la diffusion TV, heure comprise (JT 19:57:51 heure de Paris, Demain nous appartient 19:13, Les
feux de l'amour 10:57) → `Episode.broadcastAt` ; le fonds ancien porte des bouche-trous (Profilage
`2023-09-01T07:00:00Z`). `published` (ISO UTC) = mise en ligne → `publishedAt` (JT : ~40 min après
le début ; feuilletons : égal à `date` ; avant-premières : des jours avant `date`). **Pas de date de
fin** : `playingInfos.remainingDays` est un nombre entier de jours restants, compté par le serveur
au moment de la réponse et arrondi au supérieur (Les feux de l'amour, disponibles 30 jours après
diffusion : 29 pour l'Épisode du 06/11, 7 pour celui du 15/10, à 14 h 48) ; la page tf1.fr et
`videoBySlug` ne portent que ce même compteur. `availableUntil` = `extensions.report.execution.start`
(instant serveur, epoch ns ; à défaut l'horloge de la box) + `remainingDays` × 24 h : **borne
haute**, la fin réelle tombe dans les 24 h qui précèdent (le dernier jour TF1 s'affiche « 1 jour »
et non « dernier jour », mais aucun Épisode n'est masqué trop tôt). `freeRemainingDays` vaut la même
chose, sauf sur les avant-premières (0 : offre payante). `remainingPlayingHours` n'existe que sur la
page web, pas dans le listing. Fixtures : `tf1_program_by_slug_jt_20h_freshness.json`,
`tf1_program_by_slug_feux_de_l_amour_freshness.json`.

Ne garder que `type: REPLAY` (les `EXTRACT`/`BONUS` sont hors périmètre). La taxonomie
`GT_*`/`SGT_*` de `program.categories[]` mappe vers les Genres canoniques (table en code ;
chaque programme porte exactement un `GT_` et un `SGT_`, ids `SGT_` en anglais : `SGT_COMEDY`,
`SGT_DRAMA`, `SGT_DOCUMENTARY`… ; Genres = union des deux). Pas de policier/thriller natif TF1 pour
les séries → retombe sur Séries ; `SGT_THRILLER`/`SGT_DETECTIVE` n'existent que sur les films
(`GT_MOVIES`), mesuré 2026-11-07 sur le catalogue complet. « Policier & Thriller » (règle du
grilling M4 du 2026-11-07) : les films ne le reçoivent que de `SGT_THRILLER`/`SGT_DETECTIVE` ; les
séries (`GT_SERIES_AND_FICTIONS`) le reçoivent d'une heuristique de mots-clés sur titre et synopsis
normalisés (`Tf1CrimeKeywords`) : un mot-clé dans le titre, ou au moins deux mots-clés distincts
dans le synopsis (les flexions d'un mot-clé comptent une fois). Deux listes de slugs commentées
corrigent les ratés connus : l'inclusion porte HPI et les 29 séries policières que la règle à deux
mots-clés écartait (Alice Nevers, Julie Lescaut, Les Experts : Miami, Elsbeth…), l'exclusion Plus
belle la vie (fixture `tf1_crime_cases.json`, avec les pièges vus sur la box : La desalmada,
Twilight, La Ligne verte). Mesuré le 2026-11-07 sur la capture complète (4687 programmes, 2448
fictions/films du groupe avec replay) : 562 « Policier & Thriller » avec l'ancienne règle (un seul
mot-clé suffisait, films compris ; la mesure M3 du même jour donnait 555 des 2446) → 278
(268 séries dont 30 par la liste d'inclusion, 10 films par SGT natif). Les téléfilms d'un seul
Épisode à un seul mot-clé (« Piégée par mon mari »…) sortent du Genre.

Jeunesse (`GT_YOUTH`, sous-genres `SGT_THREE_TO_SIX_YEARS`/`SGT_SIX_TO_TEN_YEARS`) : 79
Programmes du groupe, tous sans autre Genre. TF1 range quelques dessins animés en fiction pure
(`GT_SERIES_AND_FICTIONS` + `SGT_SERIES`, aucun signal jeunesse dans le programme) : `pokemon`,
`yu-gi-oh`, `miraculous-chibi` reçoivent Jeunesse par une liste de slugs commentée dans
`Tf1Genres` (fixture `tf1_youth_cases.json`), mesuré le 2026-11-07. Les animés ados (Naruto,
Boruto, One Piece) restent en fiction.

Index TF1 (`CatalogueIndex`, mesuré 2026-11-07) — le listing d'une Chaîne TF1 est sa part de
l'Index : crawl de `483ce0f` sans filtre, 500 par page, offset = index de page, jusqu'à
`data.programs.total` (ou une page vide). Chaque page est tentée 3 fois ; une page en échec fait
échouer tout le crawl (rien n'est écrit, l'ancien Index reste servi). Un doublon entre pages
(dérive d'offset) est gardé une fois. Le crawl est mémorisé 10 min : les 5 Chaînes rafraîchies
d'affilée ne font qu'un crawl. Chaînes du groupe, clé `initialChannel.slug` : `tf1` « TF1 »,
`tmc` « TMC », `tfx` « TFX », `tf1-series-films` « TF1 Séries Films », `lci` « LCI » (2881
Programmes) ; tout autre `initialChannel` (arte, novo19, public-senat…) est écarté, de même que les
programmes à `replay.total == 0`. `replayRecent.total` alimente `Show.recentEpisodeCount`
(départage de la recherche). Saisons, Épisodes et Flux ne dépendent pas de la Chaîne.

Flux — résoudre l'id média interne (`videoBySlug.streamId`) puis suivre la chaîne de
l'**extracteur `wat` de yt-dlp** : `GET https://mediainfo.tf1.fr/mediainfocombo/<streamId>?pver=5010000`
(source de vérité : `yt_dlp/extractor/wat.py`, master @ bc6ff877dd). Sortie : manifest DASH sans
DRM (host `vod-das.cdn-*.diff.tf1.fr` ou `das-q1(-ssl).tf1.fr`, MPD statique à 1 Period, zéro
marqueur pub) + sous-titres dans le manifest. Porter fidèlement, ne pas réinventer.

**⚠️ Dérive mesurée le 2026-11-05** : TF1 exige un compte connecté (token Gigya) pour le replay
depuis ~janvier 2026 — en anonyme, `mediainfocombo` répond `delivery.code=403` avec
`media.error_code=DELIVERY_ERROR` (« Contenu non disponible sur le site », cf. yt-dlp #15647) ou
`PERMISSION_DENIED` (« Permission insuffisante ») selon les paramètres. Le player web affiche
« il vous suffit de vous connecter ». Seule une partie du catalogue reste jouable anonymement
(vérifié : « Les 12 coups de midi », « Le 20h », flux DASH nodrm 200, MPD à 1 Period). Décision
produit du 05/11 : sign-in TF1+ embarqué (M0.5). Les erreurs se mappent : `GEOBLOCKED` → zone,
`PERMISSION_DENIED` / `DELIVERY_ERROR` 403-500 → compte requis, autre → indisponible.

Chaîne authentifiée (mesurée, validée en lecture réelle le 05/11) :
1. `GET https://compte.tf1.fr/accounts.webSdkBootstrap?apiKey=<gigya key du login web>&pageURL=…
   &sdk=js_latest&sdkBuild=13987&format=json` — dépose les cookies `gmid`/`ucid`, **obligatoires** :
   sans eux `accounts.login` répond `400006 Invalid parameter value` (fixtures
   `tf1_login_missing_cookies.json` / `tf1_login_invalid_credentials.json`).
2. `POST https://compte.tf1.fr/accounts.login` (form Gigya standard : loginID, password,
   sessionExpiration, targetEnv=jssdk, include, APIKey, authMode=cookie…) → `userInfo`
   {UID, UIDSignature, signatureTimestamp}.
3. `POST https://www.tf1.fr/token/gigya/web` {UID, signature, timestamp, **consent_ids**} →
   {token (JWT), ttl}. **Les `consent_ids` sont obligatoires** : liste vide = équivalent
   « cookies refusés » sur le web et le Flux reste verrouillé malgré le compte. Les ids sont
   ceux du registre Didomi du bundle web : `technique-HWZ9HRWz`, `publicite-cidJFbya`,
   `personnali-yf2njgbr`, `mesureda-mn4zfWU6` (Replaybox envoie les 4, état « TOUT ACCEPTER »).
4. `mediainfocombo` avec **le jeu complet de paramètres du player web**
   (context=MYTF1, platform=web, device=desktop, os=linux, osVersion, topDomain, playerVersion,
   productName=mytf1, productVersion) + `Authorization: Bearer <token>` → `delivery.code=200`,
   manifest DASH + `delivery.drms[]` (widevine : license url + headers) quand le contenu est DRM.
   Re-login silencieux à l'expiration du JWT (exp) ; sur 401/403, régénérer le token une fois.

## Provider France TV

Listing — `GET https://api-mobile.yatta.francetv.fr/apps/channels/<slug>?platform=apps`
(france-2, france-3, france-4, france-5, franceinfo) → collections `playlist_program`/
`playlist_mixed` → `items[]` **plats** (label, description, `program_path`, images) — pas de
`items[].item` (corrigé après mesure 2026-11-05). Détail programme :
`GET /apps/program/<program_path>?platform=apps&size=250&page=N` (N = 0, 1, … — voir « Plafond
250 ») → collections étiquetées « Saison N »
(type `playlist_video`) → `items[]` plats avec `si_id`, `season`, `episode`, `duration`,
`type: integrale` ; `/apps/program/meta/<path>` donne `category` + `sub_categories` (→ Genres
canoniques ; mapping natif riche : séries-policieres, thriller, comédie, drame, doc histoire…).
Jeunesse = catégorie `enfants`, exclusive (aucun autre Genre) : 46 Programmes sur les 238 des cinq
listings de Chaîne, aucun ne portait un autre Genre (mesuré le 2026-11-07).

Détails mesurés le 2026-11-05 (lot 1 M1, fixtures `ftv_*.json`) :
- **Plafond 250** (mesuré 2026-11-06, remesuré le 2026-11-07) : sans `size`, la collection
  principale (« Les émissions » ; les « Saison N » suivent la même pagination : absentes de la
  page 1 de capitaine-marleau) s'arrête à 250 items et `playlist_audio`
  (podcasts) à 200. `size=S` fixe ce plafond **pour ces deux-là** ; les collections thématiques
  (« Histoires de… », « Grand format »… : celles qui portent un `id`) gardent leur taille (≤ 43
  items) quels que soient `size` et `page`. `page=N` est **0-based** (`page=0` = sans `page`) et
  pagine par `S` : la page suivante reprend où la précédente s'arrête, les thématiques s'y
  répètent à l'identique, et une collection épuisée **disparaît** de la page (ca-commence page 2 :
  plus de « Les émissions »). Une même page peut contenir des doublons de `si_id`.
  **`size=2000` est proscrit** : il gonfle aussi les podcasts — « Ça commence aujourd'hui »
  pèse 28,5 Mo (dont 19,5 Mo pour 2000 podcasts, 7,7 Mo pour 498 émissions), soit une String de
  57 Mo qui a fait un `OutOfMemoryError` sur la Bbox (heap 128 Mo). Mesures du 2026-11-07 (octets
  décompressés ; en gzip sur le fil, ca-commence fait 0,6 Mo en page 0 et 1,6 Mo en `size=2000`) :

  | Programme | sans `size` | `size=2000` | `size=250&page=0` / `1` / `2` | intégrales (`si_id` uniques) |
  |---|---|---|---|---|
  | ca-commence-aujourd-hui | 7,7 Mo, 274 | 28,5 Mo, 494 | 8,2 / 8,1 / 4,5 Mo — 250 / 248 / absente | 494 (498 items) |
  | journal-20h00 | 3,0 Mo, 249 | 5,0 Mo, 443 | 3,0 / 2,3 / 0,3 Mo — 250 / 194 / absente | 443 (444 items) |
  | 13h15-le-samedi | 3,3 Mo, 206 | 4,1 Mo, 254 | 3,3 / 0,9 / 0,2 Mo — 250 / 59 / absente | 254 (309 items) |
  | capitaine-marleau | 0,9 Mo, 27 | 0,9 Mo, 27 | 0,9 Mo — Saisons 10/5/5/7, page 1 sans Saison | 27 |

  L'union des pages `size=250` redonne exactement l'ensemble de `si_id` de `size=2000` sur les
  quatre. Replaybox lit donc le détail **par pages de 250** (`page=0`, `1`, … tant qu'une
  collection `playlist_video` revient pleine, 20 pages au plus), décode chaque page en flux
  (jamais de String entière, corps plafonné à 32 Mo) et ne garde que les collections
  `playlist_video` et leurs items `integrale`. Chiffres de 2026-11-06 (`size=2000`) pour mémoire :
  journal-20h00 443, ca-commence-aujourd-hui 495, 13h15-le-samedi 253. Fixtures :
  `ftv_program_ca-commence-aujourd-hui_page0.json` / `_page1.json` (vraies pages `size=250`,
  toutes les collections et tous les items, élagués de `images`/`program`/`channel`/`category`/
  `marker*`/`description`).
- `program_path` peut être préfixé par un slug de **navigation** (`info_nous-les-europeens`,
  `series-et-fictions_series-drame_les-disparus-des-argonnes`) et pas seulement par la chaîne —
  ne jamais déduire la Chaîne du préfixe du path.
- Images : `images[]` = dicts `{urls: {"w:N": url}, type}` (types `vignette_3x4`, `vignette_2x3`,
  `vignette_16x9`, `background_16x9`, `logo`…). Clé `w:0` = image vide, à ignorer ; prendre la
  largeur max de la première forme utile (portrait pour l'affiche, 16x9 pour les vignettes).
- Programmes non numérotés (magazines) : plusieurs collections `playlist_video` **thématiques**
  (« Les Émissions », « Histoires de… ») avec recouvrement — mesuré sur « Ça commence
  aujourd'hui » : 399 intégrales, 274 `si_id` uniques → **déduplication par `si_id`
  obligatoire**. `playlist_audio` (podcasts) et items `extrait`/`article` sont hors périmètre :
  filtrer `type == "integrale"` et ne considérer que `playlist_video`. Modèle de Saisons :
  collections « Saison N » = une Saison numérotée chacune ; sinon **une seule** Saison non
  numérotée regroupant tous les integrales dédupliqués (glossaire : Saison absent quand la
  chaîne ne numérote pas).
- Le champ `login: true` de yatta (épisodes « compte requis » de l'app officielle) **ne bloque
  pas** la résolution anonyme : k7 répond 200 avec URL + sous-titres (mesuré Simon Coleman S4,
  « Ça commence aujourd'hui »). `markers.pub` = config CSAI du player officiel, absente du
  manifest brut — ignorée.

Fraîcheur des Épisodes (mesuré le 2026-11-07 sur journal-20h00, capitaine-marleau, envoye-special,
quelle-epoque ; comparé à « Diffusé le … à … - Disponible jusqu'au … » des pages france.tv de 9
Épisodes) — items `integrale` du détail programme, en secondes unix (UTC) : `end_date` = fin de
disponibilité → `Episode.availableUntil` (identique au site sur les 9 ; JT : mise en ligne + 30 j ;
magazines 6 à 12 mois ; fictions : dates fixes, souvent 23:59:59) ; `2147483647` (« 19/01/2038 »
sur le site, archives du JT) = sans fin → null. `begin_date` = mise en ligne → `publishedAt` (JT :
fin de diffusion + ~2 min ; Capitaine Marleau S4E17 en ligne 8 jours avant sa diffusion ; un
ré-import du catalogue porte la date du ré-import). `broadcast_begin_date` = début de diffusion à la
seconde (JT 19:57:47) → `broadcastAt`, **seulement si `broadcast_channel` est renseigné** : sans
lui (exclusivités web, ré-imports), le site n'affiche pas « Diffusé le » et la valeur est un
bouche-trou (égale à `begin_date`, ou une heure sans rapport) — 23 Épisodes sur 27 pour Capitaine
Marleau, 90 sur 106 pour Envoyé spécial, 2 sur 443 pour le JT. La date affichée (`broadcastDate`)
ne change pas. `content_diffusion_date` (marqueur piano, `DDMMYYYY` sans heure) n'est pas utilisé.
Fixtures : `ftv_program_journal-20h00_freshness.json`, `ftv_program_envoye-special_freshness.json`.

Flux — `GET https://k7.ftven.fr/videos/<si_id>?country_code=FR&capabilities=drm&os=androidtv&offline=false`
→ `video.url` (format `dash`, `drm: false`) → signer via la base donnée dans la réponse
`video.token` (= `https://hdfauth.ftven.fr/esi/TA?format=json`) + `&url=<url encodée>` → `{"url":
<URL signée courte durée>}`. Sans DRM sur les contenus vérifiés.

Sous-titres — mesuré sur 3 épisodes (fiction ancienne, fiction récente, magazine) :
`video.text_tracks` est **toujours vide**. Les sous-titres FR sont **side-loadés** depuis
`meta.subtitles[]` (`{url: *.vtt, lang: "qsm"}` ou `"fra"`) via `MediaItem.SubtitleConfiguration`
(Media3).

Pistes du MPD — mesuré le 2026-11-06 sur 7 titres (fixture
`app/src/test/resources/fixtures/ftv_mpd_tracks.mpd`, tronquée, sans URL signée) : l'en-tête
`manifest_edit … element_remove` ne retire que `mimeType: text/vtt`. Les sous-titres en **WebVTT
dans MP4** (`application/mp4`, `codecs="wvtt"`) restent dans le MPD : `fr` (« Sous-Titres
Français », présent avec la VO) et `qsm` (« Sous-Titres Malentendant », toujours). Audio : `fr`
(rôle `main`), `qaa` (rôle `alternate`, « Audio Originale ») et `qad` (rôle `description`,
« Audio Description »), selon le titre. Vidéo : hauteurs 432 à 1080, sans DRM. Le side-load reste
(décision Q9) : `fra` est normalisé en `fr` par Media3 et dédoublonné avec la piste du MPD.

## Provider M6+

Middleware 6play, plateforme **`m6group_web`** partout (décision Q18) : host
`https://pc.middleware.6play.fr/6play/v2/platforms/m6group_web`. Chaque appel porte
`x-customer-name: m6web` + l'UA navigateur de `Tf1Http.BROWSER_UA`. Tout ce qui suit est mesuré
en **anonyme** le 2026-11-07 (200), sans compte. Code : `provider.m6` (`M6Http`, `M6Parsers`,
`M6Genres`, `M6Channels`, `M6Provider`, `m6CatalogueIndex`, `M6Auth`, `M6AuthApi`). Branché dans
`Providers` **seulement quand un compte M6+ est enregistré** (voir Licence). Spike DRM réussi sur la
box le 2026-11-07 avec le compte d'Arnaud (voir Licence) : M6+ sort en M3.

Listing — `GET {host}/services/6play/programs?limit=100&offset=N&csa=6&with=rights` → tableau JSON
de Programmes. `limit` plafonné à 100 ; **`offset` est un index d'item** (pas de page) ; le total est
dans l'en-tête `x-rest-collection-count` (1288). Un 503 HTML Varnish arrive de temps en temps, et la
pagination par offset dérive un peu (1289 items lus pour 1288) : retry et dédoublonnage par id sont
ceux de `CatalogueIndex`. Champs utilisés : `id`, `title`, `description`, `images[]`,
`service_display.code`, `count.vi`, `neustar.category`, `program_type_wording.code`.
Fixture : `m6_programs_page.json` (12 Programmes élagués aux champs utilisés).

Index M6 (`CatalogueIndex`) — 13 pages de 100, jusqu'au total de l'en-tête (ou une page vide), même
règles que l'Index TF1 (3 essais par page, échec = tout le crawl échoue, mémo 10 min partagé par les
5 Chaînes). Chaînes gardées (décision Q16), clé `service_display.code` : `m6replay` « M6 » (id `m6`),
`w9replay` « W9 » (`w9`), `6terreplay` « 6ter » (`6ter`), `gulli` « Gulli » (`gulli`), `6play`
« M6+ » (`m6plus`). Tout autre service (parispremierereplay, tevareplay, 6playmaxott, podcast…) est
écarté, de même que les Programmes à `count.vi == 0` (872 gardés sur 1286). Pas de signal de
récence dans le catalogue : `recentEpisodeCount = 0`. Recherche : locale uniquement (décision Q17).

Ids préfixés `m6:` (routables plus tard par `Providers.resolveShow`) : Programme
`m6:{service}:{programId}`, Saison `m6:{service}:{programId}:{saison ou ""}`, Épisode
`m6:{service}:{clipId}`.

Images — `images[]` = `{external_key, role}` sans URL. Fichier brut :
`https://images.6play.fr/v2/images/{external_key}/raw` (200 anonyme ; `v1` identique). Les variantes
redimensionnées (`?width=…`) exigent un paramètre `hash` signé (403 sans) : on sert le brut. Rôles
mesurés : `portrait` 1000×1500 → affiche (repli `cover` 750×1000) ; `jumbotron` 2732×1536 sans titre
incrusté → fond 16:9 de la Home (`vignette`, même taille, porte le titre). Épisode : `display_image`
(rôle `vignette`).

Genres (`M6Genres`) — union de `neustar.category` (documentaire, serie-policiere, telefilm-drame,
kids…) et `program_type_wording.code` (episode, telefilm, film, emission, dessin-anime,
documentaire). `serie-policiere` et `telefilm-frissons` → Séries + Policier & Thriller ; une
catégorie inconnue de la famille serie/series/telefilm/telenovela → Séries ; `serie-realite` →
Divertissement. `action` (54 Programmes) n'a pas de Genre propre : seul le type (`film`) le classe.
Jeunesse = catégorie `kids`, `tous-petits` ou `teens`, ou type `dessin-anime` (décidé par la
catégorie, jamais par la Chaîne Gulli), exclusive : 77 Programmes gardés, un seul portait un autre
Genre (Zig et Sharko, `kids` + `episode`, M6), mesuré le 2026-11-07. `les-plus-grands` (Gulli) et
les Programmes Gulli sans catégorie ni type restent sans Genre.

Vidéos d'un Programme — `GET {host}/services/{service}/programs/{programId}/videos?csa=6&with=clips,freemiumpacks&type=vi&limit=100&offset=N`,
mêmes plafond, offset d'item et en-tête de total (Pékin Express 945 : 155 → 2 appels).
`type=vi` = Épisodes complets seulement (pas d'extraits `vc`). Numérotation dans
`clips[0].product` : `season`, `episode`, `part_number`, `first_diffusion` (heure de Paris
« 2026-05-22 22:10:00 », affichée « 22/05/2026 »). Id vidéo `clip_13179913`, `duration` en
secondes. L'ordre de l'API n'est pas trié (S16 au milieu de S22). Saisons : numérotées, la plus
récente d'abord ; les vidéos sans numéro vont dans une Saison non numérotée « Épisodes », la seule
pour les magazines et films. Dans une Saison : épisode puis partie croissants. Des saisons sont des
années (`2025`, `2026`). Fixtures : `m6_videos_945_p0.json` / `_p1.json` (offsets 0 et 100, 3 + 2
vidéos, sans assets).

Fraîcheur des Épisodes (mesuré le 2026-11-07 sur Pékin Express 945, Wanted 28540, Scènes de ménages
829, 66 minutes 825, Les sisters 21200 sur Gulli) — `clips[0].product.first_diffusion` = première
diffusion, heure de Paris sans décalage (Wanted 21:10 et 22:10, Scènes de ménages 20:40, 66 minutes
17:05) → `Episode.broadcastAt` (fuseau Europe/Paris, heure d'été comprise) ; absente sur une partie
du fonds (64 vidéos sur 100 de Scènes de ménages) → null. `last_diffusion` = dernière rediffusion
(Gulli : rediffusions du matin), non utilisée. `publication_date` n'est **pas** une date de mise en
ligne : elle est réécrite à J+7 de la diffusion (Wanted E2, diffusé le 29/10 à 21:10, porte
`publication_date` 05/11 22:05, heure de mise en ligne de E3 ; même motif sur Scènes de ménages et 66
minutes) → `publishedAt` null, « Nouveau » retombe sur la diffusion. **Aucune fin de
disponibilité** en anonyme : ni dans la liste des vidéos, ni dans la vidéo seule (appel du Flux),
ni dans le catalogue (`rights` vaut null ; `publication_date_vi.date_end_max` est par Programme) →
`availableUntil` null, pas de badge d'expiration M6. Fixtures : `m6_videos_28540_freshness.json`,
`m6_videos_829_no_diffusion.json`.

Flux — `GET {host}/services/{service}/videos/{clipId}?csa=6&with=clips,freemiumpacks` →
`clips[0].assets[]`. `usp_dashcenc_h264` = DASH CENC (Widevine + PlayReady), `video_quality` `hd`
(1080p) ou `sd` (720p, 540p) ; on prend `hd`, sinon le premier `sd`. `usp_hlsfp_h264` est FairPlay :
ignoré. URL dans `full_physical_path`, sur le redirecteur `lbcdn.6cloud.fr`, signée (`token=` JWT,
`expiration=` epoch ~10 h) et **géo-restreinte** : son paramètre `countries` décode en
`AD|FR|GP|GF|MQ|YT|MC|NC|PF|RE|BL|MF|PM|TF|WF`. Depuis la France, la GET répond 302 vers
`*.cdn.bedrock.tech` puis 200 : MPD statique à 1 Period, zéro marqueur pub, sous-titres TTML dans
le MPD. Les assets figurent aussi dans la liste des vidéos, mais le Flux relit la vidéo seule (URL
fraîche). Fixtures : `m6_video_clip.json` (tokens remplacés par `trimmed`), `m6_video_hls_only.json`
(la même sans DASH : erreur claire, pas un plantage).

Licence — portée de catchup-tv-and-more (`resources/lib/channels/fr/6play.py` @
fa49e5a0c4bfb5f501ac8d5486c5f6465cc16745, `get_login_token` et `get_video_url`), implémentée dans
`M6Auth` / `M6AuthApi`, **mesurée de bout en bout sur la Bbox le 2026-11-07** (compte d'Arnaud saisi
sur la box, logs `M6Auth` / `ReplayboxDrm`) : login Gigya 200, getJwt 200, upfront-token 200 pour
`m6replay` (Pékin Express) **et `w9replay`** (Les Marseillais), licence drmtoday 200 dont le champ
`license` se décode (911 et 1098 octets). Le CDM charge **Widevine L1** (OEMCrypto v16, « I will use
level 1 ») malgré le profil `drm_software` des assets ; décodeur `OMX.Berlin.video_decoder.avc.secure`,
1920×1080 ; lecture PLAYING, sous-titres TTML affichés, aucune pub (l'Épisode démarre à 0:00).
1. `POST https://login-gigya.m6.fr/accounts.login`, form `loginID`, `password`, `apiKey` (clé Gigya
   publique du login web), `format=jsonp`, `callback=jsonp_3bbusffr388pem4`, en-tête
   `referer: https://www.6play.fr/connexion` → réponse JSONP dont on retire l'enveloppe : `UID`,
   `UIDSignature`, `signatureTimestamp` au premier niveau (repli sur `userInfo`). La source ne fait
   **ni webSdkBootstrap ni cookies** (contrairement à TF1+), et le spike confirme qu'il n'en faut
   pas. `errorCode` 403042 → « E-mail ou mot de passe incorrect », 403120 →
   compte bloqué, autre → refus avec son code.
2. `GET https://front-auth.6cloud.fr/v2/platforms/m6group_web/getJwt` avec `x-auth-gigya-uid`,
   `x-auth-gigya-signature`, `x-auth-gigya-signature-timestamp`, `x-auth-device-id`,
   `x-customer-name: m6web`, `x-client-release` → `{token}` (JWT), gardé en mémoire jusqu'à son
   `exp` (re-connexion complète une minute avant : la signature Gigya est horodatée). Le device id
   est un UUID aléatoire tiré une fois par installation (prefs `replaybox_m6_device`, pas un
   secret). Erreurs enregistrées en anonyme le 2026-11-07 : sans device id → 400
   « Missing homeid or deviceid » (`m6_auth_getjwt_no_device.json`), signature invalide → 498
   (`m6_auth_getjwt_rejected.json`).
3. `GET https://drm.6cloud.fr/v1/customers/m6web/platforms/m6group_web/services/{service}/users/{UID}/videos/{clipId}/upfront-token`
   avec `Authorization: Bearer <JWT>`, `x-customer-name`, `x-client-release` → `{token}`. `service`
   = le vrai code de la Chaîne (`m6replay`, `w9replay`, `6terreplay`, `gulli`, `6play`), comme le
   bundle web (`services/${service}/users/${UID}`). La source fige `m6replay`, mais le serveur accepte
   le vrai code de la Chaîne (mesuré : `w9replay` → 200). 401 ou 498 → JWT régénéré une fois, puis échec. JWT invalide enregistré :
   `{"code":"49804",…}` (`m6_auth_upfront_invalid_jwt.json`).
   `x-client-release` = release de l'app web m6.fr mesurée le 2026-11-07 (`6.54.2`, constante
   unique `M6AuthApi.CLIENT_RELEASE` ; la source envoie 5.103.3).
4. Licence Widevine : `POST https://lic.drmtoday.com/license-proxy-widevine/cenc/`, corps = challenge
   brut, en-tête `x-dt-auth-token: <upfront token>`. La réponse est un JSON `{"license": "<base64>"}`
   (clé inputstream de la source `…|R{SSM}|JBlicense`). `Stream.licenseResponseField = "license"`
   (null = corps brut, cas TF1) : le player passe alors par son propre `DefaultDrmSessionManager`
   dont le callback (`UnwrappingDrmCallback` sur `HttpMediaDrmCallback`, URL de licence forcée)
   décode ce champ avant de rendre la licence au CDM. Le manifest 302 vers un autre host :
   `DefaultHttpDataSource` suit les redirections inter-hosts, inter-protocoles autorisées en plus,
   UA navigateur.

Les identifiants ne sont enregistrés (`CredentialStore(context, "m6")`) qu'après le succès des
étapes 1–2 : un mauvais mot de passe ne fait pas apparaître les Chaînes M6. Les logs (`M6Auth`,
`ReplayboxDrm`) ne portent que l'étape et le statut HTTP, jamais e-mail, mot de passe, UID,
signature, device id ou token. Les fixtures de succès `m6_auth_*_shape.json` et
`m6_drmtoday_license_shape.json` restent des **formes tirées de la source** : les réponses réelles
portent des secrets (UID, signature, JWT, licence) et ne s'enregistrent pas telles quelles ; le spike
a confirmé ces formes en lecture réelle (champs lus sans erreur, licence décodée).

Règle d'affichage : Paramètres liste toujours M6+ (« Non connecté » sans compte) ; les 5 Chaînes M6
n'entrent dans `Providers.all` que tant que `M6Auth.hasCredentials()`. `all` est relu à chaque
accès : connexion ou déconnexion valent sans redémarrage ; au retour sur l'Accueil, seules les
Chaînes pas encore rafraîchies par ce process sont rafraîchies. Sans compte, `M6Provider.stream()`
lève l'erreur « compte requis » (le player affiche alors le message M6+). Bannière de cache
périmé : source « M6+ ».

Images : fichiers bruts de 120 à 400 Ko. Coil (`AsyncImage` sans taille explicite) décode à la
taille de la mise en page (contraintes) avec sous-échantillonnage : la mémoire suit la taille de la
carte ou du fond, pas celle du fichier.
Mesuré sur la box le 2026-11-07 (listener Coil des builds debug, `adb logcat -s ReplayboxImage`) :
les fichiers bruts (jusqu'à 478 Ko) portent `Cache-Control: max-age=5184000, public`, donc le cache
disque de Coil les garde 60 jours. App lancée, en descendant les rails de l'Accueil : M6 depuis le
disque médiane 242 ms (max 548 ms, n = 14), réseau 393–630 ms (n = 3), au niveau de TF1 (disque
médiane 289 ms) et de France TV (282 ms) ; un rail se remplit en moins de 1 s, aucun correctif.
Au démarrage à froid, toutes les images (toutes Chaînes) mettent 1 à 2,2 s, pendant un GC soutenu
(gros objets de 6 à 9 Mo) : sujet de démarrage, pas propre à M6.

## Cache et mode dégradé (ADR 0002)

Room : `shows`, `seasons`, `episodes`, `genre_map` + `progress`, `favorites`. TTL 12 h, refresh au
démarrage en arrière-plan, priorité Home. Provider KO au refresh → servir le cache + bannière
« Données du [date] ». La résolution du Flux reste toujours live (URLs signées, courte durée).

Genres et mise à jour de l'app : `genre_map` garde les Genres tels que chaque Provider les a
mappés ; les règles communes (`Genres.canonical` : Jeunesse exclusive) s'appliquent à la lecture
par `CatalogStore`, donc un Index déjà stocké les suit dès la première lecture après la mise à
jour, même hors ligne. Le mapping propre à un Provider (mots-clés TF1, slugs) ne change qu'avec
un nouvel Index : `refreshListings` recrawle toutes les Chaînes à chaque lancement, sans
regarder le TTL, et l'installation d'un APK redémarre le processus ; tant que ce crawl échoue,
l'ancien Index reste servi (ADR 0002). Pas de version de mapping à incrémenter.

Schéma v5 (2026-11-07) : `episodes` porte `broadcastAt`, `publishedAt`, `availableUntil` (epoch
ms, nullables, cf. chaque Provider) ; la migration 4 → 5 vide les marqueurs `programs` pour que
les Épisodes en cache soient relus avec ces dates.

Fraîcheur (`FreshnessPolicy`, fuseau Europe/Paris, au plus un badge, par priorité) : fin de
disponibilité aujourd'hui → `LastDay` (une fin à 00:00 compte pour la veille), sinon dans 1 à 7
jours calendaires → `DaysLeft(n)` (demain = 1) ; diffusion la veille à partir de 18:00 →
`LastNight` ; mise en ligne (à défaut diffusion) il y a moins de 48 h → `New`. Un Épisode dont la
fin est passée (`isExpired`) n'a aucun badge. Une date null saute sa règle.
## Player

Media3/ExoPlayer, DASH et HLS auto-détectés. **Sous-titres FR activés par défaut** quand la piste
existe (choix produit, PRD §utilisateurs), toggle en un appui. Erreur de résolution → 1 retry →
écran d'erreur avec bouton Réessayer. Reprendre : position sauvegardée à 5 s d'intervalle + à la
pause.

Sous-titres (mesuré, lot 5/6 M1) : France TV → WebVTT side-loadé (`Stream.subtitleUrl`,
`SELECTION_FLAG_DEFAULT`, le manifest retire lui-même ses pistes texte, cf. §Provider France TV) ;
TF1 → pistes dans le manifest MPD (mesuré M0, flag default non vérifié contenu par contenu).
Garantie « activé par défaut » : à `onTracksChanged`, si une piste texte existe et n'est pas
sélectionnée → override Media3 sur la piste par défaut : `fr` d'abord, sinon `qsm`, sinon la
première (décision Q7). Toggle un appui : bouton « Sous-titres » des contrôles et touche CC
(visible seulement si une piste texte existe — pas de bouton = pas de promesse),
`setTrackTypeDisabled(TRACK_TYPE_TEXT, …)` + ré-sélection. Pas de piste pour un contenu → pas de
bouton, pas d'échec.

Options (décisions Q2–Q10, `PlaybackChoices`) : un panneau ouvert depuis les contrôles ou la touche
MENU. Chaque rangée vient des pistes réellement exposées par le lecteur, rien n'est inventé ; une
rangée sans choix est masquée. Audio : Français / Version originale / Audiodescription (codes
ci-dessus). Sous-titres : Désactivés + pistes. Qualité : Auto + hauteurs ≥ 576 présentes, appliquées
en plafond (`setMaxVideoSize`). Vitesse : 0,75× à 1,5×, remise à 1× à chaque Épisode. Le type
d'audio et le plafond de qualité sont mémorisés sur l'appareil (SharedPreferences `playback`).
TF1 : pistes à valider une fois connecté (Q10).

## Recherche

Cross-chaînes, debounce 300 ms, minimum 3 caractères, fusion des Provider.search() par titre
normalisé (casse/accents). La recherche TF1 est locale, sur l'Index stocké de chaque Chaîne du
groupe ; tant que l'Index n'est pas stocké (premier lancement), la seule Chaîne `tf1` retombe sur
searchPrograms distant (une fois, pas cinq). France TV : filtrage local du cache (pas de route de
recherche découverte). Classement (`SearchRanking`) : titre qui commence par la requête, puis titre
qui la contient ; égalités départagées par `recentEpisodeCount` décroissant, puis ordre d'arrivée.
Les rails Genre de la Home sont plafonnés à 40 cartes.

## Auto-update

Au lancement : `GET /repos/ouro84/replaybox/releases/latest` (API GitHub) → si tag > version courante,
notification → téléchargement APK → intent d'installation. Silencieux si à jour.

Mesuré le 2026-11-06 (fixture `github_release_latest.json`) : `tag_name` `vX.Y.Z`, `draft`,
`prerelease`, `assets[]` {`name`, `state` (`uploaded`), `size`, `browser_download_url`}. Le repo
doit être **public** : en privé, l'API et `browser_download_url` répondent 404 sans token (aucun
token ne vit sur la box). Appel non authentifié (60 req/h/IP), un seul par lancement d'Activity.

Implémentation (`app.update`) :
- Asset retenu : celui qui finit par `-release.apk` et `state == uploaded` ; release ignorée si
  draft, prerelease ou tag non `X.Y.Z`. Comparaison semver numérique.
- À jour (ou release plus ancienne, 404, réseau KO) : aucune autre requête, état `Idle`, les APK
  téléchargés précédemment sont purgés. Toute erreur est silencieuse (log `AppUpdater`).
- Plus récente : téléchargement dans `cacheDir/updates/replaybox-X.Y.Z.apk` (via `.part`, taille
  vérifiée contre `size`, réutilisé s'il est déjà complet) → état `Ready`.
- « Notification » = **bandeau in-app** en tête de la Home (« Mise à jour X.Y.Z prête » +
  « Installer ») : les launchers TV n'affichent pas les notifications système. L'intent
  d'installation (`ACTION_VIEW` sur un `content://` du `FileProvider` `com.replaybox.updates`)
  ne part **que** sur appui. Si « sources inconnues » n'est pas encore accordé à Replaybox,
  l'appui ouvre d'abord `ACTION_MANAGE_UNKNOWN_APP_SOURCES` ; l'utilisateur ré-appuie ensuite.
- Mesuré sur la Bbox (06/11) : l'écran système est `com.android.tv.settings/…ExternalSourcesActivity`
  (liste, Replaybox « Non autorisé » à basculer). Basculer le droit **tue le processus**
  Replaybox (comportement Android) : après l'autorisation unique, relancer l'app ; l'APK déjà
  téléchargé est réutilisé, le bandeau revient, l'appui ouvre le prompt système « Voulez-vous
  mettre à jour cette application ? ».

## Releases

Version unique : `replaybox.version=X.Y.Z` dans `gradle.properties` (semver, chaque partie 0..99) ;
`versionCode` dérivé `X*10000 + Y*100 + Z`, donc toujours croissant avec le tag. Une release =
`scripts/release.ps1` lancé sur `main` à jour et propre (build **local**, pas de CI de PR à ce
stade — décision du 06/11) : tests + `assembleDebug assembleRelease`, tag annoté `vX.Y.Z`, GitHub
Release avec deux assets `replaybox-X.Y.Z-release.apk` (celui que l'auto-update télécharge) et
`replaybox-X.Y.Z-debug.apk`. `-DryRun` construit sans taguer ni publier.

Signature V1 : la build `release` est signée avec le keystore debug de la machine de release
(`~/.android/debug.keystore`), le même certificat que les APK debug déjà installés sur la box —
debug et release se mettent à jour l'une l'autre, aucun secret dans le repo. Passer à un keystore
release dédié casserait la mise à jour en place (désinstallation requise une fois) : à décider
avant toute distribution hors de la box.

## CI

- **PR** : `./gradlew assembleDebug testDebugUnitTest` — **en local** pour l'instant (décision du
  06/11 : pas de workflow de PR sur GitHub ; les releases aussi se construisent en local, §Releases).
- **Nightly health-check** : un épisode TF1, un épisode France TV et un épisode M6+ poussés
  **jusqu'à l'URL du manifest** ; échec → alerte. C'est le seul test d'intégration réel ; tout le reste est sur fixtures.

Health-check (`.github/workflows/healthcheck.yml`, seul workflow Actions) :
- Cron `0 2 * * *` (03:00 Paris l'hiver) + `workflow_dispatch` avec `simulate_failure`
  (`none`/`tf1`/`francetv`/`m6`) pour exercer l'alerte. Runner `ubuntu-latest`, JDK 11.
- Code : `app/src/healthCheck/` (sondes `ProviderProbe` listées dans `Probes.all` — ajouter un
  Provider = une entrée, plus `KNOWN_PROVIDERS` du script de statut), compilé et lancé **uniquement** avec `./gradlew testDebugUnitTest -PhealthCheck` ;
  sans ce flag la suite unitaire les exclut explicitement (zéro réseau en test). Chaque sonde
  appelle les vrais `Provider` étape par étape — étapes nommées comme les méthodes `Provider` —
  et écrit `app/build/healthcheck/<provider>.json` {provider, label, ok, step, error, detail,
  checkedAt}.
- **TF1+** : anonyme sur « Le 20h » — `programSlug` **`jt-20h`** (mesuré le 06/11 via
  searchPrograms `e78b188` ; `le-20h` et `journal-20h` n'existent pas). Étapes `shows` → `seasons`
  → `episodes` (dernier replay, choisi dynamiquement) → `stream` → `manifest` (GET 200 + `<MPD`).
- **France TV** : `shows` (france-2) puis, pour les 10 premiers Programmes, `seasons` →
  `episodes` → `stream` → `signature` jusqu'au premier Épisode qui résout — jamais de `si_id` codé
  en dur (les Épisodes expirent). S'arrête à l'URL signée : **mesuré depuis un runner GitHub (US)
  le 06/11, le CDN `cloudreplay.ftven.fr` répond 403 hors de France** alors que yatta, k7 et
  hdfauth répondent 200. L'étape `signature` vérifie que le 1er segment du chemin décode en
  `exp=…~acl=…~hmac=…` avec `exp` futur (sinon `FtvProvider` serait retombé sur l'URL k7 non
  signée sans le dire).
- **M6+** (`m6`, « M6+ (anonyme) ») : sans compte, donc sans `stream` (tout Flux M6 est Widevine,
  cf. §Provider M6+). `shows` (crawl de l'Index, Chaîne `m6`) puis, pour les 5 premiers Programmes
  listés, `seasons` → `episodes` → `manifest` jusqu'au premier qui résout — aucun id codé en dur.
  `manifest` = `M6Provider.manifestUrl()` et **s'arrête à l'URL signée** comme France TV : https,
  `.mpd`, `token=` présent, `expiration` future. Raison : l'URL est géo-restreinte à la France et
  ses territoires (paramètre `countries`), la GET serait refusée depuis un runner US. Mesuré depuis
  la France le 07/11 (lancement local de la sonde) : chaîne entière OK, et la GET du MPD répond 302
  puis 200 (`*.cdn.bedrock.tech`, `<MPD`, 1 Period).
- Alerte : au moindre KO, l'issue ouverte au titre « health-check KO » (label `alerte`) est
  **commentée** (date, provider, étape, erreur, lien du run) ; s'il n'y en a pas, elle est créée
  avec les labels `ci` + `alerte` (créés au besoin). Au premier run entièrement OK, l'issue reçoit
  un commentaire « rétabli » et se ferme. Pas de résultat du tout (build cassé) → KO à l'étape
  `build`.
- **Statut public** (décision du 07/11, M3 Q20) : à chaque run (cron et `workflow_dispatch`), même
  KO ou build cassé (`if: always()`), le workflow publie `healthcheck.json` sur la branche orpheline
  **`status`**, force-pushée en un seul commit (auteur `github-actions[bot]`) :
  `https://raw.githubusercontent.com/ouro84/replaybox/status/healthcheck.json`. Produit par
  `.github/scripts/healthcheck-status.js` (Node, testé par
  `node --test .github/scripts/healthcheck-status.test.js`) à partir des fichiers des sondes.
  Format :
  ```json
  {
    "generatedAt": "2026-11-07T02:14:03Z",
    "runUrl": "https://github.com/ouro84/replaybox/actions/runs/<id>",
    "providers": [
      { "provider": "tf1", "label": "TF1+ — Le 20h (anonyme)", "ok": true,
        "step": null, "error": null, "checkedAt": "2026-11-07T02:13:00Z" },
      { "provider": "francetv", "label": "France TV — france-2 (anonyme)", "ok": false,
        "step": "signature", "error": "…", "checkedAt": "2026-11-07T02:13:30Z" }
    ]
  }
  ```
  Dates ISO-8601 UTC à la seconde. `provider` = nom de la sonde (`tf1`, `francetv`, `m6`).
  `step`/`error` valent `null` quand `ok`. `detail` n'est pas publié. Tout Provider connu
  (`KNOWN_PROVIDERS` du script, à garder aligné sur `Probes.all`) sans résultat — build cassé ou
  sonde absente — est publié `ok: false`, `step: "build"`, `checkedAt` = `generatedAt` ; une sonde
  inconnue du script est publiée quand même. L'app affiche « inconnue » si `generatedAt` a plus
  de 48 h.
- GitHub désactive les workflows `schedule` après 60 jours sans activité sur le repo : le commit
  nocturne de `status` entretient cette activité ; sinon, le réactiver depuis l'onglet Actions.

## Paramètres et Santé

Paramètres liste les Provider (`ProviderGroups.all`), une page chacun avec sa Santé puis son compte
quand il en faut un. Un groupe = un diffuseur : `id` = nom de la sonde du health-check (`tf1`,
`francetv`, `m6`), ses Chaînes, son `ProviderAccount` optionnel (TF1+ : `Tf1Auth`, M6+ : `M6Auth`).
  Les échecs de compte (`AccountException`, `AccountFailure`) s'affichent en français dans le
  formulaire ; le message anglais de l'exception reste aux logs et à la Santé.
- **Comptes** : `CredentialStore(context, providerId)`, EncryptedSharedPreferences avec clé
  maîtresse Keystore, fichier `replaybox_<providerId>_credentials` (clés `email`, `password`). TF1+
  garde ainsi `replaybox_tf1_credentials` : les connexions existantes survivent. Jamais en log.
- **Santé CI** : `CiHealthSource` lit `healthcheck.json` de la branche `status` (§CI, Health-check)
  sur `Dispatchers.IO`, timeout d'appel 5 s, cache mémoire du process rafraîchi à l'ouverture de
  Paramètres s'il a plus de 10 min ; un échec garde le corps précédent. `CiHealthParser` : fichier
  illisible, sans `generatedAt` ou `providers`, ou `generatedAt` de plus de **48 h** → état
  `UNKNOWN` pour tous (jamais un OK périmé) ; entrée sans `provider` ou sans `ok` ignorée.
- **Santé sur la box** : `HealthTrackingProvider` enveloppe chaque Provider réseau sous
  `CachedProvider` (seuls les vrais appels réseau comptent) et enregistre dans `LocalHealthStore`
  (SharedPreferences `replaybox_health`, rien ne quitte la box) l'issue de `shows` (catalogue) et
  de `stream` (lecture) — date, erreur courte « Classe: message » coupée à 120 caractères ;
  `seasons`/`episodes`/`search` ne sont pas suivis, une annulation n'est pas enregistrée. Le
  catalogue est gardé **par Chaîne** et lu au niveau du groupe : KO tant qu'une de ses Chaînes a
  échoué à sa dernière passe (chaque passe réécrit toutes les Chaînes, donc « KO si une Chaîne a
  échoué à la dernière passe ») ; la lecture est gardée par groupe. Un succès remplace l'échec
  précédent.
- **Badge** : état local = KO si la dernière issue catalogue ou lecture est un échec, OK si une
  issue existe, sinon inconnu. Combinaison CI × box : la pire des deux (décision d'Arnaud du
  2026-11-07), KO > inconnu > OK ; « Fonctionne » seulement si les deux côtés sont OK. Affiché seulement dans
  Paramètres (liste + page) : pas d'alerte Accueil, jamais de notification système.

## Tests

La seam unique = `Provider` : les parsers sont testés sur **fixtures JSON enregistrées** des API
réelles (une fixture par forme de réponse rencontrée ; nouvelle casse → nouvelle fixture). Aucun
appel réseau dans les tests unitaires. Comportement player : tests sur les états du ViewModel, pas
sur ExoPlayer lui-même.

## Conventions

Code, commentaires, tests et commits en anglais ; toutes les chaînes UI en français dans
`values/strings.xml` (défaut). Commits conventionnels (`feat:`, `fix:`, `docs:`…). Trunk-based :
branches courtes, PR sur `main`, 1 PR = un lot cohérent. Tout ce qui contredit un ADR s'arrête et
se signale — les ADR ne se contournent pas en silence.
