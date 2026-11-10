# Mobile touch input and navigation — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the single Replaybox APK usable by touch on phones and tablets (scroll, tap, long-press, navigation rail, native keyboard Search, immersive landscape, rotate prompt) without changing anything on the Bbox, and record in PRD.md / ROADMAP.md that mobile/tablet is now in scope.

**Architecture:** One flag decides everything: `DeviceKind.isMobile(uiModeType)` is true when `UiModeManager.currentModeType != UI_MODE_TYPE_TELEVISION`. `MainActivity` computes it once and provides it as `LocalMobile`. Every mobile behaviour is gated on `LocalMobile.current`; on the box the flag is false and every code path is the current one. Tap needs no work: Compose's `clickable` is not focusable in touch mode, so a tap runs `onClick` without focus visuals (observed on the Pixel, 2026-11-10 état des lieux). What breaks today is scroll (`TvLazy*` default `userScrollEnabled = false`), plain `Column` screens taller than 411 dp, navigation that only exists on Home, the focus-driven Home panel, and the D-pad keyboard.

**Tech Stack:** Kotlin, Compose 1.4 (BOM 2023.03.00), `androidx.tv:tv-foundation:1.0.0-alpha07`, activity-compose 1.7.2, core-ktx 1.10.1, JUnit4 + Truth.

**Spec:** brief of 2026-11-10 (mobile milestone, PR i) + grilling 2026-11-10 (milestone + keyboard rounds), recorded in the auto-memory `mobile-milestone-decisions`; état des lieux on the Pixel 7 (2026-11-10).

## Global Constraints

