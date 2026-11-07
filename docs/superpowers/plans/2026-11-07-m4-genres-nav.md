# M4 PR 6 — Genres navigation + « Tout voir » grid Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A « Genres » destination in the nav rail opens a 3 × 3 page of Genre tiles; each tile, and a « Tout voir » card at the end of each truncated Home Genre rail, opens the « Tout voir » grid of that Genre (every Chaîne, recency then title, Chaîne-group chips).

**Architecture:** Pure logic in `core.data.GenreGrid` (recency key, sort, dedupe, Chaîne filter) and `app.ui.GenreFilters` (which chips exist). `CatalogStore.genreGrid(genre, channelIds)` reads the memoized cached listings (no network, ADR 0002) and returns the arranged list; `CatalogStore.genreRails` now returns `GenreRail(genre, shows, hasMore)`. The grid reuses the Search result card, extracted to `Components.kt` as `PosterCaptionCard`. Screens `Screen.Genres` and `Screen.GenreGrid` join the back stack; their states (`GenresUiState`, `GenreGridUiState`) live in `MainActivity` like `SearchUiState` / `HomeUiState`.

**Tech Stack:** Kotlin, Compose for TV (tv-foundation alpha07 `TvLazyVerticalGrid`), Room, JUnit4 + Truth.

**Spec:** `ROADMAP.md` §M4 (grilling 2026-11-07, bullet « Genres »), `CONTEXT.md` (Genre, Chaîne, Programme), `DESIGN.md`, lead brief (decisions 1–8).

## Recency signal (measured in the code, nothing invented)

What the cached listings carry per Programme (`ShowEntity`):

- `firstSeenAt` / `firstSeenRank`: the instant the device cache first listed the Programme, and its rank in that first listing. Kept across refreshes (`RoomCatalogDao.putListing`), so a Programme that appears today gets today's date.
- `recentEpisodeCount`: TF1 only (`replayRecent.total`), 0 for France TV and M6 (SPEC: « Pas de signal de récence dans le catalogue »). A count, not a date, and one Provider only: mixing it would push TF1 ahead of everyone.
- No listing carries the date of the latest Épisode. Épisode dates (`broadcastAt`, `publishedAt`) exist only for Programmes whose page was opened: using them would rank the Programmes the viewer happened to open first.

**Choice:** recency = the day the cache first listed the Programme (`firstSeenAt / 86 400 000`, the UTC day bucket `RoomRawDao.shows` already orders the Home rails by), newest day first; then title (normalized: case- and accent-insensitive). Same-day exact instants are not compared: one refresh writes the Chaînes a few seconds apart, so the exact instant would only sort by refresh order. On a fresh install every Programme shares one day and the grid reads alphabetically; from the next day on, newcomers lead. Documented in DESIGN.md « Tout voir ».

`Show` gains `firstSeenAt: Long = 0` (set by the cache, 0 from a Provider) so the store can sort across Chaînes.

## Review Focus

1. Sort is recency day then title, across Chaînes. Test: `GenreGridTest.newestDayFirstThenTitle`, `sameDayIgnoresRefreshOrder`.
2. A Chaîne-group chip keeps only that group's Chaînes; duplicates (same normalized title) are kept once, inside the filter. Test: `GenreGridTest.filterThenDedupe`.
3. A Home rail gets « Tout voir » only when truncated. Test: `HomeRailsTest` `genre rails say when they are truncated`.
4. M6+ chip only when its Chaînes are available. Test: `GenreFiltersTest`.
5. `CatalogStore.genreGrid` reads only the cache (provider never called) and applies `Genres.canonical`. Test: `GenreGridTest.storeReadsTheCacheOnly`.

---

### Task 1: Recency data + pure grid logic

**Files:**
- Modify: `core/model/Show.kt` (`firstSeenAt`), `core/data/RoomCatalogDao.kt` (map it), test `FakeCatalogDao` (copy it)
- Create: `core/data/GenreGrid.kt`; `CatalogStore.genreGrid`, `GenreRail` + `genreRails` → `hasMore`
- Test: `core/data/GenreGridTest.kt`, `HomeRailsTest.kt`

- [ ] Tests first (sort, same-day, filter + dedupe, store cache-only, rail truncation) → FAIL → implement → PASS.
- [ ] Commit `feat: genre grid ordering and truncated rails`

### Task 2: Shared poster card + chips logic

- [ ] Extract `SearchResultCard` to `Components.kt` as `PosterCaptionCard` (Search keeps its look); `GenreFilters.available(groups, channelIds)` in `app/ui/GenreFilters.kt` + `GenreFiltersTest`.

### Task 3: Screens and navigation

- [ ] `NavRail`: `RailDestination.GENRES` between Home and Search, Material « Category » icon (hand-built vector: the extended icon set is not a dependency), label « Genres ».
- [ ] `GenresScreen.kt`: title « Genres » (Display Compact), 3 × 3 tiles 256 × 112 dp, Raised Slate, Rail Title name bottom-left, card focus (1.1 + ring). Initial focus: first tile, or the tile opened last.
- [ ] `GenreGridScreen.kt`: Genre name (Display Compact) and the chip row as full-span items of a 6-column `TvLazyVerticalGrid` (104 dp → 48 dp safe edge, 20 dp gap), `PosterCaptionCard`s, skeleton posters while loading, « Aucun Programme dans ce Genre. » when empty. Chips: OK selects (never focus), selected = raised slate + check, focused = white + 1.06.
- [ ] Home: « Tout voir » card (116 dp 3:4, Raised Slate, Card Title label) after the last card of a truncated rail; its key `more:<GENRE>` is the Home focus key, so Back lands on it.
- [ ] `MainActivity`: `Screen.Genres`, `Screen.GenreGrid(genre)`; `GenresUiState` reset on each rail entry, one `GenreGridUiState` per opening (kept under a Programme page).
- [ ] Strings (fr, tutoiement) in `values/strings.xml`.
- [ ] Commit `feat: Genres page and Tout voir grid`

### Task 4: Docs + verification

- [ ] DESIGN.md: nav rail 4 items, sections « Genres » and « Tout voir » (recency proxy, focus lines), drop the « Not yet specified » line.
- [ ] `./gradlew testDebugUnitTest assembleDebug` green; push; PR (not merged). Box check by the lead.
