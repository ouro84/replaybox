# M4 PR 1 — Freshness data (no UI) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every Épisode the dates the M4 freshness badges need (end of availability, online publication, broadcast instant with time of day), measured on the real APIs and documented in SPEC.md, persisted in the Room episodes cache, plus a pure `FreshnessPolicy` that picks at most one badge. No UI, no strings: later PRs display the badges.

**Architecture:** Three nullable `Instant` fields on `core.model.Episode`, filled by each Provider parser from the listing call the app already makes (no extra request per Épisode), stored as epoch-millis columns of `episodes` (schema v5, additive migration). `core.data.FreshnessPolicy` is a pure object: `(Episode, now: Instant) -> FreshnessBadge?` in the Europe/Paris zone, plus `isExpired`.

**Tech Stack:** Kotlin, kotlinx.serialization JSON, Room 2.5, JUnit4 + Truth.

**Spec:** `ROADMAP.md` §M4 (« Badges de fraîcheur »), `SPEC.md` Provider sections, `CONTEXT.md`.

## Measurements (2026-11-07, curl/urllib from France, calls already in SPEC)

- **France TV** — `/apps/program/<path>?platform=apps&size=2000`, items `integrale`: `end_date` (unix s) = end of availability, matches france.tv « Disponible jusqu'au » on 6 Épisodes (journal 20h ×2, Capitaine Marleau ×2, Envoyé spécial ×1, archive); `2147483647` (2038-01-19) = no end (« Disponible jusqu'au 19/01/2038 » on the site) → null. `begin_date` = start of online availability (journal: broadcast end + ~2 min; Capitaine Marleau S4E17 put online 8 days before its broadcast). `broadcast_begin_date` = broadcast start with seconds (journal 19:57:47) — but france.tv shows « Diffusé le … à … » **only when `broadcast_channel` is set**; items with `broadcast_channel: null` (web exclusives, catalogue re-uploads) carry a placeholder (= `begin_date`, or a non-matching time). `content_diffusion_date` (piano marker) is day-only `DDMMYYYY` — not used.
- **TF1+** — `a6f9cf0e` items: `date` (ISO UTC) = broadcast start (JT 19:57:51 Paris, Demain nous appartient 19:13, Les feux de l'amour 10:57); old back-catalogue has placeholders (Profilage `2023-09-01T07:00:00Z`). `published` (ISO UTC) = online publication (JT: ~40 min after the start; soaps: = `date`; avant-premières: days before `date`). **No absolute end date**: `playingInfos.remainingDays` is an integer day count computed by the server at response time, rounded up (Les feux de l'amour: broadcast + 30 days → 29 for the 6/11 Épisode, 7 for the 15/10 one at 14:48). The tf1.fr page and `videoBySlug` carry the same count only. `freeRemainingDays` equals it except on avant-premières (0, premium only). `extensions.report.execution.start` (epoch ns) gives the server instant of the count.
- **M6+** — videos of a Programme: `clips[0].product.first_diffusion` = first broadcast, Paris local time without offset (« 2026-11-05 21:10:00 », prime time M6). `last_diffusion` = latest rerun (Gulli reruns). `publication_date` (Paris local) is **rewritten at broadcast + 7 days** (Wanted E2, Scènes de ménages, 66 minutes: the previous Épisode gets a new date when the next one is published) → not a first publication date, not used. **No end of availability** in the listing, in the single-video call, nor in the catalogue (`rights` null; `publication_date_vi.date_end_max` is per Programme).

## Model decisions

```kotlin
data class Episode(
    …existing fields (broadcastDate display string unchanged)…,
    val broadcastAt: Instant? = null,    // broadcast start, time of day known
    val publishedAt: Instant? = null,    // start of online availability
    val availableUntil: Instant? = null, // end of availability (TF1: day-precision upper bound)
)
```

- `broadcastAt` (not `broadcastDateTime`): an instant, the existing `broadcastDate` stays the display string so no screen changes. France TV: `broadcast_begin_date` only when `broadcast_channel != null`. TF1: `date`. M6: `first_diffusion` read in Europe/Paris.
- `publishedAt`: France TV `begin_date`, TF1 `published`, M6 null.
- `availableUntil`: France TV `end_date` (sentinel → null). TF1: `executionStart (else fetch clock) + remainingDays × 24 h` — an upper bound: the true end lies in the last 24 h before it. Erring late never hides a playable Épisode; the cost is « Plus que 1 jour » instead of « Dernier jour » on TF1's last day. M6 null.
- Defaults `= null` keep every existing constructor call compiling.

