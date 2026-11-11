# UX optimizations (issue #69) — Design spec

**Source:** GitHub issue #69 « optimisation ux diverses » (2026-11-10) + grilling 2026-11-11 (Q1–Q12, all answered by Arnaud).
**Release:** 1.0.1 once the three feature PRs are merged and box-checked (Arnaud: these are optimizations, not new features).
**Out of scope:** issue #70 (user account).

## Global constraints

- Every UI change goes through the `impeccable` skill and stays within `DESIGN.md` and `PRODUCT.md`. Use the design tokens (`Rb.*`, `RbType.*`), never ad-hoc values.
- Glossary terms (`CONTEXT.md`) as is: Programme, Épisode, Genre, Flux. UI strings in French in `values/strings.xml`; code, comments, tests and commits in English.
- Mobile-only behaviour is gated on `LocalMobile`. Anything this spec does not change on the box stays pixel-identical to `main` (A/B screencap diff, D-pad script, see auto-memory `box-scripted-keys-safety`).
- Decisions live in pure, unit-tested objects (the `PlayerTouch` / `PauseReveal` / `RailNavigation` pattern); Compose code only wires them.
- Gates per PR: `./gradlew testDebugUnitTest` + `./gradlew assembleDebug` green, checked on the Bbox and on the Pixel 7 (compact class). Tablet class: emulator only (no `wm density` on the Pixel).
- minSdk 26, no dependency bump.

## Delivery

| PR | Branch | Content | Platforms |
|---|---|---|---|
| A — Navigation | `feat/ux-navigation` | Rail on every screen and enlarged, back arrow, exit confirmation | rail + arrow: mobile; exit dialog: box + mobile |
| B — Player | `feat/ux-player` | Tap = pause, centre icon, Pause/Lecture button removed, draggable timeline, subtitle preference | see each item |
| C — Genres | `feat/ux-genre-covers` | Artwork on the Genre tiles | box + mobile |
| D — Release | `chore/release-1.0.1` | `versionName` 1.0.1 (+ `versionCode`), `ROADMAP.md` boxes ticked | — |

Order A → B → C → D. D only after A, B and C are merged and verified on the box. A `ROADMAP.md` section « Optimisations UX (1.0.1) » listing the seven items is added in PR A; each PR ticks its own items once box-checked.

---

## PR A — Navigation

### A1. Rail always visible (mobile)

**Today:** the rail shows on Home (its own `NavRail`) and, through `MobileRail`, on Recherche, Genres and Paramètres only. Show detail, « Tout voir » grids, Settings sub-screens (Sous-titres, Provider, À propos) have none. Collapsed for good on touch.

