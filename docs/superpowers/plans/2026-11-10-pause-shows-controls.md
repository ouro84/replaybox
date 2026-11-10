# Pause shows the controls — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Whenever playback is paused by the viewer, from any source (OK, the ⏯ media key through the `MediaSession`, HDMI-CEC from the TV remote, audio focus loss, the app going to the background), the player controls (`PlayerControls`: title, timeline, Pause/Lecture, −10 s, +10 s, Recommencer, Sous-titres, Options) appear on their own.

**Architecture:** Today only the OK key reveals the controls (`PlayerKeys.Action.PLAY_PAUSE` calls `showControls()` in `PlayerScreen.kt`); media keys are consumed by the `MediaSession` and pause the player without touching the overlay. The fix listens to the player itself: `Player.Listener.onPlayWhenReadyChanged(playWhenReady, reason)` fires for every pause whatever its source. A pure decision, `PauseReveal.shows(...)`, says whether this pause reveals the controls; `PlayerSurface` calls `showControls()` when it does. Once shown while paused, the existing rules apply unchanged: they stay while paused, auto-hide 5 s after the last interaction once playing, focus lands on Pause/Lecture.

**Tech Stack:** Kotlin, Compose, Media3 ExoPlayer 1.x, JUnit4 + Truth.

**Spec:** grilling 2026-11-10 (Q1, Q2 bis, Q3, Q13), recorded in the auto-memory `mobile-milestone-decisions`.

## Global Constraints

- Code, comments, tests, commit messages in English; no new UI string (none needed).
- Player behaviour is tested on pure logic, never on ExoPlayer (AGENTS.md).
- Build `./gradlew assembleDebug`, tests `./gradlew testDebugUnitTest`, JDK 11 (as `scripts/release.ps1`).
- Verified on the Bbox over adb before merge (`box-adb-access`, `box-scripted-keys-safety`: check Replaybox is in the foreground before every key press, screencap before every OK).
- Release: version `0.6.3` (`gradle.properties`, `replaybox.version`).

## Decisions (from the grilling)

| Pause happens while… | Controls |
|---|---|
| nothing else on screen (any source) | shown, focus on Pause/Lecture |
| controls already shown | stay shown (no change) |
| Options panel open | Options stays open; controls show when it closes (existing `closeOptions()`) |
| « Reprendre / Débuter » prompt shown | not shown |
| Épisode suivant panel shown | not shown (panel keeps the screen) |
| end of the Épisode (`PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM`) | not shown |
| buffering | not a pause: `playWhenReady` stays true, nothing fires |

On resume (`playWhenReady` back to true): nothing new, the existing 5 s auto-hide runs.

## Review Focus

1. ⏯ media key on the Bbox remote with the controls hidden → controls appear paused with focus on « Lecture »; a second ⏯ resumes and the controls hide 5 s later. (Box check, Task 2.)
2. OK with the controls hidden → controls appear once, focus on the play button, exactly as before (the listener calling `showControls()` a second time must not move focus elsewhere). (Box check, Task 2.)
3. Home on the remote mid-Épisode, then back into Replaybox → the Épisode is paused with the controls shown, no crash: the lifecycle `ON_PAUSE` pause fires while the player is still alive. (Box check, Task 2.)
4. Pause while the « Reprendre » prompt shows → the prompt stays alone on screen. (Unit test, Task 1.)
5. The listener lives in a `DisposableEffect(player)` closure: `nextUp` is a plain `val` recomputed per composition, so a captured value goes stale. Read it through `rememberUpdatedState`. (Review in Task 2; logic covered by the Task 1 test « Épisode suivant ».)

---

### Task 1: `PauseReveal` decision

**Files:**
- Modify: `app/src/main/java/com/replaybox/app/ui/PlayerControls.kt` (next to `PlayerKeys`)
- Test: `app/src/test/java/com/replaybox/app/ui/PauseRevealTest.kt`

**Interfaces:**
- Produces: `object PauseReveal { fun shows(playWhenReady: Boolean, endOfMedia: Boolean, resumePrompt: Boolean, nextUp: Boolean, optionsOpen: Boolean): Boolean }`. Media3-free (the caller maps the reason to `endOfMedia`), like `TrackRef`.

