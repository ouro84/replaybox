# Mobile touch player — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On phones and tablets, a tap on the video shows or hides the player controls and a double-tap on the left or right half seeks −10 s / +10 s; on every device (box included, approved by Arnaud on 2026-11-10) the player takes the audio focus, so a phone call or another app playing sound pauses it.

**Architecture:** Touch decisions are pure functions in a new `PlayerTouch` object (same pattern as `PlayerKeys` / `PauseReveal`), unit-tested. `PlayerScreen` adds, on mobile only, a transparent gesture layer between the `PlayerView` and the overlays (`detectTapGestures(onTap, onDoubleTap)`); it calls the existing `showControls()`, `controlsVisible = false`, `closeOptions()`, `player.seekBack()/seekForward()`. A short « −10 s » / « +10 s » flash on the tapped side confirms a double-tap. Audio focus: `ExoPlayer.Builder.setAudioAttributes(PlayerAudio.attributes, handleAudioFocus = true)`; the resulting pause (reason `AUDIO_FOCUS_LOSS`) already reveals the controls through `PauseReveal` (PR #63).

**Tech Stack:** Kotlin, Compose 1.4, Media3 1.1.1, JUnit4 + Truth.

**Spec:** brief of 2026-11-10 (PR ii) + grilling 2026-11-10 (« tap toggles controls, double-tap sides ±10 s, no brightness/volume swipe ») + Arnaud's OK on audio focus (2026-11-10), in the auto-memory `mobile-milestone-decisions`.

## Global Constraints

- Touch gestures exist on mobile only (`LocalMobile.current`); the box key handling (`PlayerKeys`) is untouched.
- Audio focus applies to the box too (approved). Nothing else changes on the box.
- No brightness/volume swipe, no seek-bar scrubbing (not asked; out of scope).
- minSdk 26, no dependency bump. Code/comments/tests/commits in English; UI strings in French (reuse `control_back10` « −10 s », `control_fwd10` « +10 s »).
- Player behaviour tested on pure logic, never on ExoPlayer (AGENTS.md).
- Build `./gradlew assembleDebug`, tests `./gradlew testDebugUnitTest`, `export JAVA_HOME="/c/Program Files/Android/Android Studio/jre"`.
- Branch `feat/mobile-touch-player`; one PR; no release (1.0.0 after PR iii). Merge when unit tests + assembleDebug are green and both devices are checked.
- Devices: Pixel 7 `2B151FDH2002RZ` (stay-on is enabled), Bbox `192.168.1.182:5555` (CEC off for the milestone; `box-scripted-keys-safety`). Undo Progression created by tests (Pixel: `pm clear`; box: MENU → « Marquer comme vu » → « Marquer comme non vu »).

## Decisions

| Gesture (mobile) | Nothing shown | Controls shown | Options open | « Reprendre / Débuter » or Épisode suivant |
|---|---|---|---|---|
| Tap on the video | show controls | hide controls | close Options (controls come back, as Back does today) | nothing (their buttons take taps) |
| Double-tap left half | −10 s, flash « −10 s » left | −10 s, controls stay, auto-hide timer restarts | nothing | nothing |
| Double-tap right half | +10 s, flash « +10 s » right | +10 s, same | nothing | nothing |

- Halves split at `width / 2`; a double-tap never toggles the controls.
- A single tap waits for the double-tap timeout (~300 ms) before toggling (Compose `detectTapGestures` behaviour, accepted).
- The flash lasts 600 ms; a new double-tap restarts it.
- Paused + tap: the controls hide anyway (an explicit gesture beats the « stay while paused » rule).
- Audio focus: usage `C.USAGE_MEDIA`, content type `C.AUDIO_CONTENT_TYPE_MOVIE`, `handleAudioFocus = true`. Transient loss (call) pauses and Media3 resumes after it; permanent loss (another player) pauses for good. Both pauses show the controls (PauseReveal, reason ≠ end of media).
- Proposed, to confirm at plan review: `setHandleAudioBecomingNoisy(true)` (unplugging headphones / BT disconnect pauses). Included in Task 1 unless Arnaud says no.

## Review Focus

1. Box: OK, LEFT/RIGHT, Back, MENU in the player behave exactly as on `main` (the gesture layer must not exist on the box, focus must still land on Pause/Lecture). (Box check, Task 3.)
2. Mobile: a tap on a control button (Pause, −10 s, Options…) acts on the button only, never also toggles the controls through the layer underneath. (Pixel check, Task 3.)
3. Mobile: tap on the video while the « Reprendre / Débuter » prompt shows does nothing, the prompt stays. (Unit test `promptOrNextUpIgnoresTaps`, Task 2.)
4. Audio focus lost while the Options panel is open: playback pauses, Options stays open (PauseReveal rule unchanged). (Pixel check, Task 1.)
5. Two quick double-taps on the right: +20 s, one flash restarted, no controls toggle. (Pixel check, Task 3.)

---

### Task 1: Audio focus

**Files:**
- Create: `app/src/main/java/com/replaybox/app/ui/PlayerAudio.kt`
- Modify: `app/src/main/java/com/replaybox/app/ui/PlayerScreen.kt` (`ExoPlayer.Builder` ~l.259)
- Test: `app/src/test/java/com/replaybox/app/ui/PlayerAudioTest.kt`

**Interfaces:**
- Produces: `object PlayerAudio { val attributes: androidx.media3.common.AudioAttributes }`.

- [ ] **Step 1: Failing test** `PlayerAudioTest.mediaMovieAttributes`: `PlayerAudio.attributes.usage == C.USAGE_MEDIA`, `.contentType == C.AUDIO_CONTENT_TYPE_MOVIE`.
- [ ] **Step 2: Run** `--tests '*PlayerAudioTest'` — FAIL (unresolved `PlayerAudio`).
- [ ] **Step 3: Implement** `PlayerAudio` with `AudioAttributes.Builder()`; in the builder chain `.setAudioAttributes(PlayerAudio.attributes, /* handleAudioFocus = */ true).setHandleAudioBecomingNoisy(true)`.
- [ ] **Step 4: Run** tests + `assembleDebug` — green.
- [ ] **Step 5: Pixel check** — play a France TV Épisode, start another player (e.g. `am start -a android.intent.action.VIEW -d <a YouTube URL>` if YouTube is installed; else ask Arnaud for a test call) → Replaybox pauses with the controls shown; return → still paused. With Options open: pause, Options stays (Review Focus 4).
- [ ] **Step 6: Box check** — playback starts and plays as before (a France TV Épisode, OK pause/resume).
- [ ] **Step 7: Commit** `feat: take the audio focus in the player` (include this plan).

### Task 2: Touch decisions

**Files:**
- Modify: `app/src/main/java/com/replaybox/app/ui/PlayerControls.kt` (next to `PlayerKeys`)
- Test: `app/src/test/java/com/replaybox/app/ui/PlayerTouchTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  object PlayerTouch {
      enum class Tap { NONE, SHOW, HIDE, CLOSE_OPTIONS }
      enum class Seek { NONE, BACK, FORWARD }
      fun tap(controlsVisible: Boolean, optionsOpen: Boolean, prompt: Boolean): Tap
      fun doubleTap(x: Float, width: Float, optionsOpen: Boolean, prompt: Boolean): Seek
  }
  ```
  `prompt` = « Reprendre / Débuter » shown or Épisode suivant shown.

- [ ] **Step 1: Failing tests** (`PlayerTouchTest`), values from the Decisions table:
  - `tapShowsHiddenControls`: `tap(false, false, false) == SHOW`.
  - `tapHidesShownControls`: `tap(true, false, false) == HIDE`.
  - `tapClosesOptions`: `tap(false, true, false) == CLOSE_OPTIONS`.
  - `promptOrNextUpIgnoresTaps`: `tap(false, false, true) == NONE`, `doubleTap(10f, 100f, false, true) == NONE`.
  - `doubleTapSidesSeek`: `doubleTap(10f, 100f, false, false) == BACK`, `doubleTap(49.9f, 100f, …) == BACK`, `doubleTap(50f, 100f, …) == FORWARD`, `doubleTap(99f, 100f, …) == FORWARD`.
  - `doubleTapDoesNothingOverOptions`: `doubleTap(90f, 100f, true, false) == NONE`.
- [ ] **Step 2: Run** `--tests '*PlayerTouchTest'` — FAIL.
- [ ] **Step 3: Implement** `PlayerTouch` (prompt first, then options, then controls; split at `width / 2`).
- [ ] **Step 4: Run** — PASS.
- [ ] **Step 5: Commit** `feat: decide what a tap or double-tap does in the player`.

### Task 3: Gesture layer and seek flash

**Files:**
- Modify: `app/src/main/java/com/replaybox/app/ui/PlayerScreen.kt` (inside the root `Box` ~l.656–734)

**Interfaces:**
- Consumes: `PlayerTouch` (Task 2), `LocalMobile`.
- Produces: private `@Composable fun SeekFlash(seek: PlayerTouch.Seek, modifier: Modifier)` in `PlayerScreen.kt`.

- [ ] **Step 1:** right after the `AndroidView`, when `LocalMobile.current`: a `Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = …, onDoubleTap = { offset -> … }) })`. Callbacks read current state through `rememberUpdatedState` (controlsVisible, optionsOpen, showResumeChoice, nextUp). Tap: `SHOW → showControls()`, `HIDE → controlsVisible = false`, `CLOSE_OPTIONS → closeOptions()`. Double-tap: `BACK → player.seekBack()`, `FORWARD → player.seekForward()`; then `positionMs = player.currentPosition`, `interaction++`, set `flash` (state `PlayerTouch.Seek`) and a `flashTick++` that restarts a `LaunchedEffect(flashTick) { delay(600); flash = NONE }`. Overlays (NextUp, ResumeChoice, OptionsPanel, PlayerControls) stay after the layer in the Box, so their buttons get taps first.
- [ ] **Step 2:** `SeekFlash` — `Text(stringResource(control_back10 | control_fwd10))`, `RbType.displayCompact`, `Rb.text` on a `Rb.ground.copy(alpha = 0.6f)` pill, aligned `CenterStart`/`CenterEnd` with `Rb.contentStart` horizontal padding; drawn only when `flash != NONE`.
- [ ] **Step 3: Run** tests + `assembleDebug` — green.
- [ ] **Step 4: Pixel check** (Review Focus 2, 5): tap video → controls; tap video → hidden; tap « −10 s » button → seeks, controls stay; double-tap right twice quickly → +20 s, flash, controls unchanged; double-tap left → −10 s; tap with Options open → Options closes, controls show; resume prompt (replay an Épisode with Progression) → taps on video ignored.
- [ ] **Step 5: Box check** (Review Focus 1): France TV Épisode — OK pauses with controls and focus on « Lecture », LEFT/RIGHT seek when hidden, Back hides, MENU opens Options; screencap of the controls identical in layout to `main` (compare the controls region of a paused frame on both APKs).
- [ ] **Step 6: Commit** `feat: tap and double-tap gestures in the mobile player`.

### Task 4: ROADMAP, PR

- [ ] **Step 1:** ROADMAP.md « Mobile et tablette (1.0.0) »: tick (ii) and add « focus audio (pause pendant un appel), box comprise ».
- [ ] **Step 2:** unit tests + assembleDebug green; clean Progression on both devices.
- [ ] **Step 3:** commit `docs: tick the touch player in the roadmap`, push, PR (French summary, device evidence, what was not verified).
- [ ] **Step 4:** final independent review (fresh reviewer, most capable model), fix what it confirms, merge when green and checked on both devices.