**Target:**
- On mobile the rail shows on **every screen except the player** (`Screen.Player`).
- Selected destination per screen: Home and Show detail opened from Home → Accueil; Genres and `GenreGrid` → Genres; Search → Recherche; Settings and its sub-screens (SubtitleSettings, ProviderDetail, About) → Paramètres. A Show detail opened from Search or a Genre grid highlights the destination it was opened from (the rail destination of the stack's root-most rail screen).
- Tapping a destination keeps today's `RailNavigation.stackFor` behaviour: the stack resets to `Home (+ destination)`. Tapping the highlighted destination from one of its sub-screens (e.g. Accueil from a Show detail) returns to that destination's root; it is a no-op only when already on that root (`RailNavigation.next` returns null).
- Still collapsed, icons only, no labels.
- **Bigger:** each item's touch target is **at least 48 dp in real (physical) dp**, on the compact canvas too, where everything is scaled by `CompactCanvas.scale` (≈ 0.76 on a 411 dp-tall phone). The icon grows with it. Exact sizes, rail width and the resulting mobile content start are decided with impeccable against `DESIGN.md` (`rail-collapsed`, `content-start`, 48 dp buttons). The box keeps `rail-collapsed` 72 dp and its current item size.
- Content must not sit under the rail on any screen that gains it (Show detail, grids, sub-screens).

### A2. Back arrow (mobile)

- A back arrow occupies the **top slot of the rail**, above Accueil, at the same size as the enlarged rail items.
- Shown on every screen that has the rail except the **Home root** (stack == `[Home]`). There the slot stays empty so the rail items never move.
- In the **player** (no rail): the arrow sits top-left in the controls overlay, shown only while the controls are visible (paused, or during the 5 s after a reveal).
- Behaviour: the rail arrow does exactly what the system Back does (dispatch through `OnBackPressedDispatcher`, so screen-level `BackHandler`s keep working). The player arrow leaves the player to the previous screen in one tap (it does not first hide the controls, unlike the system Back today).
- Accessible label « Retour » (`contentDescription`).
- Box: no arrow; the remote has Back.

### A3. Exit confirmation (box + mobile)

- When Back would leave the app (stack == `[Home]` and Home already scrolled to its top, i.e. Home's own `BackHandler(enabled = !atTop)` is not consuming it), show a dialog instead of finishing.
- Reuse `ActionDialog`: title « Quitter Replaybox ? », actions « Quitter » and « Annuler ». Default focus on **Annuler**. Back while the dialog is open cancels it. « Quitter » finishes the activity.
- Decision object (e.g. `ExitConfirm.shows(stack, homeAtTop)`), unit-tested.
- The remote's Home key and the mobile Home gesture are untouched (system-level).

---

## PR B — Player

### B1. One tap = play/pause (mobile)

**Today:** `PlayerTouch.tap` shows or hides the controls; double-tap left/right half seeks −10 / +10 s.

**Target:**
- A single tap on the video **toggles play/pause** (same `togglePlayPause()` the remote's OK uses).
- Double-tap ±10 s stays. Accepted cost: the single tap waits for the double-tap timeout (~300 ms) before pausing.
- Exceptions kept: a resume prompt (« Reprendre / Débuter ») or Épisode suivant on screen → the tap does nothing (their buttons take taps); options panel open → the tap closes it.
- Controls: a pause shows them (existing `PauseReveal`); after resuming they auto-hide after the usual 5 s. There is no longer a gesture to show the controls while playing without pausing (wanted).
- `PlayerTouch.tap` returns the new action set (e.g. `TOGGLE_PLAY`, `CLOSE_OPTIONS`, `NONE`); tests updated.

### B2. Centre play/pause icon (box + mobile)

- While paused (and not ended, no resume prompt, no Épisode suivant), a large **pause** glyph stays centred on the video.
- On resume it turns into a **play** glyph that fades out in ~600 ms.
- Size, scrim/backing and motion per impeccable + `DESIGN.md` (calm, no bounce; « nothing moves until the remote moves it » holds since the user triggered it).
- Not a button on its own: on mobile the whole video surface already toggles (B1).

### B3. Pause/Lecture removed from the control row (box + mobile)

- The row becomes: −10 s, +10 s, Recommencer, Sous-titres, Options.
- **Box focus model** (it was the initial focus): when the controls appear, **no row button has focus**. Keys then behave as when the controls are hidden: OK = play/pause, Left/Right = −10 / +10 s (and keep the controls shown), **Down** moves focus into the row (first button). From the row, **Up** returns to the no-button state. Back hides the controls as today.
- `PlayerKeys` gets the state (controls visible, focus in row or not) and is tested for each key.
- Mobile: the row keeps its buttons; play/pause is the tap (B1).

### B4. Draggable timeline (mobile)

- The `Timeline` bar accepts **drag** and **tap** on mobile.
- While dragging: the fill and the left clock follow the finger, and a time bubble above the thumb shows the target position. The Flux is **not** sought until release.
- On release: one `seekTo(target)`. A tap on the bar seeks straight to that point.
- Auto-hide is suspended during the drag and restarts after release.
- Touch target taller than the 6 dp visual bar (≥ 48 dp real); a visible thumb appears on mobile.
- The position ↔ x mapping is a pure function (clamped to `[0, duration]`, no-op when duration is unknown), unit-tested.
- Box: unchanged, display only.

### B5. Subtitle preference (box + mobile)

**Today:** every Épisode starts with French subtitles on (PRD story 2, Q7 of a previous grilling); `PlaybackPreferences` stores audio + quality only.

**Target:**
- `PlaybackPreferences` gains the subtitle preference: **on/off** + **kind** (`FRENCH` or `HEARING_IMPAIRED`), global (not per Programme), stored in the same `playback` SharedPreferences.
- Default (nothing stored yet): on, French — today's behaviour.
- Saved **only on an explicit user action**: the Sous-titres toggle, the `TOGGLE_SUBTITLES` key, a choice in the options panel. Choosing another language (`SubtitleKind.OTHER`) is a one-off, like `AudioKind.OTHER`: it saves « on » but keeps the stored kind.
- Applied when an Épisode's tracks arrive: off → text disabled; on → the stored kind if present, else French, else `PlaybackChoices.defaultSubtitle`.
- An Épisode with no subtitle track neither saves nor clears anything.
- Applies to the next Épisode played automatically (Épisode suivant) too.
- Pure rules (encode/decode, which choice to apply, what a user action saves) unit-tested.
- **PRD:** story 2 is rewritten (Arnaud's explicit decision, grilling 2026-11-11 Q2), e.g. « En tant que spectateur, je veux que les sous-titres français soient activés par défaut, puis que l'app retienne mon dernier choix (activés ou non, et leur type), pour ne jamais y penser. » Update the `PlaybackPreferences` KDoc accordingly.

---

## PR C — Genre covers (box + mobile)

**Today:** 9 text-only tiles, 3 × 3, 112 dp tall (`GenresScreen.kt`).

**Target:**
- Each tile shows the artwork of the **first Programme of that Genre's « Tout voir » grid** (`GenreGrid.sort`, same Chaînes and same source as the grid) that has an image, read from the cache (ADR 0002, no network fetch of its own).
- Image choice: `backdropUrl ?: posterUrl` (tiles are wide, like the Home backdrop and Search cards).
- **No duplicate:** tiles are resolved in glossary order; a Programme already used by an earlier tile is skipped for the next one in its grid.
- No usable image (empty cache, no artwork) → the tile stays text-only as today.
- Genre name over the image with a ground-coloured gradient, `RbType.railTitle`, still readable at focus scale; focus treatment unchanged (card focus, 1.1 lift + ring).
- The image changes as the catalogue changes (accepted: dynamic over a fixed per-Genre visual, Q8).
- Selection is a pure function (`List<Genre>` + per-Genre sorted grids → cover per Genre), unit-tested for order, dedup and fallback.
- Loading must not shift focus or layout: the tile keeps its size; artwork fades in like other cards.

---

## PR D — Release 1.0.1

- Bump `versionName` to `1.0.1` and `versionCode`, following the 1.0.0 release PR (#68).
- Tick the `ROADMAP.md` items verified on the box.

## Test & verification checklist

- Unit: `RailNavigation` (destination per screen, reset), `ExitConfirm`, `PlayerTouch`, `PlayerKeys` (focus states), timeline mapping, subtitle preference rules, Genre cover selection.
- Box: exit dialog (focus on Annuler, Back cancels); player row without Pause/Lecture, OK/Left/Right/Down/Up/Back per B3; centre icon; subtitle preference survives an Épisode change and an app restart; Genre covers; everything else A/B identical.
- Pixel 7: rail on every non-player screen, ≥ 48 dp targets, back arrow (rail and player); tap = pause with double-tap seek intact; timeline drag/tap; subtitle preference; Genre covers.
- Tablet class: emulator.
