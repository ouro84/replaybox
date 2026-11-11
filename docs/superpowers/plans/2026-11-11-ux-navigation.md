# UX navigation (1.0.1, PR A) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On mobile, the navigation rail shows on every screen but the player, with touch targets of at least 48 real dp and a back arrow in its top slot (plus a back arrow in the player controls); on box and mobile, Back from the top of Home asks « Quitter Replaybox ? » before leaving.

**Architecture:** Three pure objects decide everything and are unit-tested: `RailNavigation` (which destination a stack highlights, whether the arrow shows, whether a rail tap keeps the destination's state), `RailMetrics` (rail item, icon, width, items offset and content start per device, TV values = today's), `ExitConfirm` (whether Back asks before leaving). `RailMetrics` is provided as `LocalRail` next to the compact density; `Rb.contentStart` reads it, so every screen that already starts at `Rb.contentStart` clears the bigger rail with no per-screen change. `MainActivity` draws one `MobileRail` over every non-Home, non-player screen; the arrows dispatch through `OnBackPressedDispatcher` (rail) or pop the player directly (player overlay).

**Tech Stack:** Kotlin, Compose 1.4 (BOM 2023.03.00), `androidx.tv:tv-foundation:1.0.0-alpha07`, activity-compose 1.7.2, material-icons-core (`Icons.Filled.ArrowBack`), JUnit4 + Truth.

**Spec:** `docs/superpowers/specs/2026-11-11-ux-optimizations-design.md`, section « PR A — Navigation » (A1, A2, A3) and « Delivery » (the `ROADMAP.md` section added by PR A).

## Global Constraints

