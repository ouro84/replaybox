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

Ne garder que `type: REPLAY` (les `EXTRACT`/`BONUS` sont hors périmètre). La taxonomie
`GT_*`/`SGT_*` de `program.categories[]` mappe vers les Genres canoniques (table en code ;
chaque programme porte exactement un `GT_` et un `SGT_`, ids `SGT_` en anglais : `SGT_COMEDY`,
`SGT_DRAMA`, `SGT_DOCUMENTARY`… ; Genres = union des deux). Pas de policier/thriller natif TF1 pour
les séries → retombe sur Séries ; `SGT_THRILLER`/`SGT_DETECTIVE` n'existent que sur les films
(`GT_MOVIES`), mesuré 2026-11-07 sur le catalogue complet. Pour les fictions et films
(`GT_SERIES_AND_FICTIONS`/`GT_MOVIES`), « Policier & Thriller » est ajouté par une heuristique de
mots-clés sur titre + synopsis normalisés (`Tf1CrimeKeywords`, 555 des 2446 fictions/films du
groupe sur la capture du 2026-11-07), avec deux listes de slugs commentées pour les ratés connus
(fixture `tf1_crime_cases.json`).

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
`GET /apps/program/<program_path>?platform=apps&size=2000` → collections étiquetées « Saison N »
(type `playlist_video`) → `items[]` plats avec `si_id`, `season`, `episode`, `duration`,
`type: integrale` ; `/apps/program/meta/<path>` donne `category` + `sub_categories` (→ Genres
canoniques ; mapping natif riche : séries-policieres, thriller, comédie, drame, doc histoire…).

Détails mesurés le 2026-11-05 (lot 1 M1, fixtures `ftv_*.json`) :
- **Plafond 250** (mesuré 2026-11-06) : sans `size`, chaque collection s'arrête à 250 items.
  `page=N` pagine la collection principale par 250 (les thématiques ignorent `page` et se
  répètent) ; `size=2000` rend tout en un appel — journal-20h00 249 → 443 intégrales,
  ca-commence-aujourd-hui 274 → 495, 13h15-le-samedi 205 → 253. Séries numérotées : complètes
  sans `size`. Fixture : `ftv_program_13h15-le-samedi_size2000.json` (items élagués des champs
  `images`/`program`/`channel`/`category`/`marker*`).
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

## Cache et mode dégradé (ADR 0002)

Room : `shows`, `seasons`, `episodes`, `genre_map` + `progress`, `favorites`. TTL 12 h, refresh au
démarrage en arrière-plan, priorité Home. Provider KO au refresh → servir le cache + bannière
« Données du [date] ». La résolution du Flux reste toujours live (URLs signées, courte durée).
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
  issue existe, sinon inconnu. Combinaison CI × box : KO si l'un est KO, sinon OK si l'un est OK,
  sinon inconnu (un côté inconnu est une preuve manquante, pas une panne). Affiché seulement dans
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
