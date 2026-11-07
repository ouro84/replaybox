# M3 step 4a — Provider M6+ (anonymous part) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Everything of the M6+ Provider that works without an account: catalogue into the generic Index, Programme → Saison → Épisode, manifest resolution, M6 SPEC section and an anonymous health-check probe. Not wired into the UI yet: the DRM spike (needs Arnaud's credentials) decides whether M6+ ships in M3 (ROADMAP §M3, Q22 rule).

**Architecture:** `provider.m6` mirrors `provider.tf1`: `M6Http` (OkHttp, returns body + headers), `M6Parsers` (pure, fixture-tested), `M6Genres`, `M6Provider` (one instance per Chaîne, sharing one `CatalogueIndex` built by `m6CatalogueIndex(http)`). `stream()` needs a DRM token provider that step 4b (auth) will supply; without it, it throws the existing account-required error. A public `manifestUrl(episode)` serves the probe.

**Tech Stack:** Kotlin, OkHttp, kotlinx.serialization JSON, JUnit4 + Truth.

**Spec:** `ROADMAP.md` §M3, measurements of 2026-11-07 (below), `SPEC.md` (TF1/FTV sections as the documentation model), `CONTEXT.md`.

## Global Constraints

- Platform `m6group_web` everywhere (decision Q18). Host `https://pc.middleware.6play.fr/6play/v2/platforms/m6group_web`. Headers on every call: `x-customer-name: m6web` + the browser UA already used by `Tf1Http.BROWSER_UA`. No auth for any call in this plan.
- Measured 2026-11-07 (anonymous, 200):
  - Catalogue: `GET {host}/services/6play/programs?limit=100&offset={itemOffset}&csa=6&with=rights` — `limit` capped at 100, `offset` is an **item** offset, total in response header `x-rest-collection-count` (1288). Body = JSON array of programmes. Occasional Varnish 503 HTML → retried by `CatalogueIndex`. Offset paging drifts slightly → dedupe by id (done by `CatalogueIndex`).
  - Programme fields used: `id`, `title`, `code`, `description`, `images[]`, `service_display.code`, `count.vi`, `neustar.category`, `program_type_wording.code`.
  - Videos of a Programme: `GET {host}/services/{serviceCode}/programs/{programId}/videos?csa=6&with=clips,freemiumpacks&type=vi&limit=100&offset={itemOffset}` — same cap and count header (Pékin Express 945: 155). `type=vi` = full Épisodes only (decision: no `vc` extracts).
  - One video: `GET {host}/services/{serviceCode}/videos/{clipId}?csa=6&with=clips,freemiumpacks` → object with `clips[0].assets[]`.
  - Épisode numbering: `clips[0].product.season`, `.episode`, `.part_number`, `.first_diffusion`, `.season_title`; video `id` like `clip_13179913`, `duration` in seconds, `title`, `description`, `images`.
  - Assets: `usp_dashcenc_h264` (DASH, CENC Widevine) with `video_quality` `sd`/`hd`, URL in `full_physical_path`; `usp_hlsfp_h264` is FairPlay → ignored. MPD has 1 Period, no ad markers, TTML subtitles in the MPD.