- [ ] **Step 1: Write the failing tests** in `PauseRevealTest` (KDoc: `/** A pause reveals the player controls (grilling 2026-11-10, Q1). */`):
  - `a pause with nothing else on screen shows the controls`: `shows(playWhenReady = false, endOfMedia = false, resumePrompt = false, nextUp = false, optionsOpen = false)` is `true`.
  - `resuming never shows them`: `playWhenReady = true`, everything else false → `false`.
  - `the end of the Épisode is not a pause`: `endOfMedia = true` → `false`.
  - `the Reprendre prompt keeps the screen`: `resumePrompt = true` → `false`.
  - `the Épisode suivant panel keeps the screen`: `nextUp = true` → `false`.
  - `an open Options panel stays open`: `optionsOpen = true` → `false`.

- [ ] **Step 2: Run them, expect a compile failure** (`PauseReveal` unresolved)

Run: `./gradlew testDebugUnitTest --tests "com.replaybox.app.ui.PauseRevealTest"`

- [ ] **Step 3: Implement `PauseReveal.shows`** with a KDoc listing the exclusions (one line each, as in the Decisions table).

- [ ] **Step 4: Run the tests, expect PASS**

Run: `./gradlew testDebugUnitTest --tests "com.replaybox.app.ui.PauseRevealTest"`

- [ ] **Step 5: Commit** — `feat: decide when a pause reveals the player controls`

### Task 2: Wire it into the player, verify on the box

**Files:**
- Modify: `app/src/main/java/com/replaybox/app/ui/PlayerScreen.kt` (`PlayerSurface`, right after `fun showControls()`)

**Interfaces:**
- Consumes: `PauseReveal.shows(...)` (Task 1); `showControls()`, `showResumeChoice`, `optionsOpen`, `nextUp` already in `PlayerSurface`.

- [ ] **Step 1: Add the listener.** After `fun showControls()` (it must be declared first), add `val currentNextUp by rememberUpdatedState(nextUp)` and a `DisposableEffect(player)` that registers a `Player.Listener` overriding `onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int)`: when `PauseReveal.shows(playWhenReady, endOfMedia = reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM, resumePrompt = showResumeChoice, nextUp = currentNextUp, optionsOpen = optionsOpen)` call `showControls()`; remove the listener in `onDispose`. Declared after the `MediaSession` / `player.release()` effect, it is disposed before the player is released (Compose disposes in reverse order). Comment: why a player listener and not the key handler (the `MediaSession` consumes ⏯ and CEC keys).

- [ ] **Step 2: Unit suite and build green**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Box check** (install `app/build/outputs/apk/debug/app-debug.apk` over adb; Replaybox in the foreground before every key, screencap before every OK). Start an Épisode already in « Reprendre » (no Progression side effect to clean, or clean it with MENU → « Marquer comme vu » / « Marquer comme non vu »). Then, screencap after each:
  1. Controls hidden, `input keyevent KEYCODE_MEDIA_PLAY_PAUSE` → controls shown, focus on « Lecture ».
  2. Same key again → playback resumes; screencap after 6 s → controls hidden.
  3. Controls hidden, `KEYCODE_DPAD_CENTER` → controls shown once, focus on the play button (unchanged behaviour).
  4. MENU (Options open), `KEYCODE_MEDIA_PLAY_PAUSE` → Options still open; Back → controls shown, paused.
  5. `KEYCODE_HOME`, then relaunch Replaybox (`monkey -p com.replaybox 1`) → Épisode paused, controls shown.
  Expected: all five as stated. Report any that fails; do not merge on a failed check.

- [ ] **Step 4: Commit and PR** — commit `feat: show the player controls on any pause`; branch `feat/pause-shows-controls` (plan file committed on it as `docs: plan for pause shows controls`); PR on `main`, body lists the five box checks with their result. Merge when green and box-checked.

### Task 3: Release 0.6.3

**Files:**
- Modify: `gradle.properties` (`replaybox.version=0.6.3`)

- [ ] **Step 1:** Branch `chore/release-0.6.3` from the updated `main`, bump, commit `chore: bump version to 0.6.3`, PR, merge (same shape as #58).
- [ ] **Step 2:** On `main`, synced: `powershell -ExecutionPolicy Bypass -File scripts\release.ps1 -DryRun`, then without `-DryRun`. Expected: tag `v0.6.3` and a GitHub Release with both APKs.
- [ ] **Step 3:** On the box, the in-app update banner offers 0.6.3; installing it keeps Progression and settings.
