# UX 1.0.1 PR C — Genre covers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Each of the 9 Genre tiles shows the artwork of the first Programme of that Genre's « Tout voir » grid that has an image, no Programme used twice, text-only tile when there is none.

**Architecture:** One pure object `GenreCovers` picks a cover URL per Genre from the Genres in glossary order and each Genre's grid already sorted by `GenreGrid`. `CatalogStore.genreCovers(channelIds)` builds those grids exactly as `genreGrid` does (same memoized cached listings, same `GenreGrid.arrange`, same clock), with no new DAO query and no network. `GenresScreen` reads the covers like `GenreGridScreen` reads its items (IO dispatcher, re-read on `catalog.updates`), keeps them in `GenresUiState` so Back from a grid shows them at once, and layers art + ground scrim inside the unchanged tile.

**Tech Stack:** Kotlin, Jetpack Compose (tv-foundation), Coil `AsyncImage`, Room cache behind `CatalogDao`, JUnit4 + Truth (`FakeCatalogDao`).

**Spec:** `docs/superpowers/specs/2026-11-11-ux-optimizations-design.md` § « PR C — Genre covers » (+ its Global constraints). Assumes PR A (`feat/ux-navigation`, rail on every mobile screen, ROADMAP section « Optimisations UX (1.0.1) ») and PR B (`feat/ux-player`) are merged on `main`.

## Global Constraints

- Branch `feat/ux-genre-covers` from up-to-date `main`. Box + mobile both change.
- Glossary as is (Programme, Genre, Chaîne, Index). Code, comments, tests, commits in English; no new UI string expected (French in `values/strings.xml` if one appears).
- Cache only (ADR 0002): covers come from the cached listings through `CatalogStore`; no Provider call, no new fetch. Image bytes load through Coil like every other card. Zero network in tests.
- Image choice: `backdropUrl ?: posterUrl` (a blank string counts as no image).
- « Same Chaînes as the grid » = the « Tout » chip the grid opens on: `Providers.all.map { it.channel.id }` in that order (M6+ only with an account). Never the chip last selected inside a grid.
- Tile unchanged: 3 × 3, 112 dp tall, `Modifier.weight(1f)`, 20 dp gaps, card focus (`focusScale` 1.1 + `cardFocus` ring), name in `RbType.railTitle` Paper White bottom-left, 20 × 16 dp inset, 2 lines max. Design tokens only (`Rb.*`, `RbType.*`).
- A tile with no cover stays pixel-identical to `main` (no scrim drawn). Everything outside the Genres page stays pixel-identical on the box.
- No layout or focus shift while covers load: initial focus (Séries, or the tile opened last) never waits for covers.
- minSdk 26, no dependency bump.
- Gates: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jre"` then `./gradlew testDebugUnitTest` and `./gradlew assembleDebug`, both green before each commit.
- Conventional commits, each ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. Cold start with an empty cache: 9 text tiles, then covers fade in when the first refresh ticks `catalog.updates`; focus stays on Séries. Tests: `GenreCoversTest.emptyCacheGivesNoCovers`; Pixel check after `pm clear` (Task 5).
2. The same Programme listed on two Chaînes (two ids, one title) in Séries and Policier & Thriller covers one tile only. Test: `GenreCoversTest.sameTitleOnTwoChannelsIsOneProgramme`.
3. A Provider sending `""` as an image URL: treated as no image, the next Programme is used. Test: `GenreCoversTest.blankUrlCountsAsNoImage`.
4. The tile cover is exactly the first imaged poster the « Tout voir » grid shows (recency order, Chaîne order, a Chaîne outside the list ignored). Test: `GenreCoversTest.coversFollowTheToutVoirGrid`.
5. An image URL that fails to load (404, offline) leaves today's text tile, not a darkened plate. Covered by the Task 3 layering rule (scrim tied to Coil success); checked offline on the Pixel (Task 5).

---

### Task 1: Pure cover selection

