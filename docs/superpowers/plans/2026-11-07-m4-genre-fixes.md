# M4 PR 2 — Genre fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the known false positives of « Policier & Thriller » on TF1 and make Jeunesse exclusive, so the Séries rail no longer mixes kids' and adult Programmes. No UI change.

**Architecture:** The TF1 crime heuristic moves to series only (`GT_SERIES_AND_FICTIONS`): a keyword in the title, or two distinct keywords in the synopsis; films get « Policier & Thriller » only from the native `SGT_THRILLER` / `SGT_DETECTIVE`. Jeunesse exclusivity is one pure rule on the Genre set (`Genres.canonical`), applied by `CatalogStore` to every listing it serves, fresh or cached: the rule reaches Indexes already stored on devices at the first read after the update, even offline. The TF1 parts (crime rule, kids slugs) land with the Index rebuilt at the first launch: `refreshListings` already ignores the TTL once per process (ADR 0002: the old Index stays served until a new crawl succeeds), and an APK update always starts a new process. No schema or version bump needed.

**Tech Stack:** Kotlin, kotlinx.serialization JSON, JUnit4 + Truth, JSON fixtures (zero network).

**Spec:** `ROADMAP.md` §M4 (grilling 2026-11-07, bullet « Genres » ; « Catalogue (dettes M3) »), `CONTEXT.md` (Genre, Index), `SPEC.md` §Provider TF1+ (Genres, Index), §Provider France TV, §Provider M6+ (`M6Genres`), `docs/adr/0002-cache-first-catalog.md`.

## Global Constraints

- Code, comments, tests and commits in English; SPEC.md in French.
- Never invent an ID or an endpoint: fixtures are trimmed from the real 2026-11-07 capture (`483ce0f` without filter, 10 pages of 500, total 4687).
- Rule changes are decided (grilling 2026-11-07); a real crime series that the new rule drops goes into the include slug list with a comment, the rule is not loosened.
- Jeunesse sources: TF1 `GT_YOUTH` (+ `SGT_SIX_TO_TEN_YEARS` / `SGT_THREE_TO_SIX_YEARS`), France TV category `enfants`, M6 `neustar.category` `kids` / `tous-petits` / `teens` and type `dessin-anime` (by category, never by the Gulli Chaîne).
- TF1 files a few kids' cartoons as plain fiction (`GT_SERIES_AND_FICTIONS` + `SGT_SERIES`, no youth signal): Pokémon, Yu-Gi-Oh!, Miraculous Chibi. A commented slug list maps them to Jeunesse; extend only with a fixture case each.

## Review Focus

1. A Programme mapped to Jeunesse never shows in another rail. Test: `GenresTest.youthIsExclusive`, `CatalogStoreTest.youthIsExclusiveInCachedListings`.
2. TF1 Pokémon is in the Jeunesse rail only. Test: `Tf1YouthTest.pokemonIsYouthOnly`.
3. A film never gets « Policier & Thriller » from keywords. Test: `Tf1IndexParserTest.filmsGetCrimeOnlyFromNativeSgt` (Twilight, La Ligne verte).
4. La desalmada (one keyword in the synopsis) is out; Alice Nevers (a real crime series with one keyword) stays in via the include list. Test: `Tf1CrimeKeywordsTest.negatives` / `positives`.
5. An Index stored before the update serves the new Jeunesse rule without a crawl. Test: `CatalogStoreTest.youthIsExclusiveInCachedListings` (cache fresh, provider never called).

---

### Task 1: Jeunesse exclusive, one rule in the store

**Files:**
- Create: `app/src/main/java/com/replaybox/core/model/Genres.kt` — `object Genres { fun canonical(genres: Set<Genre>): Set<Genre> }`
- Modify: `app/src/main/java/com/replaybox/core/data/CatalogStore.kt` — `listing()` and the fresh path of `shows()` map every Show through `Genres.canonical`
- Test: `app/src/test/java/com/replaybox/core/model/GenresTest.kt`, `CatalogStoreTest.kt`