- Nothing changes on the box: every new behaviour is behind `LocalMobile.current` (false on the box). Box screens are compared before/after (Task 8).
- minSdk stays 26. No dependency bump (Media3, Compose BOM, tv-foundation stay as they are).
- Single APK, no flavor. Mobile = `UiModeManager.currentModeType != Configuration.UI_MODE_TYPE_TELEVISION`.
- Code, comments, tests, commits in English; UI strings in French in `values/strings.xml`, glossary terms (Programme, Épisode, Chaîne…).
- Tests on pure logic only (no Compose UI tests, no ExoPlayer).
- Build `./gradlew assembleDebug`, tests `./gradlew testDebugUnitTest`. `JAVA_HOME` is not set on this PC: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jre"` (JDK 11) before Gradle.
- Branch `feat/mobile-touch-nav` from `main`; conventional commits; one PR; no release (1.0.0 comes after PR iii).
- Device checks: Pixel 7 `2B151FDH2002RZ` (USB), Bbox `192.168.1.182:5555` (`box-adb-access`, `box-scripted-keys-safety`: Replaybox in the foreground before every key, screencap before every OK). Undo side effects (Progression, épingles); on the Pixel `pm clear com.replaybox` is allowed (no data of Arnaud's there).

## Decisions

| Topic | Mobile behaviour | Box |
|---|---|---|
| Tap | `onClick` at once, no focus ring/lift (Compose touch mode) | unchanged |
| Long-press | same as MENU: options of an « À suivre » card (Home) and of an Épisode row (Programme page) | unchanged (no `onLongClick`) |
| Scroll | every `TvLazyColumn/Row/VerticalGrid` gets `userScrollEnabled = true`; Genres, Paramètres, Sous-titres, À propos get `verticalScroll` | `userScrollEnabled = false`, no `verticalScroll` |
| Rail | collapsed NavRail on Home, Recherche, Genres, Paramètres; never expands (touch never focuses it); tapping a destination resets the stack to `[Home, destination]` (`[Home]` for Accueil) | Home only, as today |
| Home top panel | shows the billboard (first « À suivre », else first épingle) only; hidden when there is none, rails start at the top | follows focus, as today |
| Search | native IME (see Task 7) | D-pad keyboard, as today |
| Window | immersive (status + navigation bars hidden, swipe shows them transiently); display cutout left in default mode (letterboxed band on the camera side, nothing drawn under the hole) | unchanged |
| Orientation | manifest `sensorLandscape`; if the window is taller than wide anyway (split screen, system override), a full-screen « Tourne l'appareil » prompt covers the app; the player pauses under it and stays paused with controls shown when landscape returns | `sensorLandscape` = landscape on a TV (no sensor) |

Keyboard decisions (grilling 2026-11-10): native IME on phone and tablet; field at the top in place of « Titre du Programme… », chips below, results full width; field focused and IME open on entry (fresh visit, empty query only); live results; IME « Rechercher » hides the IME; no fullscreen extract UI in landscape; Back closes the IME, then leaves Search; « × » clears the field when not empty; `MAX_LENGTH` (40) applies, a longer paste is cut; no suggestions/autocorrect; a physical keyboard types into the field.

## Review Focus

1. Box: a D-pad session (Home → Programme → Épisode, Search with the D-pad keyboard, Genres, Paramètres) behaves and looks exactly as on `main`; the risk is a modifier added outside the `LocalMobile` gate (a `clickable` on the best result, a `verticalScroll` on Genres) that adds a focus stop or scrolls. (Task 8, screencap comparison.)
2. Search: coming back from a Programme opened from a result does not reopen the IME and keeps the query and scroll; a fresh visit from the rail opens it. (Unit test `opensKeyboardOnEntry…`, Task 7; Pixel check.)
3. Rotation with the player running in split screen: the prompt shows, playback pauses; back to full screen landscape → paused, controls shown, no restart of the Épisode (no activity recreation: `configChanges` covers `smallestScreenSize|screenLayout`). (Pixel check, Task 2.)
4. Nested scroll on Home: a horizontal swipe on a rail scrolls that rail only, a vertical swipe scrolls the page, a tap at the end of a fling does not open a Programme by accident (Compose stops the fling on touch down; check). (Pixel check, Task 3.)
5. Paste of a 200-character text into the Search field → cut at 40, no crash; « × » then empties it and results go back to the empty state. (Unit test `setQueryCapsAtMaxLength`, Task 7.)

---

### Task 1: Device kind, immersive window, orientation

**Files:**
- Create: `app/src/main/java/com/replaybox/app/ui/DeviceKind.kt`
- Modify: `app/src/main/java/com/replaybox/app/ui/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml` (activity)
- Test: `app/src/test/java/com/replaybox/app/ui/DeviceKindTest.kt`

**Interfaces:**
- Produces:
  - `object DeviceKind { fun isMobile(uiModeType: Int): Boolean; fun isPortraitWindow(widthDp: Int, heightDp: Int): Boolean }`
  - `val LocalMobile: ProvidableCompositionLocal<Boolean>` (`staticCompositionLocalOf { false }`)
  - `@Composable fun Modifier.mobileVerticalScroll(): Modifier` — `verticalScroll(rememberScrollState())` when `LocalMobile.current`, else `this`.

- [ ] **Step 1: Write the failing tests** — `DeviceKindTest`:
  - `televisionIsNotMobile`: `isMobile(Configuration.UI_MODE_TYPE_TELEVISION)` is false.
  - `normalAndUndefinedAreMobile`: `UI_MODE_TYPE_NORMAL`, `UI_MODE_TYPE_UNDEFINED`, `UI_MODE_TYPE_DESK` → true.
  - `portraitOnlyWhenTallerThanWide`: `(411, 914)` true; `(914, 411)` false; `(600, 600)` false.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*DeviceKindTest'` — FAIL (unresolved `DeviceKind`).
- [ ] **Step 3: Implement `DeviceKind.kt`** as in Interfaces (`isPortraitWindow` = `heightDp > widthDp`).
- [ ] **Step 4: Wire `MainActivity`**:
  - `private val mobile by lazy { DeviceKind.isMobile(getSystemService(UiModeManager::class.java).currentModeType) }`.
  - Wrap the `setContent` body in `CompositionLocalProvider(LocalMobile provides mobile)`.
  - When `mobile`: in `onCreate` `WindowCompat.setDecorFitsSystemWindows(window, false)`; a `private fun hideSystemBars()` using `WindowInsetsControllerCompat(window, window.decorView)` with `systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` and `hide(WindowInsetsCompat.Type.systemBars())`; call it from `onResume()` and `onWindowFocusChanged(hasFocus = true)` (bars come back after the IME or a dialog). Never called on the box.
- [ ] **Step 5: Manifest** — activity `android:screenOrientation="sensorLandscape"`; `android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden"` (split screen must not recreate the activity: the back stack lives in `remember`).
- [ ] **Step 6: Run** tests + `./gradlew assembleDebug` — PASS / BUILD SUCCESSFUL.
- [ ] **Step 7: Pixel check** — install, launch: no status/navigation bar, swipe from the edge shows them transiently; flip the phone 180° → app follows (sensorLandscape).
- [ ] **Step 8: Commit** `feat: detect mobile devices and go immersive in landscape` (include this plan file).

### Task 2: « Tourne l'appareil » prompt and player pause

**Files:**
- Create: `app/src/main/java/com/replaybox/app/ui/RotatePrompt.kt`
- Modify: `app/src/main/java/com/replaybox/app/ui/MainActivity.kt`
- Modify: `app/src/main/java/com/replaybox/app/ui/PlayerScreen.kt` (`PlayerSurface`, near the `ON_PAUSE` observer ~l.448)
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `LocalMobile`, `DeviceKind.isPortraitWindow` (Task 1).
- Produces: `@Composable fun RotatePrompt()` (full-screen, opaque `Rb.ground`, consumes every pointer event); `@Composable fun portraitBlocked(): Boolean` in `RotatePrompt.kt` = `LocalMobile.current && DeviceKind.isPortraitWindow(LocalConfiguration.current.screenWidthDp, LocalConfiguration.current.screenHeightDp)`.

- [ ] **Step 1: Strings** — `rotate_title` « Tourne l'appareil », `rotate_body` « Replaybox s'utilise en paysage. ».
- [ ] **Step 2: `RotatePrompt`** — text only (the rotation icon is in the extended Material set, not a dependency): title `RbType.displayCompact` / `Rb.text`, body `RbType.body` / `Rb.textSecondary`, centered; `Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }` so nothing underneath gets taps.
- [ ] **Step 3: MainActivity** — inside the theme surface, a `Box` holding the current screen, then `if (portraitBlocked()) RotatePrompt()` on top. The screen under it stays composed (player state kept).
- [ ] **Step 4: Player** — in `PlayerSurface`: `val blocked = portraitBlocked()`; `LaunchedEffect(blocked) { if (blocked) player.pause() }`. Nothing on unblock: playback stays paused and the pause→controls listener (PR #63, `PauseReveal`) has already shown the controls.
- [ ] **Step 5: Run** tests + `assembleDebug` — green.
- [ ] **Step 6: Pixel check** — play a France TV Épisode, open split screen (recents → app icon → « Écran partagé ») so the window is taller than wide → prompt covers the app, sound stops. Leave split screen → player paused, controls shown, same position. Also check Home in split screen shows the prompt and no tap reaches Home.
- [ ] **Step 7: Commit** `feat: ask to rotate and pause playback in a portrait window`.

### Task 3: Touch scroll

**Files (all under `app/src/main/java/com/replaybox/app/ui/`):**
- Modify: `HomeScreen.kt` (`TvLazyColumn` ~l.320, `Rail`'s `TvLazyRow` ~l.683), `ShowScreen.kt` (`TvLazyColumn` ~l.279, `SeasonPills` ~l.520), `SearchScreen.kt` (`TvLazyVerticalGrid` ~l.271, chip `horizontalScroll` row is already scrollable), `GenreGridScreen.kt` (~l.121), `GenresScreen.kt` (outer `Column` ~l.55), `SettingsScreen.kt` (~l.74), `SubtitleSettingsScreen.kt` (~l.60), `AboutScreen.kt` (~l.54).

**Interfaces:**
- Consumes: `LocalMobile`, `Modifier.mobileVerticalScroll()` (Task 1).

- [ ] **Step 1:** every `TvLazyColumn`, `TvLazyRow`, `TvLazyVerticalGrid` above gets `userScrollEnabled = LocalMobile.current` (read once per composable into a `val mobile`).
- [ ] **Step 2:** the outer `Column` of Genres, Paramètres, Sous-titres and À propos gets `.mobileVerticalScroll()` placed before its padding; `ProviderScreen` already scrolls.
- [ ] **Step 3: Run** tests + `assembleDebug` — green.
- [ ] **Step 4: Pixel check** (Review Focus 4) — Home: vertical swipe reaches the last Genre rail, horizontal swipe reaches « Tout voir »; Programme page: swipe down to the Épisodes, Saison pills swipe sideways; Search results, « Tout voir » grid, Genres, Paramètres (last row « À propos » reachable) all scroll.
- [ ] **Step 5: Commit** `feat: scroll by touch on mobile`.

### Task 4: Long-press opens the options

**Files:**
- Modify: `app/src/main/java/com/replaybox/app/ui/Components.kt` (`tvClickable` ~l.187)
- Modify: `HomeScreen.kt` (`UpNextCard` ~l.703), `ShowScreen.kt` (`EpisodeRow` ~l.580)

**Interfaces:**
- Produces: `internal fun Modifier.tvClickable(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier` — `combinedClickable(interactionSource, indication = null, onClick, onLongClick)` when `onLongClick != null`, else today's `clickable` (one focus node either way).

- [ ] **Step 1:** extend `tvClickable` as in Interfaces (`@OptIn(ExperimentalFoundationApi::class)`).
- [ ] **Step 2:** `UpNextCard` and `EpisodeRow` pass `onLongClick = onOptions.takeIf { mobile }` (`val mobile = LocalMobile.current`); the box passes null, so its OK/MENU handling is the current one.
- [ ] **Step 3: Run** tests + `assembleDebug` — green.
- [ ] **Step 4: Pixel check** — long-press an Épisode row → « Marquer comme vu » dialog; tap outside/Back closes it, nothing marked. (« À suivre » card: check once Task 6 shows the rail after a test playback; clean with « Marquer comme vu » → « Marquer comme non vu » or `pm clear`.)
- [ ] **Step 5: Commit** `feat: long-press opens the options on mobile`.

### Task 5: Navigation rail on every top-level screen

**Files:**
- Modify: `app/src/main/java/com/replaybox/app/ui/NavRail.kt`
- Modify: `app/src/main/java/com/replaybox/app/ui/MainActivity.kt`
- Test: `app/src/test/java/com/replaybox/app/ui/RailNavigationTest.kt`

**Interfaces:**
- Produces:
  - `object RailNavigation { fun stackFor(destination: RailDestination): List<Screen> }` in `NavRail.kt`: `HOME → [Home]`, `GENRES → [Home, Genres]`, `SEARCH → [Home, Search]`, `SETTINGS → [Home, Settings]`.
  - `@Composable fun BoxScope.MobileRail(current: RailDestination, onNavigate: (RailDestination) -> Unit)` in `NavRail.kt`: `if (LocalMobile.current) NavRail(current, onNavigate, Modifier.align(Alignment.CenterStart))`, drawn over the screen (callers put it in a `Box` after the screen).

- [ ] **Step 1: Write the failing test** — `RailNavigationTest.everyDestinationSitsOnHome`: `stackFor(HOME) == listOf(Screen.Home)`; `stackFor(SEARCH) == listOf(Screen.Home, Screen.Search)`; same for `GENRES`, `SETTINGS`.
- [ ] **Step 2: Run** — FAIL (unresolved `RailNavigation`).
- [ ] **Step 3: Implement** `RailNavigation.stackFor` and `MobileRail`.
- [ ] **Step 4: MainActivity** — one `fun navigate(destination: RailDestination)` replaces Home's inline `when`: resets `genresState`/`searchState` as today for their destinations, then `backStack.clear(); backStack.addAll(RailNavigation.stackFor(destination))`. Home keeps calling it (from Home the stack is `[Home]`, so the result equals today's `add`). `Screen.Search`, `Screen.Genres`, `Screen.Settings` render inside `Box(Modifier.fillMaxSize()) { <screen>; MobileRail(<its destination>, ::navigate) }`. Sub-screens (Programme, « Tout voir », Provider, Sous-titres, À propos) get no rail: Back returns to their parent.
- [ ] **Step 5: Run** tests + `assembleDebug` — green.
- [ ] **Step 6: Pixel check** — from Search tap Genres → Genres; Back → Home (not Search); from Paramètres tap Accueil → Home; the rail never expands; content does not sit under the rail (screens already start at `Rb.contentStart`).
- [ ] **Step 7: Commit** `feat: navigation rail on every top-level screen on mobile`.

### Task 6: Home top panel follows the billboard on mobile

**Files:**
- Modify: `app/src/main/java/com/replaybox/app/ui/HomeScreen.kt` (`shown` ~l.244, `DetailPanel` call ~l.282)
- Test: `app/src/test/java/com/replaybox/app/ui/HomePanelTest.kt`

**Interfaces:**
- Produces: `internal fun panelFeatured(mobile: Boolean, focused: Featured?, billboard: Featured?): Featured?` in `HomeScreen.kt` — mobile: `billboard`; box: `focused ?: billboard`.

- [ ] **Step 1: Write the failing tests** — `HomePanelTest`: `boxFollowsFocusFirst` (`panelFeatured(false, a, b) == a`, `panelFeatured(false, null, b) == b`); `mobileShowsOnlyTheBillboard` (`panelFeatured(true, a, b) == b`, `panelFeatured(true, a, null) == null`). `Featured` is constructible as in `SeeAllCard` (`Featured(title, null, null, null)`).
- [ ] **Step 2: Run** — FAIL.
- [ ] **Step 3: Implement** and use it: `val shown = panelFeatured(mobile, focused, billboardFeatured)`; `billboard = billboard.takeIf { mobile || focused == null }`; on mobile, skip `DetailPanel` entirely when `billboard == null` (rails take the whole height). `Backdrop` follows `shown`.
- [ ] **Step 4: Run** tests + `assembleDebug` — green.
- [ ] **Step 5: Pixel check** — fresh install: rails start at the top. Play a France TV Épisode 1 min, Back to Home: the « À suivre » billboard shows with « Reprendre ». Long-press the « À suivre » card (Task 4 check). Clean up the Progression.
- [ ] **Step 6: Commit** `feat: Home shows the billboard only on mobile`.

### Task 7: Search with the native keyboard

**Files:**
- Create: `app/src/main/java/com/replaybox/app/ui/SearchField.kt`
- Modify: `app/src/main/java/com/replaybox/app/ui/SearchModel.kt` (`SearchUiState`)
- Modify: `app/src/main/java/com/replaybox/app/ui/SearchScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/replaybox/app/ui/SearchStateTest.kt`

**Interfaces:**
- Consumes: `LocalMobile`, `MobileRail` (Task 5 already wraps the screen).
- Produces:
  - `SearchUiState.setQuery(text: String): Boolean` — `text.take(SearchKeyboard.MAX_LENGTH)`; false when equal to `query`; else sets `query`, `rightKey = null`, true (same contract as `type(key)`).
  - `SearchUiState.opensKeyboardOnEntry: Boolean` — `query.isEmpty()`.
  - `@Composable fun SearchField(query: String, openKeyboard: Boolean, onQuery: (String) -> Unit, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Write the failing tests** in `SearchStateTest`:
  - `setQueryKeepsWhatWasTyped`: `setQuery("Pékin Express")` returns true, `query == "Pékin Express"` (matching is normalized elsewhere; the field shows what was typed).
  - `setQueryCapsAtMaxLength`: `setQuery("a".repeat(200))` → `query.length == 40`.
  - `setQuerySameTextIsNoChange`: second identical `setQuery` returns false.
  - `opensKeyboardOnEntryOnlyWhenEmpty`: fresh state true; after `setQuery("nar")` false.
- [ ] **Step 2: Run** `--tests '*SearchStateTest'` — FAIL.
- [ ] **Step 3: Implement** both members in `SearchUiState`.
- [ ] **Step 4: `SearchField`** — Compose 1.4's `BasicTextField` cannot set `IME_FLAG_NO_EXTRACT_UI`, so an `AndroidView` of an `EditText`:
  - `imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN`; `inputType = TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_NO_SUGGESTIONS`; `filters = arrayOf(InputFilter.LengthFilter(SearchKeyboard.MAX_LENGTH))`; single line; transparent background; text colour `Rb.text`, hint `R.string.search_hint` in `Rb.textSecondary`, size and weight of `RbType.displayCompact`.
  - Text set once at creation from `query`; `doAfterTextChanged { onQuery(it.toString()) }`.
  - `setOnEditorActionListener`: on `IME_ACTION_SEARCH` hide the IME (`InputMethodManager.hideSoftInputFromWindow`) and `clearFocus()`.
  - `openKeyboard`: after the first layout, `requestFocus()` + `showSoftInput(view, SHOW_IMPLICIT)`.
  - A Compose « × » (`Icons.Filled.Close`, `contentDescription = stringResource(R.string.search_clear)`) at the end of the row, shown when `query.isNotEmpty()`; tap sets the EditText text to "" (the watcher updates the state) and keeps the IME as it is.
- [ ] **Step 5: Strings** — `search_clear` « Effacer la recherche ».
- [ ] **Step 6: `SearchScreen` on mobile** (`val mobile = LocalMobile.current`):
  - no `SearchKeys`; a `Column` with `SearchField(state.query, state.opensKeyboardOnEntry, onQuery = { if (state.setQuery(it)) scroll grid to 0 (same as onType) })` at `start = Rb.contentStart, top = TOP`, then the grid below it; the grid's header item keeps the chips but not `QueryLine`.
  - grid `columns = TvGridCells.Fixed(POSTER_GRID_COLUMNS)`, `padding(start = Rb.contentStart - GUTTER)` (full width).
  - `BackHandler(enabled = !mobile && state.inRightColumn)`; the initial-focus `LaunchedEffect` is skipped on mobile (no key to focus).
  - `BestResultCard`: on mobile the art + title `Row` gets `Modifier.clickable(indication = null, …) { onEpisodes() }`; on the box no modifier (no new focus stop).
  - Box path untouched.
- [ ] **Step 7: Run** tests + `assembleDebug` — green.
- [ ] **Step 8: Pixel check** (Review Focus 2, 5) — open Search from the rail: IME opens at the bottom, field visible, not fullscreen; type « nar » → results live above the IME; « Rechercher » hides it; tap the best result's art → Programme; Back → Search, query and results kept, IME closed; Back → leaves Search. « × » clears. Paste a long text → cut at 40.
- [ ] **Step 9: Commit** `feat: search with the native keyboard on mobile`.

### Task 8: PRD/ROADMAP, box regression check, PR

**Files:**
- Modify: `PRD.md` (« Hors périmètre V1 », new section after it)
- Modify: `ROADMAP.md` (new milestone before « V2 », V2 line)

- [ ] **Step 1: PRD.md** — remove « mobile/tablette, » from « Hors périmètre V1 »; add after that section:
  ```markdown
  ## Après la V1 — mobile et tablette

  Décision d'Arnaud du 2026-11-10 : la V1/MVP est livrée. Mobile et tablette sortent du hors-périmètre :
  la même APK s'installe sur un téléphone ou une tablette Android 8+ et s'utilise au doigt, en paysage.
  Sur la box, rien ne change. Jalon livré en version 1.0.0.
  ```
- [ ] **Step 2: ROADMAP.md** — before « ## V2 », add `## Mobile et tablette (1.0.0)` with the décision date, the criterion « une soirée au doigt sur le Pixel (accueil → recherche → Programme → lecture), et la box inchangée », and three unchecked items: (i) entrée tactile et navigation, (ii) player tactile, (iii) layouts compact/tablette. In « V2 », change « Profils enfants, Fire TV, mobile » to « Profils enfants, Fire TV ». Tick (i) only after Step 4 passes (AGENTS.md rule).
- [ ] **Step 3: Unit tests + assembleDebug** — green.
- [ ] **Step 4: Box regression** (Review Focus 1) — on `main` first, then on the branch, same screencaps: Home (focus on first card), Programme page (focus on Lecture), Search (key A focused, then « nar » typed, RIGHT into results), Genres, Paramètres. Compare pairs: identical layout and focus. Also a D-pad pass through the Search best result: no extra focus stop on its art. Remove any Progression created (MENU → « Marquer comme vu » → « Marquer comme non vu »).
- [ ] **Step 5: Pixel full pass** — Tasks 1–7 checks in one session, then `pm clear com.replaybox`.
- [ ] **Step 6: Commit** `docs: bring mobile and tablet into scope after V1`, push, open the PR (French summary, the Pixel/box evidence, « 🤖 Generated with Claude Code »).
- [ ] **Step 7: Final independent review** of the whole branch (fresh reviewer, most capable model), fix what it confirms, then merge when unit tests + `assembleDebug` are green and both devices are checked.