**Files:**
- Create: `app/src/main/java/com/replaybox/core/data/GenreCovers.kt`
- Test: `app/src/test/java/com/replaybox/core/data/GenreCoversTest.kt` (helpers `show(...)` from `CatalogTestHelpers.kt`, `.copy(backdropUrl = …, posterUrl = …)`)

**Interfaces:**
- Consumes: `ChannelShow`, `Show`, `Genre`, `Titles.normalize`.
- Produces:
  - `object GenreCovers`
  - `fun imageOf(show: Show): String?` — `backdropUrl ?: posterUrl`, blank → null.
  - `fun pick(genres: List<Genre>, grids: Map<Genre, List<ChannelShow>>): Map<Genre, String>` — resolved in `genres` order; per Genre the first entry with `imageOf != null` whose Programme is not used yet; a Genre with no such entry (or no grid) is absent. « Used » = the normalized title of each chosen entry (`GenreGrid`'s Programme identity across Chaînes), only chosen entries count.

- [ ] **Step 1: Write the failing tests** (`x`, `y`… are `ChannelShow`s; `bd(id)` = backdrop `"$id-bd"`, `po(id)` = poster `"$id-po"`)

```kotlin
@Test fun resolvesInGlossaryOrder() {
    // Map iteration order puts Policier first; the genres list decides.
    val covers = GenreCovers.pick(
        Genre.values().toList(),
        mapOf(Genre.CRIME_THRILLER to listOf(x, y), Genre.SERIES to listOf(x)),
    )
    assertThat(covers[Genre.SERIES]).isEqualTo("x-bd")
    assertThat(covers[Genre.CRIME_THRILLER]).isEqualTo("y-bd")
}
@Test fun aProgrammeCoversOneTileOnly()            // x first in Séries and Policier → Séries x, Policier y
@Test fun sameTitleOnTwoChannelsIsOneProgramme()    // "Koh-Lanta" tf1 id a (Séries), "Koh-Lanta" m6 id b (Policier) → Policier skips b
@Test fun backdropPreferredOverPoster()            // both set → "x-bd"
@Test fun posterWhenNoBackdrop()                   // backdrop null → "x-po"
@Test fun programmeWithoutImageIsSkipped()         // grid [none, y] → "y-…"; none is not marked used
@Test fun blankUrlCountsAsNoImage()                // backdropUrl = "", posterUrl = null → skipped
@Test fun emptyGridHasNoCover()                    // listOf() and missing key → assertThat(covers).doesNotContainKey(...)
@Test fun onlyUsedProgrammesLeaveATextTile()       // Policier grid = [x], x used by Séries → no Policier key
```

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "com.replaybox.core.data.GenreCoversTest"` → FAIL (`GenreCovers` unresolved).
- [ ] **Step 3: Implement** `GenreCovers` per Interfaces (one `HashSet<String>` of used normalized titles; KDoc cites spec § PR C).
- [ ] **Step 4: Run** the same command → PASS.
- [ ] **Step 5: Commit** `feat: pick Genre covers from the Tout voir order` (include this plan file)

### Task 2: Covers from the cache

**Files:**
- Modify: `app/src/main/java/com/replaybox/core/data/CatalogStore.kt` (next to `genreGrid`, ~line 512)
- Test: `app/src/test/java/com/replaybox/core/data/GenreCoversTest.kt`

**Interfaces:**
- Consumes: `GenreCovers.pick`, `GenreGrid.arrange`, private `listing(channelId)` (memo per listing date).
- Produces: `suspend fun genreCovers(channelIds: List<String>): Map<Genre, String>` — builds `all` once (`channelIds.flatMap { listing(it).map { ChannelShow(show, it) } }`, the exact expression `genreGrid` uses; extract it as a private `channelShows(channelIds)` shared by `genreGrid`, `browse` and this), then `Genre.values().associateWith { GenreGrid.arrange(all, it, null, clock()) }` with one `clock()` read, then `GenreCovers.pick(Genre.values().toList(), grids)`.
- Cost (checked in `CatalogDao`/`listing()`): one `listingAt` + at most one `dao.shows` per Chaîne thanks to the memo (the grid reuses the same memo right after); then 9 in-memory filter + sort over ~5 000 rows on `Dispatchers.IO`. No new query needed; do not add one.

- [ ] **Step 1: Write the failing tests** (`FakeCatalogDao`, clock fixed at `now`)

```kotlin
@Test fun coversFollowTheToutVoirGrid() {
    // tf1: "Abeille" SERIES undated, backdrop; france-2: "Zèbre" SERIES recencyAt = now, poster only;
    // m6 (not in channelIds): "Alpha" SERIES recencyAt = now, backdrop.
    val covers = catalog.genreCovers(listOf("tf1", "france-2"))
    assertThat(covers[Genre.SERIES]).isEqualTo("zebre-po")
    val first = catalog.genreGrid(Genre.SERIES, listOf("tf1", "france-2")).first { GenreCovers.imageOf(it.show) != null }
    assertThat(covers[Genre.SERIES]).isEqualTo(GenreCovers.imageOf(first.show))
}
@Test fun emptyCacheGivesNoCovers() { assertThat(catalog.genreCovers(listOf("tf1"))).isEmpty() }
```

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "com.replaybox.core.data.GenreCoversTest"` → FAIL (`genreCovers` unresolved).
- [ ] **Step 3: Implement** per Interfaces (KDoc: cache only, ADR 0002, same arrangement as `genreGrid`).
- [ ] **Step 4: Run** `./gradlew testDebugUnitTest` → all PASS (`GenreGridTest`, `CatalogBrowseTest` unchanged).
- [ ] **Step 5: Commit** `feat: read Genre covers from the cached listings`

### Task 3: Artwork on the Genre tiles

**Files:**
- Modify: `app/src/main/java/com/replaybox/app/ui/GenresScreen.kt`
- Modify: `DESIGN.md` § « Genres » (Tile line: « No image, no count » → the cover rule and the treatment decided in Step 1)

**Interfaces:**
- Consumes: `CatalogStore.genreCovers(channelIds: List<String>): Map<Genre, String>`, `Providers.catalog`, `Providers.all`.
- Produces: `GenresUiState.covers: Map<Genre, String>` (`mutableStateOf(emptyMap())`; one instance per rail entry already, so Back from a grid redraws covers without a fade); `private fun GenreTile(label: String, cover: String?, requester: FocusRequester, modifier: Modifier, onClick: () -> Unit)`.

- [ ] **Step 1: Load the `impeccable:impeccable` skill and decide the tile artwork crop, gradient and text treatment against `DESIGN.md`** (« Genres », Scrims, Contrast Floor Rule, Scrim Not Shadow, No Pure Black, Concentric Ring, Lift Is Focus). Fixed inputs: 256 × 112 dp tile (≈ 2.3:1) receiving 16:9 backdrops or 3:4 posters; name position, style and inset unchanged; ground-coloured gradient only (`Rb.ground`, never black), at least 92 % ground under the name; no shadow; focus untouched. Decide: `ContentScale.Crop` alignment (backdrop vs poster), gradient direction and stops, scrim fade-in. Write the result into `DESIGN.md` § « Genres ».
- [ ] **Step 2: Load covers.** In `GenresScreen`: `catalog.updates.collectAsState()`, `channelIds = remember { Providers.all.map { it.channel.id } }`, `LaunchedEffect(updates) { state.covers = withContext(Dispatchers.IO) { catalog.genreCovers(channelIds) } }`. The focus `LaunchedEffect(Unit)` stays as is and never waits for covers.
- [ ] **Step 3: Tile layers.** Box modifier chain unchanged except `padding` moves from the Box to the `Text` (same 20 × 16 dp inset). Inside, in order: `AsyncImage` (only when `cover != null`; `ImageRequest…crossfade(Rb.FOCUS_MS)`, `contentDescription = null`, `fillMaxSize`, crop per Step 1), the scrim (drawn only once Coil reports `AsyncImagePainter.State.Success`, alpha animated over `Rb.FOCUS_MS`), then the name. A pending or failed load therefore looks exactly like today's tile.
- [ ] **Step 4: Gates** `./gradlew testDebugUnitTest` and `./gradlew assembleDebug` → green (`GenreFocusTest` unchanged).
- [ ] **Step 5: Commit** `feat: show Programme artwork on the Genre tiles`

### Task 4: Bbox verification + ROADMAP

**Files:**
- Modify: `ROADMAP.md` § « Optimisations UX (1.0.1) » — tick the Genre covers item (the box only)

- [ ] **Step 1: Install.** adb at `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`, `adb connect <box-ip>:5555` (was `192.168.1.182`; scan `arp -a` if it moved), `install -r app/build/outputs/apk/debug/app-debug.apk`, cold start. Before **every** scripted key press: `dumpsys window | grep mCurrentFocus` must contain `com.replaybox`, else stop (wake with `KEYCODE_WAKEUP`, relaunch). Screencap before every OK; strip the bytes before `\x89PNG`.
- [ ] **Step 2: Genres page.** Rail → Genres: 9 tiles keep their size and grid; tiles with a cover show art + scrim, name readable at rest and at 1.1 focus, ring intact; no image repeated across tiles; focus on Séries at arrival and not moved while covers appear.
- [ ] **Step 3: Grid match and return.** OK on Policier & Thriller → its first imaged poster is the tile's Programme; Back → focus on the Policier & Thriller tile, covers shown at once (no fade, no flash). Repeat for one more Genre.
- [ ] **Step 4: A/B.** Same D-pad script on `main` APK and branch APK (Home, a « Tout voir » grid, Search, Paramètres): PIL `ImageChops` diff bbox `None` for every capture outside the Genres page; a text-only tile (if any) identical too. Undo any side effect (pin, Progression).
- [ ] **Step 5: Tick ROADMAP** item; commit `docs: tick Genre covers in ROADMAP`

### Task 5: Pixel 7 verification

- [ ] **Step 1: Install** on serial `2B151FDH2002RZ` (`svc power stayon usb` already on; if locked, ask Arnaud). `pm clear com.replaybox` (test data only), launch, open Genres from the rail right away: text tiles first, covers fade in once the refresh lands, nothing moves, no tile changes size (Review Focus 1).
- [ ] **Step 2: Compact canvas.** Screenshots: 3 × 3 tiles fit the 411 dp canvas as on `main` (scroll unchanged), names readable over art, tap a tile → grid, PR A back arrow → Genres with covers in place.
- [ ] **Step 3: Offline load failure.** Keep the Room cache, drop only Coil's images: `am force-stop com.replaybox`, `run-as com.replaybox rm -rf cache/image_cache`, then cut the network. The Pixel is Arnaud's personal phone: never toggle airplane mode, Wi-Fi or any global setting (`svc`, `settings put`, `cmd connectivity`) without his explicit OK; ask him to switch airplane mode himself, or skip this step and say so in the PR. Relaunch → Genres: tiles show today's plate + name, no dark scrim (Review Focus 5). Network back; covers fade in on the next visit. Never touch the launcher.
- [ ] **Step 4:** tablet class not checked on the Pixel (no `wm density`); emulator only if the lead asks.

### Task 6: Pull request

- [ ] **Step 1:** `git push -u origin feat/ux-genre-covers`.
- [ ] **Step 2:** `gh pr create --base main --title "feat: Genre covers (UX 1.0.1, PR C)"`; body: what changed, the three resolved choices (dedup by normalized title, « Tout » Chaînes, scrim on load success), box + Pixel results with screenshots, tests added; ending with `🤖 Generated with [Claude Code](https://claude.com/claude-code)`. Merge (Arnaud granted M3-style autonomy, 2026-11-11): once the final review is clean, gates green and the box + Pixel checks passed, `gh pr merge --merge --delete-branch`, then `git checkout main && git pull`.
