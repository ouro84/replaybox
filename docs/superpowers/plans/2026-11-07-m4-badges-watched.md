# M4 PR 7 — Freshness badges, « Marquer comme vu », expired Épisodes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show the single `FreshnessBadge` of PR 1 as a text pill on Programme page Épisode rows and (expiry only) on Home Reprendre cards, hide expired Épisodes from Reprendre, the billboard and the Épisode rows, and let the viewer mark an Épisode watched or unwatched from the remote's Options key.

**Architecture:** No schema change. The expiry of a Reprendre card comes from the `episodes` cache (`availableUntil`, schema v5) joined by Épisode id in `CatalogStore.continueWatching` through a new `CatalogDao.episodesById`; no network call. A row missing from the cache has no badge and never counts as expired. Expired Épisodes are filtered in `CatalogStore` (`continueWatching`, `resumeFor`, `episodes`), so every caller (Programme page, Épisode suivant queue, billboard) sees the same list. « Marquer comme vu » writes a Progression at 100 %; « Marquer comme non vu » deletes it.

**Tech Stack:** Kotlin, Compose for TV, Room 2.5, JUnit4 + Truth.

**Spec:** `ROADMAP.md` §M4 (« Badges de fraîcheur », « À suivre »), `SPEC.md` « Fraîcheur (`FreshnessPolicy`) », `DESIGN.md`, `CONTEXT.md`.

## Decisions

- **Label** (pure, `app/ui/Freshness.kt`): `LastDay` → « Dernier jour », `DaysLeft(1)` → « Plus qu'un jour », `DaysLeft(n)` → « Plus que n jours » (`plurals`), `LastNight` → « Hier soir », `New` → « Nouveau ».
- **Row badge:** `FreshnessPolicy.badge`, except on a watched row (« ✓ Vu » already says it all: no badge).
- **Card badge:** `FreshnessPolicy.expiryBadge(availableUntil, now)` (new public function, the expiry half of `badge`), replacing the « N min restantes » line; the bar stays.
- **Pill:** Meta, Mist, pill shape, 10 x 2 dp padding. Fill Raised Slate; on a focused Épisode row (whose plate is Raised Slate) the fill becomes Slate Surface so the pill stays visible (Mist on Slate Surface 9:1). Never amber.
- **Watched with an unknown duration:** the duration falls back to the saved row, then the cached Épisode; with none, the row stores `duration = position = max(saved position, 30 s)`, which the existing `watched` rule (`position ≥ 95 % of duration`) and `ResumePolicy` already read as finished. No new column.
- **Dialogs:** shared `ActionDialog` (440 dp Slate Surface, Rail Title, panel buttons, « Annuler » first and focused). Programme page: Épisode title, « Annuler » + « Marquer comme vu » / « Marquer comme non vu » in one row. Reprendre card: Programme title, Épisode title as hint, « Annuler », « Marquer comme vu », « Retirer de Reprendre »: three buttons overflow the 392 dp row, so they stack full width.

## Tasks

- [ ] `FreshnessPolicy.expiryBadge` + tests (expired → null, LastDay, DaysLeft, beyond 7 days → null).
- [ ] `freshnessLabel` / `rowBadge` + tests.
- [ ] `CatalogDao.episodesById` (Room `IN` query, chunked; fake DAO).
- [ ] `ResumeItem.availableUntil`; `continueWatching` and `resumeFor` skip expired rows (an expired row does not claim its Programme) + tests.
- [ ] `CatalogStore.episodes` drops expired Épisodes (fresh cache and network) + test.
- [ ] `markWatched` / `markUnwatched` + tests (known duration, duration from cache, unknown duration, context kept, leaves Reprendre, unwatched removes the row).
- [ ] UI: pill on rows and cards, MENU on rows, `ActionDialog` on both screens, strings.
- [ ] DESIGN.md: badge pill, mark-watched dialog, expired handling; drop « Freshness or "new" badges » from « Not yet specified ».
- [ ] `./gradlew testDebugUnitTest assembleDebug`.

## Review Focus

1. An Épisode absent from the `episodes` cache keeps its Reprendre card, without badge (test `uncachedEpisodeStaysWithoutExpiry`).
2. An expired newest row does not hide an older unexpired one of the same Programme.
3. Marked watched leaves Reprendre and reads `watched` on the Programme page, with and without a known duration.