- [ ] Step 1: tests `youthIsExclusive` ({YOUTH, SERIES, COMEDY} → {YOUTH}; {SERIES, CRIME_THRILLER} unchanged) and `youthIsExclusiveInCachedListings` (cached Show {SERIES, YOUTH} → absent from the SERIES rail, present in YOUTH, `shows(provider, SERIES)` empty, provider not called).
- [ ] Step 2: run → FAIL. Step 3: implement. Step 4: full suite → PASS.
- [ ] Step 5: commit `feat: make Jeunesse an exclusive Genre`

### Task 2: TF1 « Policier & Thriller » rule

**Files:**
- Modify: `app/src/main/java/com/replaybox/provider/tf1/Tf1CrimeKeywords.kt` — keywords as a list of distinct roots; `matches` = slug lists, then title hit, else ≥ 2 distinct roots in the synopsis; include list extended with the real crime series that the rule drops (measured on the capture)
- Modify: `app/src/main/java/com/replaybox/provider/tf1/Tf1Parsers.kt` — heuristic on `GT_SERIES_AND_FICTIONS` only
- Modify fixture: `tf1_crime_cases.json` — add la-desalmada, twilight-chapitre-iii-hesitation, la-ligne-verte, la-ligne-verte-919 (real items), plus les-petits-meurtres-dagatha-christie (title hit)
- Test: `Tf1CrimeKeywordsTest.kt`, `Tf1IndexParserTest.kt`

- [ ] Step 1: tests — positives (profilage, hpi, experts, section-de-recherches, mentalist, alice-nevers via include list, petits meurtres via title); negatives (+ la-desalmada); `oneSynopsisKeywordIsNotEnough`, `twoDistinctSynopsisKeywordsMatch`, `sameKeywordTwiceCountsOnce`; parser: Twilight / La Ligne verte films without CRIME_THRILLER, a native SGT_THRILLER film keeps it; julie-lescaut and hercule-poirot keep it.
- [ ] Step 2: run → FAIL. Step 3: implement. Step 4: full suite → PASS.
- [ ] Step 5: commit `fix: tighten TF1 Policier & Thriller heuristic`

### Task 3: TF1 kids' cartoons filed as fiction

**Files:**
- Modify: `app/src/main/java/com/replaybox/provider/tf1/Tf1Genres.kt` — `YOUTH_SLUGS` (pokemon, yu-gi-oh, miraculous-chibi) and `fun forProgram(slug, categoryIds)`
- Modify: `Tf1Parsers.kt` — use it; crime heuristic skipped for Jeunesse
- Create fixture: `tf1_youth_cases.json` — real items pokemon, yu-gi-oh, miraculous-chibi, plus one native `GT_YOUTH` Programme
- Test: `app/src/test/java/com/replaybox/provider/tf1/Tf1YouthTest.kt`

- [ ] Step 1: tests — `pokemonIsYouthOnly` (index page → store → Jeunesse rail only, not Séries), `nativeYouthIsYouthOnly`.
- [ ] Step 2: run → FAIL. Step 3: implement. Step 4: full suite + `assembleDebug` → PASS.
- [ ] Step 5: commit `fix: map TF1 kids' cartoons to Jeunesse`

### Task 4: M6 / France TV coverage + SPEC

- [ ] Tests: `M6GenresTest` — `kids` + `episode` (Zig et Sharko, real pair from the capture) is Jeunesse only after `Genres.canonical`; `FtvGenresTest` — `enfants` + a series sub-category is Jeunesse only.
- [ ] SPEC.md §Provider TF1+ Genres paragraph: new rule, before/after counts « mesuré le 2026-11-07 », kids slug list; §France TV and §M6+ Genres: Jeunesse exclusive; §Cache: when the new mapping reaches the device.
- [ ] Commit `docs: SPEC genre rules measured on 2026-11-07`
