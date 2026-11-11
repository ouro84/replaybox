---
name: Replaybox
description: French replay (TF1+, France TV) on Android TV, the streaming standard tuned for a 55" screen at 3 m in a dim room.
colors:
  ground: "#0E1013"
  surface: "#181B20"
  surface-raised: "#262A31"
  track: "#3A3F48"
  text: "#F3F4F6"
  text-secondary: "#B8BEC8"
  focus: "#FFFFFF"
  on-focus: "#0E1013"
  accent: "#F2B544"
  error: "#FF9C8F"
typography:
  display:
    fontFamily: "Roboto, sans-serif"
    fontSize: "40sp"
    fontWeight: 700
    lineHeight: "46sp"
    letterSpacing: "-0.5sp"
  display-compact:
    fontFamily: "Roboto, sans-serif"
    fontSize: "30sp"
    fontWeight: 700
    lineHeight: "36sp"
    letterSpacing: "-0.3sp"
  rail-title:
    fontFamily: "Roboto, sans-serif"
    fontSize: "20sp"
    fontWeight: 500
    lineHeight: "26sp"
  body:
    fontFamily: "Roboto, sans-serif"
    fontSize: "18sp"
    fontWeight: 400
    lineHeight: "26sp"
  body-strong:
    fontFamily: "Roboto, sans-serif"
    fontSize: "18sp"
    fontWeight: 500
    lineHeight: "24sp"
  meta:
    fontFamily: "Roboto, sans-serif"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: "22sp"
  card-title:
    fontFamily: "Roboto, sans-serif"
    fontSize: "16sp"
    fontWeight: 500
    lineHeight: "22sp"
  label:
    fontFamily: "Roboto, sans-serif"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: "20sp"
rounded:
  button: "8dp"
  card: "10dp"
  pill: "50%"
spacing:
  xs: "4dp"
  rail-stack: "8dp"
  button-gap: "12dp"
  caption-top: "14dp"
  gutter: "20dp"
  card-gap: "20dp"
  dialog: "24dp"
  safe-vertical: "27dp"
  safe-horizontal: "48dp"
  rail-collapsed: "72dp"
  content-start: "104dp"
  rail-expanded: "232dp"
components:
  button-panel:
    backgroundColor: "{colors.surface-raised}"
    textColor: "{colors.text}"
    typography: "{typography.body-strong}"
    rounded: "{rounded.button}"
    padding: "0 20dp"
    height: "44dp"
  button-panel-focus:
    backgroundColor: "{colors.focus}"
    textColor: "{colors.on-focus}"
    typography: "{typography.body-strong}"
    rounded: "{rounded.button}"
    padding: "0 20dp"
    height: "44dp"
  card-poster:
    backgroundColor: "{colors.surface-raised}"
    rounded: "{rounded.card}"
    width: "116dp"
  card-up-next:
    backgroundColor: "{colors.surface-raised}"
    rounded: "{rounded.card}"
    width: "176dp"
  nav-rail:
    backgroundColor: "{colors.ground}"
    width: "{spacing.rail-collapsed}"
  nav-rail-expanded:
    backgroundColor: "{colors.surface}"
    width: "{spacing.rail-expanded}"
  nav-item:
    textColor: "{colors.text-secondary}"
    typography: "{typography.body-strong}"
    rounded: "{rounded.pill}"
    padding: "0 12dp"
    height: "48dp"
  nav-item-selected:
    backgroundColor: "{colors.surface-raised}"
    textColor: "{colors.text}"
    rounded: "{rounded.pill}"
    height: "48dp"
  nav-item-focus:
    backgroundColor: "{colors.focus}"
    textColor: "{colors.on-focus}"
    rounded: "{rounded.pill}"
    height: "48dp"
  progress-bar:
    backgroundColor: "{colors.track}"
    rounded: "{rounded.pill}"
    width: "120dp"
    height: "4dp"
  stale-notice:
    textColor: "{colors.text-secondary}"
    typography: "{typography.meta}"
    rounded: "{rounded.pill}"
    padding: "4dp 12dp"
  update-chip:
    backgroundColor: "{colors.surface-raised}"
    textColor: "{colors.text}"
    typography: "{typography.meta}"
    rounded: "{rounded.pill}"
    padding: "6dp 16dp"
  freshness-pill:
    backgroundColor: "{colors.surface-raised}"
    textColor: "{colors.text-secondary}"
    typography: "{typography.meta}"
    rounded: "{rounded.pill}"
    padding: "2dp 10dp"
  freshness-pill-on-focused-row:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text-secondary}"
    typography: "{typography.meta}"
    rounded: "{rounded.pill}"
    padding: "2dp 10dp"
  dialog:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    rounded: "{rounded.card}"
    padding: "{spacing.dialog}"
    width: "440dp"
  skeleton-block:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.card}"
---

# Design System: Replaybox

<!-- Recorded from the shipped Home build (HomeScreen.kt, NavRail.kt, Theme.kt) and the on-device captures in .impeccable/review/, 2026-11-06. Units are Android dp and sp. The Bbox renders a 960x540 dp canvas at 320 dpi, so 1 dp = 2 px on the 1080p panel. -->

## Overview

**Creative North Star: "The Short Path"**

