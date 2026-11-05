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
  catalogue, découvert/mesuré 2026-11-05)
- `id=e78b188` variables `{"query":…}` → searchPrograms
- `id=b2dc9439` variables `{"query":…}` → searchVideos
- vidéos d'un programme : `id=a6f9cf0e` (mesuré 2026-11-05) variables `{"programSlug":…,"offset":N,
  "limit":20,"types":["REPLAY"],"sort":{"type":"DATE","order":"DESC"}}` → `programBySlug.videos`
  {items, total} (pagination par 20)
- détail d'une vidéo + id média interne : hash yt-dlp
  `9b80783950b85247541dd1d851f9cc7fa36574af015621f853ab111a679ce26f` variables
  `{"programSlug":…,"slug":…}` → `videoBySlug.streamId` (id numérique wat)

Ne garder que `type: REPLAY` (les `EXTRACT`/`BONUS` sont hors périmètre). La taxonomie
`GT_*`/`SGT_*` de `program.categories[]` mappe vers les Genres canoniques (table en code ;
pas de policier/thriller natif TF1 → retombe sur Séries/Action).

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
`GET /apps/program/<program_path>?platform=apps` → collections étiquetées « Saison N »
(type `playlist_video`) → `items[]` plats avec `si_id`, `season`, `episode`, `duration`,
`type: integrale` ; `/apps/program/meta/<path>` donne `category` + `sub_categories` (→ Genres
canoniques ; mapping natif riche : séries-policieres, thriller, comédie, drame, doc histoire…).

Flux — `GET https://k7.ftven.fr/videos/<si_id>?country_code=FR&capabilities=drm&os=androidtv&offline=false`
→ `video.url` → signer via `GET https://hdfauth.ftven.fr/esi/TA?format=json&url=<url>` → URL signée
courte durée. Sans DRM sur les contenus vérifiés.

## Cache et mode dégradé (ADR 0002)

Room : `shows`, `seasons`, `episodes`, `genre_map` + `progress`, `favorites`. TTL 12 h, refresh au
démarrage en arrière-plan, priorité Home. Provider KO au refresh → servir le cache + bannière
« Données du [date] ». La résolution du Flux reste toujours live (URLs signées, courte durée).

## Player

Media3/ExoPlayer, DASH et HLS auto-détectés. **Sous-titres FR activés par défaut** quand la piste
existe (choix produit, PRD §utilisateurs), toggle en un appui. Erreur de résolution → 1 retry →
écran d'erreur avec bouton Réessayer. Reprendre : position sauvegardée à 5 s d'intervalle + à la pause.

## Recherche

Cross-chaînes, debounce 300 ms, minimum 3 caractères, fusion des Provider.search() par titre
normalisé (casse/accents). La recherche TF1 utilise searchPrograms ; France TV : filtrage local du
cache (pas de route de recherche découverte).

## Auto-update

Au lancement : `GET /repos/ouro84/replaybox/releases/latest` (API GitHub) → si tag > version courante,
notification → téléchargement APK → intent d'installation. Silencieux si à jour.

## CI

- **PR** : `./gradlew assembleDebug testDebugUnitTest`.
- **Nightly health-check** : un épisode TF1 et un épisode France TV poussés **jusqu'à l'URL du
  manifest** ; échec → alerte. C'est le seul test d'intégration réel ; tout le reste est sur fixtures.

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
