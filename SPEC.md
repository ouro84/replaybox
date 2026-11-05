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
+ UA navigateur) :
- `id=909c68c0` variables `{}` → catégories racine
- `id=e78b188` variables `{"query":…}` → searchPrograms
- `id=b2dc9439` variables `{"query":…}` → searchVideos
- vidéos d'un programme : `programBySlug` avec variables `{"programSlug":…,"offset":N,
  "types":["REPLAY"],"sort":{"type":"DATE","order":"DESC"}}`

Ne garder que `type: REPLAY` (les `EXTRACT`/`BONUS` sont hors périmètre). La taxonomie
`GT_*`/`SGT_*` de `program.categories[]` mappe vers les Genres canoniques (table en code ;
pas de policier/thriller natif TF1 → retombe sur Séries/Action).

Flux — résoudre l'id média interne puis suivre la chaîne de l'**extracteur `wat` de yt-dlp**
(api.wat.tv delivery, source de vérité : `yt_dlp/extractor/wat.py`). Sortie : manifest DASH sans DRM
+ piste sous-titres VTT. Porter fidèlement, ne pas réinventer.

## Provider France TV

Listing — `GET https://api-mobile.yatta.francetv.fr/apps/channels/<slug>?platform=apps`
(france-2, france-3, france-4, france-5, franceinfo) → collections `playlist_program`/
`playlist_mixed` → `items[].item` (title, si_id). Détail programme :
`/apps/program/meta/<path>?platform=apps` (category + sub_categories → Genres canoniques ; mapping
natif riche : séries-policieres, thriller, comédie, drame, doc histoire…).

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
