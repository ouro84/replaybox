# Mobile layouts — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Three layout classes in the single APK: the box keeps its TV layout untouched; a tablet (mobile, window height ≥ 480 dp) keeps the TV layout without the overscan margins; a phone in landscape (mobile, height < 480 dp) gets a « compact » class that fits the whole TV composition on screen.

**Architecture:** `LayoutClass.of(mobile, heightDp)` decides the class (pure, tested). The overscan insets (`Rb.safeHorizontal`, `Rb.safeVertical`) stop being constants: they read a `LocalInsets` composition local (TV 48/27 dp, mobile 24/16 dp). Compact renders the TV composition on a 540 dp-tall canvas: `MainActivity` provides a scaled `LocalDensity` whose density is `real × heightDp / 540` (so every TV dimension fits, nothing is redesigned per screen), with a font-scale compensation so text lands at 0.9 × its TV size instead of 0.76 ×. Android views inside Compose (the Search `EditText`) take their text size from that density, not from the system's.

**Tech Stack:** Kotlin, Compose 1.4, JUnit4 + Truth.

**Spec:** brief of 2026-11-10 (PR iii: « une classe compacte pour une hauteur < 480 dp. La tablette garde le layout TV, sans les marges d'overscan ») + grilling 2026-11-10 (auto-memory `mobile-milestone-decisions`). Arnaud delegated the design choices and the execution mode for this PR (2026-11-10).

## Global Constraints

- Box (LocalMobile false): class TV, insets 48/27, real density — pixel-identical to `main` (A/B screencap diff, as in PR i).
- Class is decided from the real window (`LocalConfiguration.screenHeightDp`), outside the scaled density.
- No per-screen redesign; no dependency bump; minSdk 26.
- Code/comments/tests/commits English; no new UI string.
- Build/tests as usual (`JAVA_HOME` = Android Studio jre). Branch `feat/mobile-layouts`; then the 1.0.0 release (separate step after merge).
- Devices: Pixel 7 (compact natively: 914×411 dp); tablet class emulated on the Pixel with `wm density 280` (≈ 1371×617 dp) and restored with `wm density reset` right after. Box A/B with the D-pad script.

## Decisions

| Class | When | Insets (h / v) | Density |
|---|---|---|---|
| TV | not mobile | 48 / 27 dp (5 % overscan) | system |
| TABLET | mobile, height ≥ 480 dp | 24 / 16 dp | system |
| COMPACT | mobile, height < 480 dp | 24 / 16 dp (in canvas dp) | `system × heightDp / 540`; font scale × `0.9 / (heightDp / 540)` |

- Canvas height 540 dp = the Bbox's 960×540 dp design canvas; a 914×411 dp phone becomes ≈ 1201×540 canvas dp (20:9, wider than the box: rails show more cards).
- Text factor 0.9 in compact: body 18 → ~16 sp, label 14 → ~12.6 sp, display 40 → 36 sp (phone at arm's length vs TV at 3 m).
- Accepted trade-off: touch targets shrink by the same factor (a 44 dp TV button ≈ 33 dp on the Pixel). Recorded; revisit with real use.
- Width-only constraints are unchanged (no wide/narrow split: landscape is enforced).

## Review Focus

1. Box: every screen pixel-identical to `main` (Home, Programme, Search, Genres, Paramètres, player controls).
2. Compact: nothing overflows the 411 dp height where it cannot scroll — Home panel + first rail, player controls, Options panel, Reprendre/Débuter prompt, Épisode suivant panel, ActionDialog. (Pixel screenshots.)
3. Compact: the Search field text is the same size as the rest of the compact UI (EditText sized from the scaled density, not 30 sp system).
4. Rotation/resize (split screen leaves, tablet with a smaller window): the class follows the new height without restarting the activity.
5. Tablet (emulated): TV layout with 24/16 margins, nothing scaled.

---

### Task 1: Layout class and insets

**Files:**
- Modify: `app/src/main/java/com/replaybox/app/ui/DeviceKind.kt`, `Theme.kt`, `MainActivity.kt`
- Test: `app/src/test/java/com/replaybox/app/ui/LayoutClassTest.kt`

**Interfaces:**
- Produces:
  - `enum class LayoutClass { TV, TABLET, COMPACT; companion object { const val COMPACT_MAX_HEIGHT_DP = 480; fun of(mobile: Boolean, heightDp: Int): LayoutClass } }`
  - `data class Insets(val horizontal: Dp, val vertical: Dp)`, `val TvInsets = Insets(48.dp, 27.dp)`, `val MobileInsets = Insets(24.dp, 16.dp)`, `val LocalInsets = staticCompositionLocalOf { TvInsets }`
  - `Rb.safeHorizontal` / `Rb.safeVertical` become `@Composable @ReadOnlyComposable get() = LocalInsets.current.horizontal|vertical` (call sites unchanged where they are inside composables; fix any the compiler flags by reading the value inside the composable).
- [ ] **Step 1: Failing tests** `LayoutClassTest`: `tvIsAlwaysTv` (`of(false, 300) == TV`, `of(false, 1000) == TV`); `mobileSplitsAt480` (`of(true, 479) == COMPACT`, `of(true, 480) == TABLET`, `of(true, 411) == COMPACT`, `of(true, 617) == TABLET`).
- [ ] **Step 2: Run** — FAIL. **Step 3:** implement `LayoutClass`, insets, getters; `MainActivity` provides `LocalInsets provides if (mobile) MobileInsets else TvInsets`. **Step 4:** tests + assembleDebug green.
- [ ] **Step 5: Commit** `feat: layout classes and mobile insets without overscan` (include this plan).

### Task 2: Compact canvas

**Files:**
- Modify: `DeviceKind.kt` (pure function), `MainActivity.kt`, `SearchField.kt`
- Test: `LayoutClassTest.kt`

**Interfaces:**
- Produces: `object CompactCanvas { const val HEIGHT_DP = 540; const val TEXT_FACTOR = 0.9f; fun scale(heightDp: Int): Float /* min(1, heightDp / 540) */; fun fontScale(systemFontScale: Float, heightDp: Int): Float /* system × TEXT_FACTOR / scale */ }`
- [ ] **Step 1: Failing tests**: `compactScaleFitsTheTvCanvas` (`scale(411) == 411f/540` within 1e-4; `scale(600) == 1f`); `compactTextIsNinetyPercentOfTv` (`fontScale(1f, 411) * scale(411) == 0.9f` within 1e-4; `fontScale(1.3f, 411) * scale(411) == 1.17f` within 1e-4 — the user's own font size still applies).
- [ ] **Step 2: Run** — FAIL. **Step 3:** implement; in `MainActivity`, read `LocalConfiguration.current.screenHeightDp` and `LocalDensity.current`; when `LayoutClass.of(mobile, h) == COMPACT`, wrap the screen content (not the window-level effects) in `CompositionLocalProvider(LocalDensity provides Density(d.density * s, d.fontScale * f/…))` per the interface. `SearchField`: `setTextSize(TypedValue.COMPLEX_UNIT_PX, with(LocalDensity.current) { RbType.displayCompact.fontSize.toPx() })` read in the composable and applied in `factory` + `update`.
- [ ] **Step 4:** tests + assembleDebug green.
- [ ] **Step 5: Pixel check** (Review Focus 2, 3): Home (with and without billboard: fresh install, then after a short France TV playback), Programme page, Search with keyboard open, Genres, Paramètres, player controls, Options, Reprendre/Débuter prompt, long-press dialog — screenshots, nothing clipped that cannot scroll.
- [ ] **Step 6: Tablet emulation** (Review Focus 5): `wm density 280`, relaunch, Home/Search/player screenshots (TV layout, 24/16 margins, unscaled); `wm density reset`.
- [ ] **Step 7: Split screen** (Review Focus 4): enter/leave split screen on Home; class follows, no restart.
- [ ] **Step 8: Commit** `feat: fit the TV layout on landscape phones`.

### Task 3: Box regression, ROADMAP, PR

- [ ] **Step 1: Box A/B** (Review Focus 1): main APK vs branch APK, the PR i key script plus a player pass (controls shown paused); all captures except live video pixel-identical; clean any Progression.
- [ ] **Step 2:** ROADMAP.md: tick (iii).
- [ ] **Step 3:** commit, push, PR; final independent review; fix what it confirms; merge when green and both devices checked.