- Chaînes kept (decision Q16), keyed on `service_display.code`: `m6replay` → `Channel("m6", "M6")`, `w9replay` → `Channel("w9", "W9")`, `6terreplay` → `Channel("6ter", "6ter")`, `gulli` → `Channel("gulli", "Gulli")`, `6play` → `Channel("m6plus", "M6+")`. Everything else (parispremierereplay, tevareplay, 6playmaxott, podcast…) is dropped; Programmes with `count.vi == 0` are dropped (measured: 872 kept of 1286).
- Ids are namespaced with prefix `m6:` (like France TV's `FtvParsers.ID_PREFIX`) so `Providers.resolveShow` can route them later: Show `m6:{serviceCode}:{programId}`, Season `m6:{serviceCode}:{programId}:{seasonNumber or ""}`, Episode `m6:{serviceCode}:{clipId}`.
- `recentEpisodeCount` for ranking: number of `vi` videos is the only count available → use `0` (no recency signal) rather than inventing one.
- Zero network in unit tests; fixtures trimmed from the captures in `C:\Users\Arnaud\AppData\Local\Temp\claude\39557f7cc970b587\5bf95d13-6e8b-43fe-a66b-b646656da3b8\scratchpad\m6\` (`progpages/p_*.json` catalogue pages, `web_videos_945_vi.json`, `web_video_clip.json`, `mpd_945_hd.mpd`).

## Review Focus

1. A Programme without season numbers (magazine, film) → one Season « Épisodes » (number null), all its Épisodes inside. Test `seasonsWithoutNumbersCollapse`.
2. A Programme with > 100 Épisodes → all pages fetched (155 for Pékin). Test `videosArePaged` with two fixture pages.
3. Paris Première / Téva Programme in the catalogue page → never listed. Test `dropsExcludedChannels`.
4. Only FairPlay HLS assets, or no asset at all → clear `StreamResolutionException`, not a crash. Test `noDashAssetFails`.
5. `stream()` with no DRM token provider → account-required error (same type the player already maps to « compte requis »). Test `streamWithoutAccountIsAccountRequired`.

---

### Task 1: Parsers, genres, fixtures

**Files:**
- Create: `app/src/main/java/com/replaybox/provider/m6/M6Channels.kt` (the 5 Chaînes + `fun forService(code: String): Channel?`)
- Create: `app/src/main/java/com/replaybox/provider/m6/M6Genres.kt`
- Create: `app/src/main/java/com/replaybox/provider/m6/M6Parsers.kt`
- Fixtures: `m6_programs_page.json` (≈12 items: each kept Chaîne, one Paris Première, one Téva, one `count.vi == 0`, one magazine without seasons, one `serie-policiere`), `m6_videos_945_p0.json` + `m6_videos_945_p1.json` (trimmed to 3 + 2 videos), `m6_video_clip.json`, `m6_video_hls_only.json` (derived from the clip fixture with DASH assets removed — say so in the test).
- Test: `app/src/test/java/com/replaybox/provider/m6/M6ParsersTest.kt`, `M6GenresTest.kt`

**Interfaces:**
- Consumes: `IndexEntry`, `CataloguePage` (`provider.api.CatalogueIndex`, merged in step 2).
- Produces:
  ```kotlin
  object M6Parsers {
      const val ID_PREFIX = "m6:"
      fun catalogPage(body: String, totalHeader: Int?, pageIndex: Int, pageSize: Int): CataloguePage
      data class VideoPage(val episodes: List<M6Video>, val total: Int?)
      data class M6Video(val clipId: String, val title: String, val synopsis: String?, val season: Int?, val episode: Int?, val part: Int?, val firstDiffusion: String?, val durationSeconds: Int?, val thumbnailUrl: String?)
      fun videoPage(body: String, totalHeader: Int?): VideoPage
      fun seasons(showId: ShowId, videos: List<M6Video>): List<Season>
      fun episodes(seasonId: SeasonId, serviceCode: String, videos: List<M6Video>): List<Episode>
      fun dashManifest(videoBody: String): String // hd before sd; throws StreamResolutionException-compatible error when none
  }
  object M6Genres { fun map(neustarCategory: String?, programType: String?): Set<Genre> }
  ```
- [ ] Step 1: failing tests:
  - `catalogPageKeepsChannelsAndVi`: fixture → only kept Chaînes, ids `m6:{service}:{id}`, channel ids from `M6Channels`; `dropsExcludedChannels`; `hasMore` = `(pageIndex + 1) * 100 < 1288` for header 1288.
  - `posterAndBackdropFromImages`: pick a portrait image for `posterUrl` and a landscape one for `backdropUrl` (inspect `images[].role`/format in the fixture and pin the roles in the test).
  - `videosArePaged` (parser side): page totals read from header value; `seasonsWithoutNumbersCollapse`; `seasonsSortedDescending` (newest Saison first, matching TF1's `seasonsFromVideos` order — check it and mirror); `episodesOrderedByEpisodeThenPart`.
  - `dashManifestPrefersHd`, `noDashAssetFails`.
  - `M6GenresTest`: `serie-policiere` → SERIES + CRIME_THRILLER; `telefilm-frissons` → SERIES + CRIME_THRILLER; `comedie`/`serie-humour` → COMEDY; `drame`/`series-dramatiques`/`telefilm-drame` → DRAMA; `documentaire` or type `documentaire` → DOCUMENTARY; `kids`/`tous-petits`/`teens` or type `dessin-anime` → YOUTH; `divertissement`/`serie-realite`/`cuisine`/`musique`/`spectacle`/`competition` or type `emission` → ENTERTAINMENT; `magazine-dinformation`/`magazine-d-information`/`info`/`infos`/`info-et-societe`/`sport` → SPORT_AND_INFO; type `film` → MOVIES; type `episode`/`telefilm` or category starting `serie`/`series`/`telefilm`/`telenovela` → SERIES; null/null → empty set.
- [ ] Step 2: run → FAIL. Step 3: implement. Step 4: run → PASS. Step 5: commit `feat: M6+ parsers and genre mapping`

### Task 2: `M6Http`, `M6Provider`, Index wiring (no UI)

**Files:**
- Create: `app/src/main/java/com/replaybox/provider/m6/M6Http.kt` — `open suspend fun get(path: String): M6Response` with `data class M6Response(val body: String, val totalCount: Int?)` (`x-rest-collection-count`), non-2xx → `M6HttpException(code)`; `PAGE_SIZE = 100`.
- Create: `app/src/main/java/com/replaybox/provider/m6/M6Index.kt` — `fun m6CatalogueIndex(http: M6Http): CatalogueIndex`.
- Create: `app/src/main/java/com/replaybox/provider/m6/M6Provider.kt` — `class M6Provider(channel: Channel, http: M6Http, index: CatalogueIndex, drmTokenProvider: (suspend (serviceCode: String, clipId: String) -> String)? = null, localSearch: (suspend (String) -> List<Show>)? = null) : Provider`. `shows` = index slice; `seasons`/`episodes` from all video pages (in-memory cache per Programme like `Tf1Provider.loadReplayVideos`, cap 1000); `search` = `localSearch?.invoke(q) ?: emptyList()` (decision Q17: local only); `suspend fun manifestUrl(episode: EpisodeId): String`; `stream` = without `drmTokenProvider` → throw the account-required `StreamResolutionException` path (reuse the existing exception type used by the player, see `PlayerScreen.kt:123`); with it → `Stream(manifest, DASH, licenseUrl = "https://lic.drmtoday.com/license-proxy-widevine/cenc/", licenseHeaders = mapOf("x-dt-auth-token" to token))` (licence response unwrapping is step 4b).
- Test: `app/src/test/java/com/replaybox/provider/m6/M6ProviderTest.kt` — fake `M6Http` serving fixtures by path: `fiveChannelsShareOneCrawl`, `videosArePaged` (calls offsets 0 and 100), `streamWithoutAccountIsAccountRequired`, `streamWithTokenCarriesDrmtodayHeader`, `searchIsLocalOnly` (no HTTP call).
- [ ] Steps: failing tests → run → implement → run full suite + assembleDebug → commit `feat: M6+ provider on the shared Index (not wired yet)`

### Task 3: Anonymous probe + SPEC + live check

**Files:**
- Create: `app/src/healthCheck/java/com/replaybox/healthcheck/M6Probe.kt` — name `m6`, label « M6+ (anonyme) »; steps `shows` (Chaîne `m6` non-empty) → `seasons` → `episodes` on the first listed M6 Programme that has a Season, then `manifest` = `manifestUrl()` is an https URL whose GET returns 200 and contains `<MPD`, **or** (if the GET is refused from outside France) stop at the URL like France TV's `signature` step — decide after the live run below and document it in the probe KDoc. Register it in `Probes.all`.
- Modify: `SPEC.md` — new §Provider M6+ with every measured fact of the Global Constraints (date 2026-11-07), the licence chain marked **SOURCED, not yet measured** (catchup-tv-and-more `resources/lib/channels/fr/6play.py` @ fa49e5a0c4bfb5f501ac8d5486c5f6465cc16745), and the probe description in §Health-check.
- [ ] Step 1: run the probe locally against the real API: `./gradlew testDebugUnitTest -PhealthCheck --tests "*M6Probe*"` (or the way `NightlyHealthCheck` is launched — read it) and paste the result in the PR body.
- [ ] Step 2: full suite + build green; commit `feat: anonymous M6+ health-check probe` and `docs: Provider M6+ in SPEC`.
- [ ] Step 3: push `feat/m3-m6-anonymous`, PR, merge when green (Arnaud's autonomy grant, 2026-11-07). No box check needed: nothing is visible in the UI yet — say so in the PR.