- Every UI change goes through the `impeccable:impeccable` skill and stays within `DESIGN.md` and `PRODUCT.md`; tokens (`Rb.*`, `RbType.*`) only, never ad-hoc values.
- Mobile-only behaviour is gated on `LocalMobile`. The box (`LocalMobile` false) stays pixel-identical to `main` except the exit dialog (A/B screencap diff, Task 6).
- Decisions live in pure, unit-tested objects; Compose only wires them. Zero network in tests. No Compose UI tests, no ExoPlayer in tests.
- Glossary terms as is (Programme, Épisode, Genre, Flux). UI strings French in `app/src/main/res/values/strings.xml`; code, comments, tests, commits English.
- minSdk 26, no dependency bump.
- Gates: `./gradlew testDebugUnitTest` and `./gradlew assembleDebug`, after `export JAVA_HOME="/c/Program Files/Android/Android Studio/jre"` (JDK 11; not set on this PC).
- Branch `feat/ux-navigation` from `main`. Conventional commits, each ending with the trailer line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (use a HEREDOC for `git commit -m`).
- Devices: Bbox `192.168.1.182:5555` over adb Wi-Fi (adb at `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`; DHCP may move it; strip the vendor bytes before `\x89PNG` in `exec-out screencap -p`). Before **every** scripted key on the box, `dumpsys window | grep mCurrentFocus` must contain `com.replaybox`, else stop; screencap before every OK. Pixel 7 `2B151FDH2002RZ` (USB, landscape ≈ 914×411 dp, compact class, `CompactCanvas.scale` = 411/540 ≈ 0.761); `pm clear com.replaybox` allowed there, never on the box. Tablet class: an emulator only, never `wm density` or any display override on the Pixel.
- `PRD.md` and ADRs untouched. `ROADMAP.md`: only the new section and its boxes (Arnaud's authorisation).

## Decisions taken in this plan (spec ambiguities)

- **Rail tap that returns to the highlighted destination's root** (e.g. Recherche from a Programme opened from Search): the root's state is **kept** (query, results, the Genres tile), as if Back were pressed down to it. A tap on any other destination stays a fresh visit (today's `navigate`). Rationale: the spec says « returns to that destination's root »; resetting would wipe the query the user just came from. Pinned by `RailNavigation.keepsState`.
- **« Home at top »** for A3 is exactly the negation of Home's existing `BackHandler(enabled = !atTop)` condition, hoisted into `HomeUiState.atTop`. On mobile, focus is rarely set, so Back from a touch-scrolled Home shows the dialog (as the spec's definition implies).
- **The player keeps the TV content start** (`RailMetrics.Tv.contentStart`) for its error panel and seek flash: it has no rail, its layout stays as in 1.0.0.
- **Tablet class** meets 48 real dp with the TV item (scale 1); impeccable may raise the mobile floor, the tests only pin the invariants.

## Review Focus

1. Box: Home rail (collapsed and expanded), Programme page, Search, Genres, « Tout voir », Paramètres and its sub-screens, the player controls are pixel-identical to `main`; the risk is `Rb.contentStart`/`Rb.railCollapsed` turning into `LocalRail` reads, or the outer `Box` around the screens, changing a value or a focus stop. → `RailMetricsTest.boxKeepsTodaysRail` (Task 2) + A/B diff (Task 6).
2. Exit dialog: a second Back while the dialog is open cancels it and never finishes the app; OK on the default focus (« Annuler ») keeps the app; Back from a scrolled Home still returns to its top first. → `ExitConfirmTest` (Task 5) + box script (Task 6).
3. Programme opened from Search → tap Recherche on the rail: back on Search with the query and results kept, IME not reopened (`opensKeyboardOnEntry` is false with a query). → `RailNavigationTest.returningKeepsTheDestinationState` (Task 1) + Pixel check (Task 3).
4. Player arrow while paused: one tap leaves to the previous screen (no first tap that only hides the controls), the Progression is saved (`saveProgress` on dispose), and the tap does not also reach the video tap detector. → Pixel check (Task 4).
5. Compact phone smaller than the Pixel (window height 320–479 dp): every rail item stays ≥ 48 real dp and the five rail slots fit the 540 dp canvas; content never sits under the wider rail on Show detail, grids and sub-screens. → `RailMetricsTest.compactItemIsAtLeast48RealDp`, `contentClearsTheRail`, `railFitsTheCanvas` (Task 2) + Pixel screenshots (Task 3).

---

### Task 1: Rail decisions per back stack

**Files:**
- Modify: `app/src/main/java/com/replaybox/app/ui/NavRail.kt` (`RailNavigation`, ~l.55-70)
- Modify: `app/src/main/java/com/replaybox/app/ui/MainActivity.kt` (`navigate`, ~l.86-97)
- Test: `app/src/test/java/com/replaybox/app/ui/RailNavigationTest.kt`

**Interfaces:**
- Produces (all in `object RailNavigation`, `stackFor`/`next` unchanged):
  - `fun destinationFor(stack: List<Screen>): RailDestination?` — null when `stack.last()` is `Screen.Player`; else the destination of the first screen in the stack that maps to one (`Genres`, `GenreGrid` → GENRES; `Search` → SEARCH; `Settings`, `SubtitleSettings`, `ProviderDetail`, `About` → SETTINGS; `Home`, `ShowDetail` map to none), defaulting to HOME.
  - `fun showsBack(stack: List<Screen>): Boolean` — `destinationFor(stack) != null && stack != listOf(Screen.Home)`.
  - `fun keepsState(stack: List<Screen>, destination: RailDestination): Boolean` — true when `stackFor(destination)` is a strict prefix of `stack`.

- [ ] **Step 1: Write the failing tests** in `RailNavigationTest` (fixtures: `val show = Screen.ShowDetail(ShowId("s"), "S")`, `val grid = Screen.GenreGrid(Genre.SERIES)`, `val player = Screen.Player(EpisodeId("e"), "E")`):
  - `destinationFollowsTheRootMostRailScreen`: `[Home]` → HOME; `[Home, show]` → HOME; `[Home, Genres]`, `[Home, Genres, grid]`, `[Home, grid]`, `[Home, Genres, grid, show]` → GENRES; `[Home, Search, show]` → SEARCH; `[Home, Settings, SubtitleSettings]`, `[Home, Settings, ProviderDetail("tf1")]`, `[Home, Settings, About]` → SETTINGS.
  - `noRailInThePlayer`: `[Home, show, player]` and `[Home, Search, show, player]` → null.
  - `backArrowEverywhereButHomeRoot`: `showsBack([Home])` false; `[Home, Search]`, `[Home, show]`, `[Home, Settings, About]` true; `[Home, show, player]` false.
  - `highlightedDestinationFromASubScreenReturnsToItsRoot`: `next([Home, Search, show], SEARCH) == [Home, Search]`; `next([Home, show], HOME) == [Home]`; `next([Home, Settings, About], SETTINGS) == [Home, Settings]`; `next([Home, grid], GENRES) == [Home, Genres]`.
  - `returningKeepsTheDestinationState`: `keepsState([Home, Search, show], SEARCH)` true; `keepsState([Home, Genres, grid], GENRES)` true; `keepsState([Home, grid], GENRES)` false; `keepsState([Home, Search], GENRES)` false; `keepsState([Home, Search], SEARCH)` false.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*RailNavigationTest'` — FAIL (unresolved `destinationFor`, `showsBack`, `keepsState`).
- [ ] **Step 3: Implement** the three functions as in Interfaces.
- [ ] **Step 4: Wire `navigate`** — compute `next` as today; reset `genresState`/`searchState` only when `!RailNavigation.keepsState(backStack.toList(), destination)`. Update the comment (« a fresh visit, or back to the destination's root with its state »).
- [ ] **Step 5: Run** tests — PASS; `assembleDebug` — BUILD SUCCESSFUL.
- [ ] **Step 6: Commit** `feat: rail destination and back arrow decided from the back stack`. Include the spec `docs/superpowers/specs/2026-11-11-ux-optimizations-design.md` and this plan file (the B and C plans travel with their own PRs).

### Task 2: Rail metrics token (48 real dp on mobile)

**Files:**
- Modify: `app/src/main/java/com/replaybox/app/ui/Theme.kt` (`Rb.railCollapsed`, `Rb.contentStart` ~l.58-60; new `RailMetrics`, `LocalRail` next to `Insets`/`LocalInsets`)
- Modify: `app/src/main/java/com/replaybox/app/ui/MainActivity.kt` (`CompactDensity`, ~l.215)
- Modify: `app/src/main/java/com/replaybox/app/ui/NavRail.kt` (`NavRail` padding/width, `RailItem` height/icon)
- Modify: `app/src/main/java/com/replaybox/app/ui/PlayerScreen.kt` (`Rb.contentStart` at ~l.171 and ~l.818)
- Test: `app/src/test/java/com/replaybox/app/ui/RailMetricsTest.kt` (create)

**Interfaces:**
- Consumes: `LayoutClass.of`, `CompactCanvas.scale` (`DeviceKind.kt`), `GUTTER` (`Components.kt`, 20 dp).
- Produces (in `Theme.kt`):
  - `data class RailMetrics(val item: Dp, val icon: Dp, val width: Dp, val itemsTop: Dp, val contentStart: Dp)` — `item` = pill height (and the back-arrow slot), `width` = collapsed rail width (item width is `width − 2 × 12 dp` inset), `itemsTop` = offset of the first destination below `Rb.safeVertical`.
  - `companion object { const val MIN_TOUCH_DP = 48f; val Tv: RailMetrics; fun mobile(scale: Float): RailMetrics; fun of(mobile: Boolean, heightDp: Int): RailMetrics }` — `Tv = RailMetrics(item = 48.dp, icon = 24.dp, width = 72.dp, itemsTop = 72.dp, contentStart = 104.dp)` (today's values, DESIGN.md « Navigation »); `of` returns `Tv` when `!mobile`, `mobile(CompactCanvas.scale(heightDp))` on COMPACT, `mobile(1f)` on TABLET.
  - `val LocalRail = staticCompositionLocalOf { RailMetrics.Tv }`.
  - `Rb.railCollapsed` and `Rb.contentStart` become `@Composable @ReadOnlyComposable get()` reading `LocalRail.current.width` / `.contentStart`.

- [ ] **Step 1: Load the `impeccable:impeccable` skill and decide the mobile rail sizes against `DESIGN.md`** (« Navigation (nav rail) », `rail-collapsed`, `content-start`, `rail-stack`, 48 dp buttons) and `PRODUCT.md`. The arithmetic to start from:
  - Canvas dp needed for 48 real dp = `48 / scale`. Pixel 7: `48 / 0.761 ≈ 63.1` → at least 64 canvas dp. Smallest compact window the class admits (height 320 dp): `48 / 0.593 ≈ 81`. Tablet: scale 1 → 48 (TV item already enough).
  - So `mobile(scale).item = max(Tv.item, ceil(48 / scale) rounded up to the grid step impeccable picks)`; the width must keep the 12 dp inset on both sides: `width ≥ item + 24 dp` (Pixel, item 64: ≥ 88).
  - Home clips its content column at `contentStart − GUTTER`, which must clear the rail: `contentStart ≥ width + 20 dp` (TV keeps a 32 dp gap: 104 = 72 + 32).
  - The arrow slot sits above Accueil: `itemsTop ≥ item + 8 dp` (`rail-stack`), and five slots must fit the 540 dp canvas: `safeVertical (16) + itemsTop + 4 × item + 3 × 8 ≤ 540 − safeVertical`.
  - Icon size: TV is 24 in a 48 pill; impeccable decides the mobile ratio.
  Record the decided formula (grid step, icon ratio, gap) in the KDoc of `RailMetrics.mobile`; values live only there (mobile-only token, the box reads `Tv`).
- [ ] **Step 2: Write the failing tests** — `RailMetricsTest`:
  - `boxKeepsTodaysRail`: `RailMetrics.Tv == RailMetrics(48.dp, 24.dp, 72.dp, 72.dp, 104.dp)`; `of(false, 540)`, `of(false, 411)`, `of(false, 0)` all `== Tv`.
  - `compactItemIsAtLeast48RealDp`: for `h` in `320, 360, 411, 479`: `val m = of(true, h); val s = CompactCanvas.scale(h)`; `m.item.value * s >= 48f`; `(m.width - 24.dp).value * s >= 48f`.
  - `tabletItemIsAtLeast48Dp`: `of(true, 800).item >= 48.dp` and `of(true, 800).width - 24.dp >= 48.dp`.
  - `contentClearsTheRail`: for `Tv`, `of(true, 411)`, `of(true, 320)`, `of(true, 800)`: `contentStart - GUTTER >= width`.
  - `backSlotFitsAboveTheItems`: for the mobile cases: `itemsTop >= item + 8.dp`.
  - `railFitsTheCanvas`: for the compact cases: `16 + itemsTop + 4 × item + 3 × 8 <= 540 − 16` (in dp values).
  - `pixel7Metrics`: `of(true, 411)` equals the values decided in Step 1 (pins the decision).
- [ ] **Step 3: Run** `--tests '*RailMetricsTest'` — FAIL (unresolved `RailMetrics`).
- [ ] **Step 4: Implement** `RailMetrics`, `LocalRail`, the two `Rb` getters. Fix any non-composable `Rb.contentStart` read the compiler reports by moving it into the composable that uses it.
- [ ] **Step 5: Wire** — `CompactDensity` also provides `LocalRail provides RailMetrics.of(mobile, heightDp)` (same single `CompositionLocalProvider`, no branch). `NavRail`: `width(if (expanded) Rb.railExpanded else Rb.railCollapsed)` unchanged in form, top padding `Rb.safeVertical + LocalRail.current.itemsTop`; `RailItem`: `height(LocalRail.current.item)`, icon box and `Icon` `size(LocalRail.current.icon)`. `PlayerScreen` error panel and `SeekFlash` use `RailMetrics.Tv.contentStart`.
- [ ] **Step 6: Run** tests + `assembleDebug` — green.
- [ ] **Step 7: Pixel check** — Home, Recherche, Genres, Paramètres: bigger rail items (screencap, measure an item's px height ≥ 48 × 2.625 = 126 px), titles and cards start right of the rail, nothing clipped under it.
- [ ] **Step 8: Commit** `feat: rail items reach 48 real dp on mobile`.

### Task 3: Rail on every non-player screen, with the back arrow

**Files:**
- Modify: `app/src/main/java/com/replaybox/app/ui/NavRail.kt` (`MobileRail`, `NavRail`)
- Modify: `app/src/main/java/com/replaybox/app/ui/MainActivity.kt` (the `when (current)` block, ~l.101-185)

**Interfaces:**
- Consumes: `RailNavigation.destinationFor`, `RailNavigation.showsBack` (Task 1); `LocalRail` (Task 2).
- Produces:
  - `fun NavRail(current: RailDestination, onNavigate: (RailDestination) -> Unit, modifier: Modifier = Modifier, onExpandedChange: (Boolean) -> Unit = {}, showBack: Boolean = false)` — when `showBack`, a back item (same pill size `LocalRail.current.item`, `Icons.Filled.ArrowBack`, `contentDescription = stringResource(R.string.back)` « Retour », never selected) sits in the slot above Accueil; tapping it calls `LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher?.onBackPressed()`. The destinations' position does not depend on `showBack`.
  - `@Composable fun BoxScope.MobileRail(stack: List<Screen>, onNavigate: (RailDestination) -> Unit)` — replaces the `current: RailDestination` signature: on mobile only, `destinationFor(stack)?.let { NavRail(it, onNavigate, Modifier.align(Alignment.CenterStart), showBack = RailNavigation.showsBack(stack)) }`.

- [ ] **Step 1: Load the `impeccable:impeccable` skill and decide the back item's visual against `DESIGN.md`** (nav rail item states: rest = Mist icon on the ground, no pill; pressed state on touch; whether a hairline or gap separates it from Accueil). Its size is `LocalRail.current.item`, not a new value.
- [ ] **Step 2: Implement** `NavRail(showBack)` and the new `MobileRail` as in Interfaces. The back item is never the rail's focus entry (`focusProperties { enter }` keeps the current destination).
- [ ] **Step 3: Wire `MainActivity`** — wrap the `when (current)` in one `Box(Modifier.fillMaxSize())`; drop the three per-screen `Box { …; MobileRail(...) }` wrappers (Settings, Search, Genres render bare); after the `when`, `if (current !is Screen.Home) MobileRail(backStack.toList(), ::navigate)`. Home keeps its own `NavRail` (box expand + dim); on mobile it gets no arrow (stack is `[Home]`) and its items sit at the same height as everywhere else.
- [ ] **Step 4: Run** tests + `assembleDebug` — green.
- [ ] **Step 5: Pixel check** — rail with arrow on Programme (from Home: Accueil highlighted; from Search: Recherche; from a Genre grid: Genres), « Tout voir », Sous-titres, Provider, À propos; Home: no arrow, items at the same y as on Search (screencap compare). Arrow = system Back: on Search with a query, arrow → Home. Review Focus 3: Search « nar » → Programme → tap Recherche → query and results kept, IME closed. Rail tap Accueil from a Programme → Home. Player: no rail. Talkback not required; `uiautomator dump` shows `content-desc="Retour"` on the arrow.
- [ ] **Step 6: Commit** `feat: rail with a back arrow on every screen but the player on mobile`.

### Task 4: Back arrow in the player controls

**Files:**
- Modify: `app/src/main/java/com/replaybox/app/ui/PlayerScreen.kt` (`PlayerScreen` params ~l.96, `PlayerSurface` params ~l.248, controls branch ~l.787)
- Modify: `app/src/main/java/com/replaybox/app/ui/MainActivity.kt` (`Screen.Player` call)

**Interfaces:**
- Consumes: `LocalRail` (Task 2), `R.string.back`.
- Produces: `PlayerScreen(..., onBack: () -> Unit = {})` passed down to `PlayerSurface(onBack)`; `MainActivity` passes `{ if (backStack.size > 1 && backStack.last() == current) backStack.removeAt(backStack.lastIndex) }` (same guard as `onFinished`).

- [ ] **Step 1: Load the `impeccable:impeccable` skill and decide the player arrow's visual against `DESIGN.md`** (« Player » overlay: top-left inside `Rb.safeHorizontal`/`Rb.safeVertical`; backing over the video — the seek flash uses a ground 60 % pill — icon tint, and that it fades with the controls in `Rb.FOCUS_MS`). Size: `LocalRail.current.item` (the enlarged rail item).
- [ ] **Step 2: Implement** — in the `else if (controlsVisible)` branch, when `mobile`, draw the arrow `Modifier.align(Alignment.TopStart)` next to `PlayerControls`; `contentDescription` « Retour »; `onClick = onBack` (one tap leaves, it does not hide the controls first). Not composed on the box, nor while Options, the resume prompt or Épisode suivant show (they replace the controls branch).
- [ ] **Step 3: Run** tests + `assembleDebug` — green.
- [ ] **Step 4: Pixel check** (Review Focus 4) — play a France TV Épisode, tap to show controls → arrow top-left; wait 5 s → it hides with the controls; pause → arrow stays; tap it → back on the Programme in one tap, the Épisode shows its Progression. Arrow never shows over Options or « Reprendre / Débuter ». `pm clear com.replaybox` after.
- [ ] **Step 5: Commit** `feat: back arrow in the player controls on mobile`.

### Task 5: Exit confirmation (box + mobile)

**Files:**
- Create: `app/src/main/java/com/replaybox/app/ui/ExitConfirm.kt`
- Modify: `app/src/main/java/com/replaybox/app/ui/HomeScreen.kt` (`HomeUiState` ~l.104; `atTop` ~l.287)
- Modify: `app/src/main/java/com/replaybox/app/ui/MainActivity.kt` (BackHandlers ~l.99, dialog)
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/replaybox/app/ui/ExitConfirmTest.kt` (create)

**Interfaces:**
- Consumes: `ActionDialog(title, hint, actions, onDismiss)`, `DialogAction(label, onClick)` (`ActionDialog.kt`: « Annuler » first and focused).
- Produces:
  - `object ExitConfirm { fun shows(stack: List<Screen>, homeAtTop: Boolean): Boolean }` — `stack == listOf(Screen.Home) && homeAtTop`.
  - `HomeUiState.atTop: Boolean` (snapshot state, default true), written by `HomeScreen` from its existing `atTop` (`SideEffect { state.atTop = atTop }`).

- [ ] **Step 1: Write the failing tests** — `ExitConfirmTest`:
  - `asksOnlyFromTheTopOfHome`: `shows([Home], true)` true; `shows([Home], false)` false (Home scrolls back to top first).
  - `neverAsksAwayFromHome`: `shows([Home, Search], true)`, `shows([Home, ShowDetail(..)], true)`, `shows([Home, ShowDetail(..), Player(..)], true)` false.
- [ ] **Step 2: Run** `--tests '*ExitConfirmTest'` — FAIL.
- [ ] **Step 3: Implement** `ExitConfirm` and `HomeUiState.atTop`.
- [ ] **Step 4: Strings** — `exit_title` « Quitter Replaybox ? », `exit_confirm` « Quitter » (« Annuler » is the existing `cancel`).
- [ ] **Step 5: Load the `impeccable:impeccable` skill and check the dialog against `DESIGN.md` « Options dialogs »** (440 dp slate panel, Rail Title, no hint, « Annuler » then « Quitter » in one row, initial focus « Annuler »). Expected outcome: `ActionDialog` as is, no new visual; note any deviation impeccable asks for before coding it.
- [ ] **Step 6: Wire `MainActivity`** — `var exitAsked by remember { mutableStateOf(false) }`; `BackHandler(enabled = ExitConfirm.shows(backStack.toList(), homeState.atTop)) { exitAsked = true }`; inside `CompactDensity` (so the dialog takes the compact density) `if (exitAsked) ActionDialog(title = stringResource(R.string.exit_title), hint = null, actions = listOf(DialogAction(stringResource(R.string.exit_confirm)) { finish() }), onDismiss = { exitAsked = false })`. Back while open is the Dialog's `onDismissRequest` → cancel. Home's own `BackHandler(!atTop)` is unchanged; the two are mutually exclusive.
- [ ] **Step 7: Run** tests + `assembleDebug` — green.
- [ ] **Step 8: Pixel check** — Home top, system Back (edge swipe) → dialog; Back → closes, app stays; arrow-less Home rail unchanged; Back → « Quitter » → app finishes. From Search: Back → Home, no dialog.
- [ ] **Step 9: Commit** `feat: confirm before leaving Replaybox with Back`.

### Task 6: ROADMAP section, device verification, PR

**Files:**
- Modify: `ROADMAP.md` (new section before « ## V2 »)

- [ ] **Step 1: ROADMAP.md** — add, before « ## V2 (décisions prises, exécution repoussée) »:
  ```markdown
  ## Optimisations UX (1.0.1)

  Issue #69 (2026-11-10), grilling du 2026-11-11. Des optimisations, pas de nouvelle fonction : livrées
  en 1.0.1. Une case se coche quand l'item est vérifié sur la box (et sur le Pixel pour le mobile).

  - [ ] Mobile : menu de gauche visible sur tous les écrans sauf le player, icônes agrandies (48 dp réels).
  - [ ] Mobile : flèche retour en haut du menu, et dans les contrôles du player.
  - [ ] Box et mobile : confirmation « Quitter Replaybox ? » avant de quitter avec Retour.
  - [ ] Mobile : un tap met en pause ; icône pause/lecture au centre (box aussi) ; bouton Pause/Lecture retiré de la barre (box aussi).
  - [ ] Mobile : barre de lecture glissable.
  - [ ] Box et mobile : les sous-titres suivent le dernier choix (activés ou non, et leur type).
  - [ ] Box et mobile : une image de Programme sur chaque tuile Genre.
  ```
- [ ] **Step 2: Gates** — `./gradlew testDebugUnitTest` and `./gradlew assembleDebug` green.
- [ ] **Step 3: Box A/B regression** (Review Focus 1) — build the `main` APK and the branch APK; for each: install, cold start, same D-pad script (focus check before every key): Home (first card), LEFT → expanded rail, RIGHT, Programme page (focus on Lecture), Back, Search (key A, type « nar », RIGHT into results), Genres, a « Tout voir » grid, Paramètres → Sous-titres, Provider, À propos, an Épisode with controls shown (then Back out of the player, no OK on a Programme page without a screencap). PIL `ImageChops.difference(...).getbbox()` must be `None` for every pair. Clean any Progression (MENU → « Marquer comme vu » → « Marquer comme non vu »).
- [ ] **Step 4: Box exit dialog** (Review Focus 2) — Home with focus on a lower rail: Back → Home back at top, no dialog; Back → dialog, screencap: focus on « Annuler »; Back → dialog gone, app in front; Back → dialog, OK → still in app (Annuler); Back → dialog, RIGHT, screencap (focus on « Quitter »), OK → app finished: **stop sending keys**, relaunch with `adb shell am start -n com.replaybox/com.replaybox.app.ui.MainActivity` and check focus again.
- [ ] **Step 5: Pixel full pass** — Tasks 2–5 checks in one session (rail on every non-player screen, ≥ 48 dp items, both arrows, exit dialog), screenshots kept for the PR, then `pm clear com.replaybox`.
- [ ] **Step 6: Tablet class** — on an Android emulator (tablet AVD, landscape, height ≥ 480 dp): rail on a Programme page, arrow, items ≥ 48 dp. No AVD or system image is installed on this PC (2026-11-11): ask Arnaud before downloading one; if he declines, write « tablette : couverte par `RailMetricsTest.tabletItemIsAtLeast48Dp`, non vérifiée sur émulateur » in the PR.
- [ ] **Step 7: Tick** the first three ROADMAP items only if Steps 3–5 passed. Commit `docs: add the 1.0.1 UX roadmap section and tick the navigation items`.
- [ ] **Step 8: Push and open the PR** on `main` — `git push -u origin feat/ux-navigation`; `gh pr create --base main` with a French body (A1/A2/A3 summary, the decisions above, box A/B result, Pixel screenshots, tablet status) ending with `🤖 Generated with [Claude Code](https://claude.com/claude-code)`. Merge (Arnaud granted M3-style autonomy, 2026-11-11): once the final review is clean, gates green and the box + Pixel checks passed, `gh pr merge --merge --delete-branch`, then `git checkout main && git pull`.

---

## Self-review

- Spec coverage: A1 rail everywhere but the player (Tasks 1, 3), destination per screen (Task 1), tap behaviour and no-op (Task 1, existing `next`), collapsed icons only (unchanged `NavRail`), ≥ 48 real dp with impeccable sizing (Task 2), content not under the rail (Task 2 `contentClearsTheRail`, Task 3 check), box rail 72 dp (Task 2 `boxKeepsTodaysRail`); A2 rail arrow slot, Home root empty slot, dispatcher (Task 3), player arrow visible with the controls, one tap (Task 4), « Retour » (Tasks 3, 4), box no arrow (mobile gates); A3 condition, `ActionDialog`, strings, focus, Back cancels, finish, decision object (Task 5), Home key untouched (no code); ROADMAP section (Task 6).
- Types: `RailNavigation.destinationFor/showsBack/keepsState`, `RailMetrics.Tv/mobile/of`, `LocalRail`, `MobileRail(stack, onNavigate)`, `NavRail(..., showBack)`, `PlayerScreen(onBack)`, `ExitConfirm.shows`, `HomeUiState.atTop` are used with the same names in every task.