The screen is a short path to the video. Replaybox runs the category standard (Netflix rail grammar, Apple TV's large legible cards and calm type) on a cool near-black ground. Nothing on screen moves until the remote moves it. The billboard does not rotate, trailers do not autoplay, and rails never reorder. The artwork carries the color. The interface around it is quiet neutral tones with one amber accent and a white focus.

Density is tuned for two viewers on a sofa 3 m from a 55" panel. Type starts at 14 sp and body text sits at 18 sp. Cards are large enough to read from across the room. Everything sits inside the 5 % overscan-safe frame. The top half of Home is a detail panel that names whatever has focus: title, a single metadata line, and either the synopsis or the remaining time. The Programme posters themselves carry no captions.

Depth comes from tonal surfaces and gradient scrims over key art, not from shadows. Focus is the loudest thing on screen: a white ring held 3 dp off the artwork plus a 1.1 scale. A focused button fills white with dark text.

**Key Characteristics:**
- Cool near-black ground, never pure black, with three tonal steps above it.
- One amber accent, used only for Progression and the current navigation destination.
- White focus, always paired with scale or an outline, never color alone.
- Key art bleeds in from the top right under left-to-right and top-to-bottom scrims.
- Stable, predictable rails. Focused cards pivot to a fixed edge, and the previous card hides fully behind the column clip.
- Roboto throughout, sp scale with a 14 sp floor.

## Colors

A cool, low-chroma neutral ramp with one warm accent. The key art supplies all other color.

### Primary
- **Screen White** (focus): The focus color. It is used for the focus ring around cards, the fill of a focused button, nav item or chip, and nothing else. Material `primary` maps to it.

### Secondary
- **Progression Amber** (accent): The single accent. It fills the progress bar on « À suivre » cards in progress and in the detail panel, and it tints the icon of the current navigation destination. Material `secondary` maps to it.

### Neutral
- **Night Ground** (ground): The app background and the base color of every scrim. It is cool near-black, never pure black, which avoids OLED smear and harsh edges. It also serves as the 72 % dim behind the expanded nav rail and the 85 % backing of the stale-catalog pill.
- **Slate Surface** (surface): Expanded nav rail panel, dialog body, skeleton placeholders, and the freshness pill on a focused Épisode row.
- **Raised Slate** (surface-raised): Resting button, update chip, freshness pill and selected-nav pill fill, and the card plate shown under loading or missing art.
- **Track Grey** (track): The unfilled part of every progress bar.
- **Paper White** (text): Titles, rail titles, card titles, button labels, and remaining time in the detail panel. 17.3:1 on ground.
- **Mist** (text-secondary): Metadata line, synopsis, card captions, stale notice, freshness pill text, unselected nav labels and icons. 10.2:1 on ground (AAA), 7.6:1 on Raised Slate, 9:1 on Slate Surface.
- **Ink on White** (on-focus): Text and icons on a white focus fill. Same value as ground.
- **Soft Coral** (error): Error text. Supplied through the Material color scheme and used outside Home only.

### Named Rules
**The One Amber Rule.** Amber marks Progression and the current navigation destination, nothing else. It never appears on buttons, focus, badges or text.

**The No Pure Black Rule.** The darkest value in the system is the ground (#0E1013). Scrims fade into the ground, never into #000.

**The Contrast Floor Rule.** All running text and metadata reach 7:1 on the surface they sit on. Text over key art sits on the scrim side, where the ground is at 92 % opacity or more.

## Typography

**Display Font:** Roboto (platform default, no custom family loaded)
**Body Font:** Roboto
**Label/Mono Font:** none distinct

**Character:** A single-family system. Hierarchy comes from size and weight steps (Bold, Medium, Regular), as on the Android TV home itself. Display titles carry slight negative tracking.

### Hierarchy
- **Display** (Bold 700, 40 sp, 46 sp line): The title of the focused or billboard item in the detail panel. One line.
- **Display Compact** (Bold 700, 30 sp, 36 sp line): Replaces Display when the title overflows one line. Up to two lines, then ellipsis.
- **Rail Title** (Medium 500, 20 sp, 26 sp line): Rail headings ("À suivre", "Favoris", Genre names), Genre tiles and dialog titles.
- **Body** (Regular 400, 18 sp, 26 sp line): Synopsis (max 2 lines in a 540 dp column), dialog hint, empty-catalog line.
- **Body Strong** (Medium 500, 18 sp, 24 sp line): Metadata line under the title ("France 3 · Policier & Thriller"), button labels, nav labels.
- **Meta** (Regular 400, 16 sp, 22 sp line): Remaining time ("34 min restantes"), stale notice, update chip.
- **Card Title** (Medium 500, 16 sp, 22 sp line): Title under an « À suivre » card or a grid poster, on missing-art plates, and the « Tout voir » card label.
- **Label** (Regular 400, 14 sp, 20 sp line): The floor. Defined and mapped to Material `bodySmall`/`labelSmall`, but not used on Home.

The Material3 mapping covers screens that still read `MaterialTheme.typography`: headlineLarge = Display, headlineMedium = 32/38 sp Bold, headlineSmall = 24/30 sp Medium, titleLarge = Rail Title, titleMedium and labelLarge = Body Strong, bodyLarge = Body, bodyMedium and labelMedium = Meta, bodySmall and labelSmall = Label.

### Named Rules
**The 14 sp Floor Rule.** Nothing renders below 14 sp. Running text is at least 18 sp, and secondary metadata is at least 16 sp.

**The Wrap, Don't Truncate Rule.** A long display title steps down to Display Compact on two lines before anything is cut.

**The Name Once Rule.** The Chaîne is named once, in the metadata line under the title, never as an eyebrow above it and never repeated on the card.

## Layout

**Canvas.** 960x540 dp. The overscan-safe insets are 5 %: 48 dp horizontal (safe-horizontal) and 27 dp vertical (safe-vertical).

**Columns.** A collapsed nav rail (72 dp) is pinned at the left over the content. The content column starts at content-start minus gutter (104 − 20 = 84 dp) and clips there. Rail titles, cards and detail text all align at 104 dp because they carry a 20 dp start padding (gutter) inside the column. The gutter equals the card gap (20 dp), so a card that scrolls off sits fully under the clip edge.

**Detail panel.** Top 252 dp of the screen (about 47 %). Text block is 540 dp wide, starting 27 + 8 dp from the top. Title, then metadata (4 dp above), then synopsis (6 dp above) or remaining time and progress, then the button row (16 dp above, 12 dp between buttons). The stale notice and the update chip stack at the top right, inside the 48 dp safe edge.

**Backdrop.** The 16:9 key art is 640 dp wide (360 dp tall), anchored top right, and crossfades with focus.

**Rails.** A vertical list with 8 dp between rails and a 27 dp bottom inset. Each rail has a title (4 dp top) over a horizontal row with 14 dp top and bottom padding. That padding leaves room for the scale and the ring. The row has a 20 dp start, a 48 dp end and a 20 dp card gap. The next card peeks at the right edge.

**Pivots (measured on the Bbox).**
- Column pivot: 0.16 of the column height. The focused rail lands about 45 dp below the top, so its rail title stays visible.
- Row pivot: 0.015 of the ~876 dp row. tv-foundation positions the *scaled* (1.1) card. At 0.015, the card at rest sits about 19 dp inside the row, and the previous card ends under the column clip at 84 dp. A value of 0.021 left an 8 px sliver of the previous card. Do not raise it.

**Responsive.** A single fixed TV canvas. No breakpoints.

### Named Rules
**The Clean Edge Rule.** The gutter equals the card gap (20 dp), and the row pivot is 0.015. No sliver of a previous card shows at the left edge.

**The Overscan Rule.** Every text and focusable element sits within 48 dp horizontal and 27 dp vertical of the screen edge. Only the backdrop art bleeds.

## Elevation & Depth

The system is flat. No shadows are used anywhere. Depth comes from three things. Tonal steps take the ground to surface, to raised surface, to track. Gradient scrims separate text from key art. Focus scale lifts the focused element.

### Scrims
- **Horizontal scrim** over the backdrop: ground at 100 % (0) → 92 % (0.3) → 60 % (0.55) → 10 % (0.85) → transparent (1).
- **Vertical scrim** over the backdrop: transparent (0) → ground at 35 % (0.55) → ground at 100 % (1). The art dissolves into the rails.
- **Nav dim:** ground at 72 % over the whole content while the rail is expanded, animated in 200 ms.
- **Dialog dim:** the platform Dialog dim behind the remove dialog.

### Named Rules
**The Scrim, Not Shadow Rule.** Separate layers with ground-colored gradients and tonal fills. A drop shadow never appears.

**The Lift Is Focus Rule.** Only focus changes an element's scale: 1.1 for cards and 1.06 for buttons, in 200 ms.

## Shapes

Soft, consistent corners in the 8 to 12 dp band. Cards use 10 dp (card), panel buttons 8 dp (button), and nav items, chips, the stale pill, progress bars and skeleton title bars are fully rounded (pill). Card art clips to the card shape. The progress bar on an « À suivre » card runs flush along the bottom edge inside that clip.

### Named Rules
**The Concentric Ring Rule.** The focus ring is drawn outside the art, never over it. It has a 3 dp white stroke and sits 3 dp beyond the card edge. Its corner radius is the card radius plus its reach (10 dp + 4.5 dp), so ring and card stay concentric.

## Components

### Buttons (panel button)
Quiet at rest, unmistakable on focus.
- **Shape:** gently rounded (8 dp), 44 dp tall, 20 dp horizontal padding.
- **Rest:** raised slate fill, Paper White Body Strong label. Optional leading play icon (24 dp, 8 dp gap) on the play actions only ("Reprendre", "Lecture", "Lire maintenant").
- **Focus:** white fill, ink label and icon, scale 1.06 in 200 ms.
- **Variants:** none. Dialog actions ("Marquer comme vu", "Retirer d'À suivre") use the same style as "Annuler". Initial focus goes to "Annuler".

### Cards
- **Poster card (Programme):** 3:4, 116 dp wide, 10 dp corners, no caption. The detail panel names the focused item.
- **« Tout voir » card:** ends a Home Genre rail only when the rail is truncated (more Programmes than its 40 cards); a complete rail has none. Same size as a poster (116 dp, 3:4, 10 dp corners), Raised Slate plate, « Tout voir » centred in Card Title, Paper White. Card focus. While focused, the detail panel shows the Genre name alone. OK opens the « Tout voir » grid of that Genre.
- **« À suivre » card (Épisode):** 16:9 thumbnail, 176 dp wide, 10 dp corners. Below it, 14 dp down: the Programme title (Card Title, Paper White, 1 line), then one line. It shows no Chaîne. One card per Programme.
  - **In progress:** a 4 dp progress bar along the bottom edge (amber over track) and the remaining time in words (Meta, Mist). When the Épisode ends within 7 days, a freshness pill (« Dernier jour », « Plus qu'un jour », « Plus que N jours ») replaces the remaining-time line, 2 dp below the title, never next to it; the bar stays. « Hier soir » and « Nouveau » never appear on these cards.
  - **New Épisode of a pinned Programme:** no bar. Its freshness pill (« Hier soir », « Nouveau » or the expiry, the one `FreshnessPolicy` picks), else its label in Meta Mist: « S2 E4 · Titre » with the numbers the Chaîne gives (the title alone when it already reads « S02 E04 » or a number is missing).
- **Focus:** scale 1.1 in 200 ms plus the concentric white ring (see Shapes).
- **Missing art:** a flat raised-slate plate with the title in Card Title, bottom-left, 12 dp inset, max 3 lines.
- **Options key** on an « À suivre » card opens its options dialog (see Options dialogs).

### « À suivre » rail
The first rail of Home, replacing « Reprendre ».
- **Content:** the Épisodes in progress (one per Programme, the « Reprendre » rule), plus for each pinned Programme without one its next Épisode: for a series, the Épisode after the last one watched (95 % or marked watched), only once one was watched, across Saisons; for a magazine, the newest unwatched Épisode broadcast 7 days ago or less. Expired Épisodes never appear; a dismissed Épisode stays off until its Programme offers another one.
- **Order:** the most recent event first: last playback for an Épisode in progress, broadcast (else publication) for a new one. The order only changes when the data does, never by algorithm.
- **OK:** an Épisode in progress resumes at once; a new Épisode plays from the start, both without the Reprendre / Débuter prompt.
- **Freshness:** each time Home opens, pinned Programmes whose Épisodes are older than 12 h refetch in the background, three at a time. The rail updates in place without moving focus; a failure keeps the cache and shows nothing.
- **Focus line:** the card keeps its focus when its content changes (one key per Programme, so an Épisode in progress that becomes the next one stays focused); after a dialog action takes the card off, focus returns to the billboard.

### Detail panel (billboard)
The top of Home always describes something. On launch it shows the first « À suivre » item with its primary action focused, plus "Épisodes": "Reprendre" (play icon) then the remaining time and a 120 x 4 dp progress bar for an Épisode in progress; "Lecture" (play icon) for a new Épisode, whose metadata line reads « Chaîne · S2 E4 · Titre » over its synopsis. With no « À suivre » item, it falls back to the most recently pinned favourite with a single "Épisodes" button. When a card has focus, the panel shows that card instead: the synopsis for a Programme, the remaining time and progress for an Épisode in progress, the label and synopsis for a new Épisode. In that state the panel has no buttons. With nothing to show, the panel is empty and focus starts on the first card.

### Progress bar
A 4 dp pill, amber fill over the track, always paired with the remaining time in words ("34 min restantes", "1 h 05 restantes", "Moins d'une minute"). Progression is never coded by the bar alone.

### Navigation (nav rail)
- **Collapsed (content focused):** 72 dp, on the ground, icons only, 24 dp. Four destinations in this order: Accueil (Home), Genres (Material « Category »: triangle, circle, square), Recherche (Search), Paramètres (Settings). Items are 48 dp pills with 12 dp inset and 8 dp spacing, starting 27 + 72 dp from the top.
- **Expanded (focus inside):** widens to 232 dp over a slate-surface panel in 200 ms, labels fade in ("Accueil", "Genres", "Recherche", "Paramètres", Body Strong, 16 dp after the icon), and the content dims to 72 %.
- **Selected:** raised-slate pill, amber icon, Paper White label.
- **Focused:** white pill, ink icon and label, scaled 1.06 over 200 ms (same lift as panel buttons), so focus never relies on color alone.
- **Entry:** focus entering the rail always lands on the current destination.
- **Mobile (`RailMetrics.mobile`):** items reach at least 48 real dp: 48 / canvas scale rounded up to 8 dp, icon half the item, rail = item + 2 × 12 dp, first item one item + 8 dp below the top (room for the back arrow), content-start = rail + 32 dp. Pixel 7 landscape (383 dp window, scale 0.709): 72 dp items (51 real dp), 36 dp icons, 96 dp rail, content at 128 dp.
- **Back (mobile, every screen but Home and the player):** a « Retour » arrow (system Back) in the slot above Accueil, same pill size, Mist icon on the ground, never selected. No pressed state (like the destinations, the screen change answers the tap) and no hairline: the 8 dp rail-stack gap and the glyph set it apart; the destinations never move for it. Expanded (D-pad on mobile): label « Retour », Body Strong Mist like an unselected destination; never the focus entry.

### Status line and chips
- **Stale notice:** Meta in Mist on an 85 % ground pill, 12 dp x 4 dp padding, top right. Copy: "Catalogue du 05/11 · TF1 injoignable" (plural "injoignables"). It is not focusable.
- **Update chip:** a focusable pill with 16 dp x 6 dp padding. It shows "Mise à jour %s prête" and then "Installer" in Medium, 12 dp apart. At rest it has a raised-slate fill. On focus it turns white with ink text and scales 1.06 over 200 ms, like panel buttons. It is never the initial focus.

### Options dialogs
The remote's Options key (MENU) opens a dialog on an « À suivre » card or on an Épisode row of the Programme page.
- **Panel:** 440 dp slate surface, 10 dp corners, 24 dp padding. Title in Rail Title (2 lines), an optional hint in Body Mist (2 lines), then the panel buttons 24 dp below, 12 dp apart.
- **Order and focus:** « Annuler » comes first and takes the initial focus. One action sits in a row next to it. Two or more actions do not fit the 392 dp row, so all the buttons stack full width.
- **« À suivre » card:** title = the Programme, hint = the Épisode title. Buttons « Annuler », « Marquer comme vu », « Retirer d'À suivre ». Both actions take the card off the rail (a pinned series then offers its following Épisode). « Retirer d'À suivre » forgets the Progression of an Épisode in progress, and on any card records the Épisode as dismissed for its Programme (stored on the box), so the same Épisode is not offered again; a newer one is. Focus then goes back to the billboard, as after a removal.
- **Exit dialog:** Back from the top of Home reuses the panel with no hint: title « Quitter Replaybox ? », buttons « Annuler » and « Quitter ».
- **Épisode row:** title = the Épisode. Buttons « Annuler » and « Marquer comme vu », or « Marquer comme non vu » on a watched row. Focus returns to the row.
- **Marquer comme vu** stores the Progression at 100 %. The Épisode leaves « À suivre » and its row reads « ✓ Vu » with the full Mist bar. **Marquer comme non vu** forgets the Progression. There is no Saison-wide marking.

### Freshness pill
At most one per Épisode (`FreshnessPolicy`): « Dernier jour », « Plus qu'un jour » / « Plus que N jours » (7 days at most), « Hier soir » (broadcast yesterday from 18:00), « Nouveau » (online less than 48 h).
- **Look:** Meta in Mist on a Raised Slate pill, 10 x 2 dp padding. Never amber, never an alert colour: the One Amber Rule holds.
- **On a focused Épisode row:** the row plate is Raised Slate too, so the pill fill steps down to Slate Surface.
- **Where:** at the end of the Épisode row's meta line, 12 dp after the text, and on « À suivre » cards (expiry only on an Épisode in progress, see Cards). Nowhere else: not on posters, the billboard or the detail panel.
- **Watched rows:** no pill. « ✓ Vu » already says enough.

### Expired Épisodes
An Épisode whose known end of availability has passed leaves « À suivre » and the billboard. Its Progression stays stored. It is not listed on the Programme page either, even while the 12 h cache still holds it. An Épisode with no known end (M6+, or missing from the cache) never counts as expired.

### Skeleton
Shown only on a cold first launch with an empty cache. It uses the same geometry as real rails: a 16 dp pill title bar, then a row of slate-surface cards. The first rail holds 16:9 cards at 176 dp, the second 3:4 cards at 116 dp. There is no spinner and no shimmer.

### Copy
French, tutoiement, glossary terms capitalized (Chaîne, Programme, Saison, Épisode, Flux, Progression, Genre). Time is written in words. Sources and dates are stated plainly.

### Programme page
The same world as Home, one level down. Verified on the Bbox (2026-11-06).

**Hero.**
- Key art 640 dp at the top right, with the Home scrims.
- Title in Display (stepping to Display Compact on two lines), then « Chaîne · Genre » in Body Strong Mist, then a synopsis of up to 3 lines in Body Mist.
- Two panel buttons:
  - the primary, with the play icon, follows a cascade: « Reprendre » on the newest unfinished Épisode of this Programme; else « Lecture » on the next unwatched Épisode after the last watched one (95 % or marked watched), across Saisons in their number order; else « Lecture » on the first Épisode of the first Saison for a series, the newest Épisode for a magazine;
  - the secondary is « Épingler » / « Épinglé », with no icon.
- 14 dp below the buttons, a line names what the primary plays: a 120 dp progress bar plus « Épisode · 3 min restantes », or « S2 E4 · Titre » with the numbers the Chaîne gives (the title alone when it already carries them or one is missing).
- Initial focus goes to the primary.

**Reprendre plays at once.** A « Reprendre » action (billboard, « À suivre » card, Programme hero) resumes without the player's Reprendre / Débuter prompt. Opening an Épisode row keeps the prompt, with the position written as the player clock (« 0:45 »). « Lecture » on the hero plays from the start.

**Saisons.** Pills only when there is more than one Saison; otherwise an « Épisodes » Rail Title.
- Focused: white fill with a 1.06 lift.
- Selected: raised slate. On opening, the selected Saison is the one holding the primary's target, not necessarily the first listed.
- Other: Mist with no fill.
- Watched: a Saison whose listed Épisodes are all watched carries a 20 dp check 8 dp before its label, in the label colour (shape, never colour alone). Nothing else changes on the rows.
- Selection happens on OK, never on focus, so crossing the pills fires no request. Focus stays on the pill while its Épisodes load.

**Épisode row.** A 208 dp 16:9 thumbnail at 104 dp, then 32 dp of space, then:
- the title in Body Strong (2 lines);
- the meta line in Meta Mist: « 02/11/2026 · 4 min », plus « · 3 min restantes » or « · ✓ Vu », then the freshness pill when there is one;
- the synopsis in Body Mist (2 lines).

The Options key on a row opens its options dialog (« Marquer comme vu » / « Marquer comme non vu »).

Duration is rounded up, so 3 min 45 s reads « 4 min ».

**Progression on a row.** In progress: an amber bar along the thumbnail plus the remaining time in words. Watched (95 % or more): a full bar in Mist plus a check and « Vu ». Never amber, never dimmed.

**Row focus (second focus idiom).**
- The thumbnail takes the card ring and the 1.1 lift.
- The whole row sits on a raised-slate plate: 32 dp inset, 24 dp vertical padding, 26 dp corners concentric with the ring. The ring stays at least 10 dp inside the plate.
- The plate ends at the 48 dp safe edge.

**Scrolling.**
- Column pivot 0.45, so the hero stays whole while its buttons are focused.
- Once the list scrolls, the key art sinks under 92 % ground (300 ms), so rows never sit on unscrimmed art.
- A 64 dp top fade (solid to about 40 dp) dissolves the cut line.
- Back from the list returns to the hero; Back from the hero leaves.
- Returning from the player focuses the Épisode just watched; after Épisode suivant moved on, the last one played, in its Saison. Opened from the hero, focus returns to the primary.

**States.** Loading shows 3 skeleton rows. An error reads « Impossible de charger les Épisodes. » with a focused « Réessayer ». Empty uses « Aucun Épisode disponible. ».

### Focus targets
Every D-pad target uses `tvClickable`: one focus node and no Material ripple. `focusable()` + `clickable()` made two nodes, so after OK the visible focus was lost and a grey ripple rectangle appeared.

### Search
The keyboard on the left, the results on the right. No system IME, no voice.

**Keyboard.** An in-app grid at 104 dp, 300 dp wide, top aligned with the query (27 + 8 dp).
- Six columns: A–Z, then 0–9 (six rows), then « Espace » and « Effacer », each three columns wide. No accents: search is normalized (case and accents ignored).
- Keys are panel buttons with a centred Body Strong label: 44 dp tall, 8 dp corners, 8 dp apart. Rest: Raised Slate, Paper White label. Focus: white fill, ink label, 1.06 lift.
- OK types the letter (lower case). « Espace » never leads or doubles. « Effacer » deletes one character. The query stops at 40 characters.

**Results column.** From 440 dp (the keyboard plus 36 dp) to the 48 dp safe edge, 472 dp wide. One scrolling grid, top to bottom:
- **Query:** the typed text in Display Compact, Paper White, two lines before the ellipsis. Empty: « Titre du Programme… » in Mist.
- **Chips:** 16 dp below, the Chaîne-group row (« Tout », « TF1 », « France TV », « M6+ », M6+ only with an account); 8 dp below, the Genre row (« Tout », then the 9 Genres in glossary order), which scrolls sideways. Both are the « Tout voir » chips: one choice per row, selected on OK and never on focus, Raised Slate plus a check when selected. Filters apply before the title fusion, so a Chaîne group keeps its own copy of a Programme another group also lists.
- **Best result:** when the top-ranked result's title equals the query or starts with it (case and accents ignored). A 208 dp 16:9 key art (10 dp corners; the poster cropped when the Chaîne ships no key art), then 20 dp to the right: the title in Rail Title (2 lines), « Chaîne · Genre » in Meta Mist, and with an Épisode in progress, « Épisode · 34 min restantes » in Meta Mist. Below, 16 dp down, panel buttons: « Reprendre » (play icon, resumes at once like the Home « Reprendre ») then « Épisodes » (opens the Programme), or « Épisodes » alone. Otherwise there is no highlight. The best result is not repeated in the grid.
- **Grid:** four 3:4 posters per row (about 103 dp), 20 dp gap, 28 dp between rows, the Home card focus and the shared caption (`PosterCaptionCard`: title in Card Title on 2 lines, « Chaîne · Genre » in Meta Mist). The Chaîne comes from the cached listings, or the Provider that returned the result; it is never guessed.

**When it searches.** From the first character (local search is instant, 150 ms pause between keys). TF1's remote search, used only while its Index is not stored, waits for 3 characters. An empty query with a chip selected lists the cached Programmes of that filter in the « Tout voir » order (recency, then title), with no best result. An empty query with no chip selected shows the hint. While the next query runs, the previous results stay on screen.

**States.** Body Mist at the top of the grid:
- empty query, no filter: « Tape le titre d'un Programme. »;
- loading: « Chargement… », only after 500 ms, never « Aucun Programme trouvé. »;
- nothing found: « Aucun Programme trouvé. »;
- every Provider failing: « Recherche indisponible. » with « Réessayer » 20 dp below. One Provider failing shows the others' results as they are.

**Focus line.**
- Initial focus: « A ».
- RIGHT from the keyboard's last column (F, L, R, X, 3, 9, « Effacer ») enters the results: on the element focused there last, when the query has not changed since; else the best result's first button; else the first poster; else « Réessayer »; else the selected Chaîne chip.
- LEFT from the first element of a row (first chip of each row, the best result's first button, the first poster of each grid row) returns to the key focused last.
- DOWN: Chaîne chips, Genre chips, best result, grid. From the best result DOWN lands on the first poster; from the first grid row UP lands on the best result. Moves never leave the keyboard by its top, bottom or left edge.
- Back from the results column returns to the key focused last; Back from the keyboard leaves Search.
- Typing scrolls the results back to the top.
- On return from a Programme page or the player: query, chips, results, scroll and the focused element (key, chip, button or poster) are restored. « Reprendre » gone after the Épisode was finished: focus is on « Épisodes ».

### Player
The video owns the screen. Every overlay sits at the bottom over a ground scrim (transparent to 92–94 % ground), inside the 48 dp safe frame, and one Back closes it.
- **Keys with no row button focused (controls hidden or shown).** Left/Right seek 10 s and show the controls, OK plays or pauses, Up/Down show the controls, CC toggles subtitles, MENU opens Options. Once they show, Down enters the control row and Up leaves it.
- **Controls.** Programme title in Rail Title, Épisode title in Meta Mist, then the timeline: tabular clocks either side of a 6 dp track with the amber Progression fill (One Amber Rule). Below, panel buttons: −10 s, +10 s, Recommencer, Sous-titres (check or cross: state by shape), Options, starting on the safe edge under the title and timeline, 12 dp apart. No button has focus when they appear: all rest in raised slate, no lift, and OK plays or pauses; Down focuses « −10 s ». They hide 5 s after the last key while playing and stay while paused.
  Mobile: the timeline takes a drag (fill, left clock and a time bubble follow the finger; one seek on release) and a tap (seeks there); its touch area is 48 dp in physical size. The box timeline is display only. The thumb is a 16 dp amber disc, no ring: the head of the Progression fill (One Amber Rule), never lifted. The bubble is the position in Meta tnum, Paper White on a Raised Slate pill (12 x 4 dp), 8 dp above the thumb, centred on it but held within the bar's span, so inside the safe frame at both ends; it shows only while a drag holds. The touch area covers the 14 dp and 16 dp gaps above and below the timeline as far as it reaches, never the buttons. A drag holds the controls; its release restarts the 5 s.
- **Back arrow (mobile).** With the controls, while the Flux resolves (over the loading ring) and on the error panel, top left inside the safe frame: the rail's back arrow at the rail item size, Paper White on a ground 60 % circle (the seek flash backing), one tap leaves, no pressed state. Never over Options, the resume prompt or Épisode suivant; absent on the box.
- **Centre glyph.** While paused (not at the end, no resume prompt, Épisode suivant or Options), a pause glyph stays centred; on resume a play glyph fades out in ~600 ms. It replaces the loading ring while paused. A call holding playback silent reads paused. The glyph is Paper White at the ring's 48 dp on an 80 dp ground 60 % disc (the seek flash backing), clear of the controls panel; the pause glyph appears at once, the play glyph starts opaque and fades on the standard curve, no scale, no bounce. Decorative: no label, no pointer input (taps reach the video), under the overlays and the back arrow. Box and mobile alike.
- **Options panel.** Takes the controls' place. Title « Options », then one row per setting (Audio, Sous-titres, Taille du texte and Fond du texte when the Flux has subtitles, Qualité, Vitesse): label in Body Strong Mist on a 180 dp column, choices as panel buttons with a check on the selected one. Rows come only from the tracks the player has; a row with nothing to choose is left out. Initial focus is the selected choice of the first row.
- **Captions.** While controls or Options are shown, the caption layer lifts (34 % and 62 % of its height) so no cue sits under an overlay.
- **Resume prompt, Épisode suivant, error.** Slate panels with panel buttons, bottom left for the prompt and bottom right for Épisode suivant. The error reuses Display Compact with a focused « Réessayer ».
- **Épisode suivant.** A 420 dp panel shown over the last 10 s of the video while it keeps playing underneath (no credit markers exist in the listings): « Épisode suivant » in Rail Title, the numbering in Meta Mist (« Saison 3 · Épisode 1 », only the numbers the Chaîne gives), the title in Body (2 lines), then « Lecture dans N s » in Meta Mist, counting the time left in the video (it holds while paused). Panel buttons « Lire maintenant » (play icon, initial focus) and « Annuler ». The controls and Options make way while it shows.
  - **Next:** the following Épisode of the numbered Saison in number order (Chaînes list the newest first), else the first Épisode of the next Saison number. Its Épisodes load while the current one plays, so the panel never waits. None after the last Saison, and none for an unnumbered magazine Saison.
  - **At 0** (the end of the video) or on « Lire maintenant », the next Épisode plays in the same player entry, and the one just left reads watched.
  - **« Annuler » or Back** hides the panel and lets the Épisode finish; its end then leaves the player for the screen that opened it (the Programme page, on the Épisode just watched). The same happens at the end of an Épisode with no next one.
  - **Seeking** back before the last 10 s hides the panel and forgets « Annuler »; a resume or a seek landing inside them shows it at once.
- **Loading.** One indicator everywhere a Flux loads: a 48 dp Paper White ring (4 dp stroke), centred, no text, shown only after 500 ms so quick loads never flash. It covers the Flux resolution on ground and buffering over the video, except while paused: the pause glyph holds the centre then (a paused viewer is not waiting).
- **Playback error.** The first error re-resolves the Flux silently and resumes at the last position; a second one in a row shows « Flux indisponible » / « La lecture s'est interrompue. » with « Réessayer », which resumes there.
- **Focus holder.** The idle focus target is a sibling of the overlays, never their parent: Compose maps Back to a focus exit, and a focusable parent swallows the first press.

### Failure states
A failure is never shown as an empty result, and every error screen has one focused way out. The complete list:
- **Catalogue (Home).** With a cache, Home keeps its rails and shows the dated stale notice (« Catalogue du 05/11 · TF1 injoignable »). Empty after a refresh where every Chaîne failed: « Catalogue indisponible. Vérifie la connexion. » with a focused « Réessayer ».
- **Search.** Every Provider failing: « Recherche indisponible. » with « Réessayer » (RIGHT from the keyboard reaches it). One Provider failing: the others' results as they are, no message.
- **Programme page.** « Impossible de charger les Épisodes. » with a focused « Réessayer »; « Aucun Épisode disponible. » when the list is empty.
- **Genres / Tout voir.** Read from the cache only, so no network error: an empty Genre or filter reads « Aucun Programme dans ce Genre. ».
- **Player.** The Flux resolution error and the second playback error in a row use one panel: title « Flux indisponible » in Display Compact, one Body Mist line, a focused « Réessayer ». The line names the cause when it is known: « TF1 exige désormais un compte connecté… » / « M6+ exige un compte connecté… » (both point to Paramètres), « Ce contenu reste indisponible avec ce compte (droits TF1 ou zone). », « Ce contenu est bloqué dans ta zone. », « La lecture s'est interrompue. », else « Impossible de résoudre le Flux pour cet Épisode. ».
- **Account form.** One Body Soft Coral line in French: wrong e-mail or password, account locked, connection refused (with the code), service not answering, no network.
- **À propos.** « Impossible de vérifier les mises à jour. Vérifie la connexion. »; the button stays focused for another try.
- **France TV size guard.** A response over its byte limit fails as a normal Provider error (the screens above), never as a crash.

### Focus checklist
Every screen's initial focus, Back target and focus on return, checked on the Bbox on 2026-11-07 unless marked « code » (read in the code, not triggered on the box: the player overlays below need a Flux error, a saved position to risk, or the last 10 s of an Épisode).

| Screen | Initial focus | Back | Focus on return |
|---|---|---|---|
| Home | Billboard primary (« Reprendre » / « Lecture »), else the first card | Scrolled down: back to the top; at the top: « Quitter Replaybox ? » | The card or button that opened the next screen |
| Nav rail | Current destination (entering it) | As Home (on Home); elsewhere on mobile, the screen's own Back | — |
| Genres | First tile (Séries) | Home | The tile that opened the grid |
| Tout voir | First poster (selected chip when empty) | Genres page (tile) or Home (« Tout voir » card) | The poster, filter and scroll |
| Programme page | Primary button | From the list: the hero; from the hero: leaves | The Épisode just watched |
| Épisode row dialog | « Annuler » | Closes, focus on the row | — |
| À suivre card dialog | « Annuler » | Closes, focus on the card | Billboard after the card leaves |
| Exit dialog | « Annuler » | Closes, focus back where it was | — |
| Player controls (code) | No button (OK plays or pauses; Down: « −10 s ») | Hides the controls | — |
| Options panel | Selected choice of the first row | Back to the controls | — |
| Resume prompt (code) | « Reprendre » | Leaves the player | — |
| Épisode suivant (code) | « Lire maintenant » | Hides the panel, the Épisode plays on | — |
| Player error (code) | « Réessayer » | Leaves the player | — |
| Search | Key « A » | From the results: last key; from the keyboard: leaves | Query, filters, scroll and the focused key or poster |
| Paramètres | First row (Sous-titres) | Home | The row that opened the page |
| Sous-titres | Saved size | Paramètres | — |
| Provider page | « Se connecter » | Paramètres | — |
| À propos | The single button | Paramètres | — |


### Paramètres
Verified on the Bbox (2026-11-07). One list, one level of pages; Back from a page lands on the row that opened it.

**List.** Title « Paramètres » in Display Compact at 104 dp, then 600 dp rows, 12 dp apart, in this order: « Sous-titres », the Providers (TF1+, France TV, M6+), then « À propos ». Five 72 dp rows end on the 27 dp bottom safe edge: a sixth row needs a scrolling list.
- **Row:** slate-surface plate with 10 dp corners, 24 x 12 dp padding. Title in Rail Title, one summary line in Meta Mist (ellipsis after one line). Provider rows end with the Santé badge, 16 dp after the text.
- **Focus:** raised-slate plate plus the white ring 3 dp outside (`focusRing`), no scale, since rows are wide.
- **Summaries:** « Grand · fond semi-opaque » for « Sous-titres »; « Connecté : <e-mail> », « Non connecté » or « Sans compte » for a Provider; « Version 0.4.0 » for « À propos », or « Version 0.4.0 · mise à jour 0.5.0 prête » once one is downloaded.
- **Initial focus:** the first row; after Back, the row that opened the page.

**Sous-titres.** Title in Display Compact, a 400 x 225 dp preview (10 dp corners), then two choice rows 14 dp apart: « Taille du texte » (Normal, Grand, Très grand) and « Fond du texte » (Contour, Semi-opaque, Opaque). Each row is a 180 dp Body Strong Mist label plus panel buttons; the saved choice carries a check, never colour alone. Initial focus: the saved size.
- **Preview:** the player's own Media3 SubtitleView over the key art of the latest Épisode in progress (a slate gradient without one), showing a two-line cue. It follows the focused choice; OK saves at once and the player follows live, even mid-Épisode.
- **Values:** text 5.33 %, 6.66 % and 8 % of the video height (Media3 default, x1.25, x1.5); band Night Ground at 70 % or 100 %; Contour draws a Night Ground outline with no band. Text is Paper White. Default: Grand on a semi-opaque band.
- **Override:** the setting replaces the system caption style and the colours and font sizes embedded in the cues (`setApplyEmbeddedStyles(false)`), so the three Providers look alike; cue positions are kept.

**Santé badge.** A 12 dp dot plus a Body Strong label, never color alone: filled Paper White « Fonctionne », filled Soft Coral « En panne », hollow Mist ring « État inconnu ». Shown in Paramètres only (list and Provider page), never on Home.

**Provider page.** Title (the Provider name) in Display Compact, the Santé badge 12 dp below, then two Body Mist lines in a 720 dp column: « Vérification de cette nuit : … » and « Sur cette box : … », dated « le 07/11 à 21:02 ». Then, 28 dp below, the account form when the Provider needs one. The page scrolls when the form and the keyboard need room.

**Account form.** Rail Title « Connexion <Provider> », the account state in Body Strong, a Meta Mist hint, two 540 x 52 dp fields (raised slate, 8 dp corners, ring on focus), then panel buttons « Se connecter » and, when signed in, « Se déconnecter ». The initial focus is « Se connecter », never a field, so reading the Santé does not raise the keyboard. UP/DOWN leave a field instead of moving its cursor. Errors read in Body Soft Coral, in French; the English exception text stays in the logs and the Santé line.

**À propos.** Title in Display Compact, the logo (see Logo), « Version X.Y.Z » in Body Strong, one status line in Body Mist, then one panel button, which is the initial focus:
- « Rechercher une mise à jour » runs the GitHub check; the status reads « Recherche en cours… », then « Tu as la dernière version. » or « Impossible de vérifier les mises à jour. Vérifie la connexion. ».
- Once a newer APK is downloaded, the status reads « La version X.Y.Z est prête à être installée. » and the button becomes « Installer ».
The Home update chip stays; « À propos » is where to look when it is not shown.

### Genres
The nav rail's « Genres » destination.
- **Layout:** title « Genres » in Display Compact at 104 dp, then 28 dp below, the 9 canonical Genres in glossary order (Séries, Films, Policier & Thriller / Comédie, Drame, Documentaire / Jeunesse, Divertissement, Sport & Info) as a 3 × 3 grid of text tiles, 20 dp apart, from 104 dp to the 48 dp safe edge (256 x 112 dp each).
- **Tile:** Raised Slate plate with 10 dp corners, the Genre name in Rail Title, Paper White, bottom left with a 20 x 16 dp inset. No image, no count.
- **Focus:** the card idiom, a 1.1 lift plus the concentric white ring 3 dp outside. The plate stays Raised Slate.
- **OK** opens the « Tout voir » grid of that Genre.
- **Focus line:** initial focus is the first tile (Séries) on each entry from the rail; Back goes to Home, on the card that had focus; on return from a grid, focus is on the tile that opened it.

### Tout voir
The grid of one Genre, opened from a Genres tile or from the « Tout voir » card of a Home rail.
- **Header:** the Genre name in Display Compact at 104 dp, then one row of Chaîne-group chips 16 dp below, 12 dp apart: « Tout », « TF1 », « France TV », « M6+ ». A group shows only when one of its Chaînes is listed, so « M6+ » appears only with an M6+ account. The header scrolls away with the grid.
- **Chips:** single choice, selected on OK, never on focus, so crossing the row reloads nothing (like the Saison pills). Selected: Raised Slate plus a 20 dp check before the label, never colour alone. Focused: white fill, ink label, 1.06 lift. Other: Mist label, no fill. 40 dp tall, 20 dp horizontal padding, Body Strong.
- **Grid:** six 3:4 posters per row, 20 dp gap, 28 dp between rows, between 104 dp and the 48 dp safe edge, with the Home card focus. Each poster has the Search caption: the title in Card Title (2 lines), then « Chaîne · Genre » in Meta Mist. One shared component (`PosterCaptionCard`) draws this grid and the Search grid (four per row there).
- **Content:** every Programme of the Genre on every listed Chaîne of every Provider, from the cached listings only (no crawl). France TV shows what its cache holds. The same title on two Chaînes is shown once, inside the selected filter.
- **Order:** most recent first, then title (case- and accent-insensitive). Recency is the latest sign of a new Épisode each Chaîne's own listing gives (`recencyAt`, measured 2026-11-07, SPEC per Provider): M6 dates every Programme by the publication of its latest full Épisode; France TV dates only the Programmes whose Épisode its Chaîne page features (about one in eight); TF1 gives no date, only a count of Épisodes aired in the last day or so, so a Programme with such a count is dated to that refresh. The box keeps the latest date it has seen, so a TF1 Programme stays dated by the last refresh that saw it recent. The day a Programme first appeared on the box (`firstSeenAt`) never ranks: on the Bbox a few Programmes listed the day before made almost the whole TF1 Index look new and the grid read alphabetically again (fixed and re-checked 2026-11-07). Only the last 30 days rank, by Paris calendar day (one refresh writes the Chaînes seconds apart, so the exact time is ignored); within a day, and for all older or undated Programmes after them, the title decides. Old M6 dates do not outrank undated France TV and TF1 Programmes: that would bury the Chaînes that date less. On a fresh install the grid opens on the Programmes with an Épisode from the last 30 days, then lists the rest alphabetically. The Home rails keep their own order (first-seen, Chaîne by Chaîne).
- **States:** loading shows two rows of skeleton posters (slate surface, with a caption bar), no spinner. Empty reads « Aucun Programme dans ce Genre. » in Body Mist at 104 dp.
- **Focus line:** initial focus is the first poster (the selected chip when the grid is empty). Back goes to where the grid was opened: the Genres page, on the tile, or Home, on the « Tout voir » card. On return from a Programme page, the filter, scroll and focused poster (or chip) are restored.

### Logo and launcher banner
Chosen 2026-11-07 (piste A « Reprise »), verified in the Bbox launcher (« Mes services » row).
- **Symbol:** an amber (Progression Amber) replay arrow, counterclockwise with its gap at the top and its head at the top right, around a Paper White play triangle. The logo is the one sanctioned use of amber outside Progression and the current destination: amber means « reprendre ».
- **Wordmark:** « Replaybox » in Manrope ExtraBold (SIL OFL 1.1), vectorised, Paper White, -0.6 tracking.
- **Banner** (`android:banner`, 320 x 180 dp): Night Ground plate, symbol at left (centre 74,90, radius 30, 8 dp stroke), wordmark from x 124 on a 101 dp baseline.
- **Icon** (`android:icon`, adaptive): Night Ground background, the symbol alone centred in the 66 dp safe zone.
- **Source:** `scripts/logo/make_logo.py` regenerates both vectors from the Manrope variable font (instanced at weight 800); the font itself is not in the repo.
- **In the app:** only on « À propos », 240 x 90 dp (banner cropped top and bottom, pulled 30 dp left so the symbol sits on the 104 dp text column). No splash screen, no logo in the nav rail.

## Do's and Don'ts

### Do:
- **Do** pair every focus state with a non-color cue: scale 1.1 plus the 3 dp ring for cards, scale 1.06 plus the white fill for buttons.
- **Do** keep the gutter equal to the card gap (20 dp) and the row pivot at 0.015, so no previous card shows a sliver.
- **Do** write remaining time in words next to every progress bar.
- **Do** step a long title down to Display Compact on two lines before truncating.
- **Do** show stale data with its date in one Meta line, and use skeleton cards (not a spinner) on cold start.
- **Do** keep text and focusables inside the 48 dp x 27 dp safe frame.
- **Do** run every animation through Compose tweens (200 ms focus, 300 ms backdrop), so the system "Supprimer les animations" setting makes them instant.

### Don't:
- **Don't** use amber for anything but Progression and the current navigation destination.
- **Don't** draw the focus ring over the artwork or the progress bar.
- **Don't** use drop shadows. Use scrims and tonal steps.
- **Don't** use pure black (#000) or text below 14 sp.
- **Don't** put the Chaîne or any label above the title as an eyebrow.
- **Don't** rotate the billboard, autoplay trailers or sound, or reorder rails by algorithm.
- **Don't** stack badges on cards or add a "Zéro pub" claim.