## FreshnessPolicy rules

```kotlin
sealed interface FreshnessBadge {
    object LastDay : FreshnessBadge
    data class DaysLeft(val days: Int) : FreshnessBadge   // 1..7
    object LastNight : FreshnessBadge
    object New : FreshnessBadge
}
object FreshnessPolicy {
    val ZONE: ZoneId = ZoneId.of("Europe/Paris")
    fun badge(episode: Episode, now: Instant): FreshnessBadge?
    fun isExpired(episode: Episode, now: Instant): Boolean // availableUntil < now
}
```

1. Expired → no badge. Expiry: `LastDay` when `availableUntil` falls before the next Paris midnight; else `DaysLeft(n)` with **n = Paris calendar days between today and the expiry day** (expires tomorrow → 1), only when n ≤ 7. Calendar days, not 24 h blocks: consistent with « Dernier jour » = day 0 and immune to DST (a 23 h or 25 h day still counts 1).
2. `LastNight`: `broadcastAt` on yesterday's Paris date at or after 18:00 local.
3. `New`: `publishedAt ?: broadcastAt` within `[now − 48 h, now]` (a future publication is not new).

## Review Focus

1. A France TV item with `broadcast_channel: null` never gets `broadcastAt` (would fake « Hier soir »). Test `webOnlyItemHasNoBroadcastInstant`.
2. `end_date = 2147483647` → `availableUntil == null`. Test `noEndSentinelIsNull`.
3. TF1 `remainingDays` is anchored on the server execution instant, falls back to the fetch clock. Tests in `Tf1FreshnessParserTest`.
4. M6 `first_diffusion` across DST (summer +02:00, winter +01:00). Test `firstDiffusionIsParisTime`.
5. Policy edges: 23:59:59 vs next midnight, DST changeover days, null fields, priority order. `FreshnessPolicyTest`.
6. Migration 4 → 5 is additive (Progression and Favoris survive); episode freshness markers are cleared so cached Épisodes refetch with the new fields.

---

### Task 1: Model + fixtures + parsers

**Files:**
- Modify: `app/src/main/java/com/replaybox/core/model/Episode.kt`
- Modify: `provider/francetv/FtvParsers.kt`, `provider/tf1/Tf1Parsers.kt`, `provider/tf1/Tf1Provider.kt` (clock), `provider/m6/M6Parsers.kt`
- Fixtures (trimmed from the 2026-11-07 captures): `ftv_program_journal-20h00_freshness.json`, `ftv_program_envoye-special_freshness.json`, `tf1_program_by_slug_jt_20h_freshness.json`, `tf1_program_by_slug_feux_de_l_amour_freshness.json`, `m6_videos_28540_freshness.json`, `m6_videos_829_no_diffusion.json`
- Tests: `FtvFreshnessParserTest`, `Tf1FreshnessParserTest`, `M6FreshnessParserTest`

- [ ] Step 1: failing tests (fixture values pinned to the measured instants). Step 2: run → FAIL. Step 3: implement. Step 4: run → PASS. Step 5: commit `feat: parse Episode freshness dates from the three Providers`

### Task 2: Room schema v5

**Files:** `core/data/CatalogEntities.kt` (3 nullable `Long` columns + `toModel()`/`toEntity()` mapping), `core/data/RoomCatalogDao.kt`, `core/data/ReplayboxDatabase.kt` (`MIGRATION_4_5`), `app/ReplayboxApplication.kt` (register it).
- Test: `EpisodeEntityMappingTest` (round trip with and without dates). No Room instrumentation in this repo (no Robolectric): the migration SQL mirrors the previous additive ones.
- [ ] Steps: failing test → implement → full suite → commit `feat: persist Episode freshness dates (schema v5)`

### Task 3: FreshnessPolicy

**Files:** `core/data/FreshnessPolicy.kt`, test `core/data/FreshnessPolicyTest.kt`.
- [ ] Steps: failing tests → implement → commit `feat: FreshnessPolicy picks one freshness badge per Episode`

### Task 4: SPEC + verification

- [ ] SPEC.md: one paragraph per Provider, next to its Épisode listing, « mesuré le 2026-11-07 ».
- [ ] `./gradlew testDebugUnitTest` and `./gradlew assembleDebug` green; push `feat/m4-freshness-data`; PR on `main` (not merged).
