package com.replaybox.app.ui

import android.view.KeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.replaybox.R
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Remote keys over the player (decisions Q1, Q28; UX grilling 2026-11-11, B3). Four
 * focus states:
 * - [Focus.HIDDEN]: nothing shown;
 * - [Focus.CONTROLS]: controls shown, no row button focused;
 * - [Focus.ROW]: a button of the control row has focus;
 * - [Focus.PANEL]: Options is open.
 *
 * No row button has focus when the controls appear (B3): with nothing focused, the
 * arrows seek, OK plays or pauses, Up shows the controls and Down shows them, then
 * enters the row. In the row the D-pad belongs to the buttons, and Up leaves it. In
 * Options the D-pad belongs to the panel. CC toggles the subtitles and MENU opens
 * Options (Q2) whatever has focus. Media keys stay with the MediaSession. Holding OK
 * plays or pauses once: its repeats are swallowed ([Action.SWALLOW]).
 */
object PlayerKeys {
    enum class Focus { HIDDEN, CONTROLS, ROW, PANEL }
    /** [NONE] lets the key through; [SWALLOW] consumes it and does nothing. */
    enum class Action { NONE, SWALLOW, SHOW, SEEK_BACK, SEEK_FORWARD, PLAY_PAUSE, ENTER_ROW, LEAVE_ROW, TOGGLE_SUBTITLES, OPTIONS }

    /** [focusInRow] is ignored once the controls are hidden: a stale value never counts. */
    fun focus(controlsVisible: Boolean, optionsOpen: Boolean, focusInRow: Boolean): Focus = when {
        optionsOpen -> Focus.PANEL
        !controlsVisible -> Focus.HIDDEN
        focusInRow -> Focus.ROW
        else -> Focus.CONTROLS
    }

    /** [repeatCount] is the KeyDown repeat count: above 0 the key is held. */
    fun action(keyCode: Int, focus: Focus, repeatCount: Int = 0): Action {
        if (keyCode == KeyEvent.KEYCODE_CAPTIONS) return Action.TOGGLE_SUBTITLES
        if (keyCode == KeyEvent.KEYCODE_MENU) return Action.OPTIONS
        return when (focus) {
            Focus.PANEL -> Action.NONE
            Focus.ROW -> if (keyCode == KeyEvent.KEYCODE_DPAD_UP) Action.LEAVE_ROW else Action.NONE
            Focus.HIDDEN, Focus.CONTROLS -> when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> Action.SEEK_BACK
                KeyEvent.KEYCODE_DPAD_RIGHT -> Action.SEEK_FORWARD
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (repeatCount > 0) Action.SWALLOW else Action.PLAY_PAUSE
                KeyEvent.KEYCODE_DPAD_UP -> Action.SHOW
                KeyEvent.KEYCODE_DPAD_DOWN -> if (focus == Focus.HIDDEN) Action.SHOW else Action.ENTER_ROW
                else -> Action.NONE
            }
        }
    }
}

/**
 * Touch on the video, mobile only: a tap plays or pauses (UX grilling 2026-11-11, B1); no
 * gesture shows the controls without pausing. A double-tap on the left or right half seeks
 * −10 s / +10 s (milestone grilling 2026-11-10). [prompt] is « Reprendre / Débuter » or
 * Épisode suivant on screen: their buttons take the taps.
 *
 * A press is a tap only when still: it moved no more than [slopPx] (the touch slop) from
 * where it went down, [movedPx], and was held no longer than [longPressMs], [heldMs]. A
 * stray swipe, a thumb resting on the glass or the status bar pulled in immersive mode
 * never plays, pauses or seeks.
 */
object PlayerTouch {
    enum class Tap { NONE, TOGGLE_PLAY, CLOSE_OPTIONS }
    enum class Seek { NONE, BACK, FORWARD }

    fun tap(optionsOpen: Boolean, prompt: Boolean, movedPx: Float, heldMs: Long, slopPx: Float, longPressMs: Long): Tap = when {
        prompt || !still(movedPx, heldMs, slopPx, longPressMs) -> Tap.NONE
        optionsOpen -> Tap.CLOSE_OPTIONS
        else -> Tap.TOGGLE_PLAY
    }

    /** [movedPx] and [heldMs] are those of the second press. */
    fun doubleTap(
        x: Float,
        width: Float,
        optionsOpen: Boolean,
        prompt: Boolean,
        movedPx: Float,
        heldMs: Long,
        slopPx: Float,
        longPressMs: Long,
    ): Seek = when {
        prompt || optionsOpen || !still(movedPx, heldMs, slopPx, longPressMs) -> Seek.NONE
        x < width / 2 -> Seek.BACK
        else -> Seek.FORWARD
    }

    private fun still(movedPx: Float, heldMs: Long, slopPx: Float, longPressMs: Long): Boolean =
        movedPx <= slopPx && heldMs <= longPressMs
}

/**
 * Whether a pause reveals the player controls (grilling 2026-11-10, Q1), whatever paused
 * the player: OK, the media keys or HDMI-CEC through the MediaSession, the app going to
 * the background. It does not when:
 * - [playWhenReady] is true: playback resumed, the 5 s auto-hide applies;
 * - [endOfMedia]: the Épisode ended, Épisode suivant or leaving the player takes over;
 * - [resumePrompt]: the « Reprendre / Débuter » choice keeps the screen;
 * - [nextUp]: the Épisode suivant panel keeps the screen;
 * - [optionsOpen]: Options stays open, the controls show when it closes.
 */
object PauseReveal {
    fun shows(playWhenReady: Boolean, endOfMedia: Boolean, resumePrompt: Boolean, nextUp: Boolean, optionsOpen: Boolean): Boolean =
        !playWhenReady && !endOfMedia && !resumePrompt && !nextUp && !optionsOpen
}

/**
 * What the centre of the video shows (UX grilling 2026-11-11, B2), on the box and on mobile.
 * One centre slot:
 * - [Kind.NONE] under the resume prompt (« Reprendre / Débuter » keeps the screen, as before);
 * - [Kind.PAUSED] when paused: `!playWhenReady`, or held silent by a call (callSilenced,
 *   the meaning of paused in [PlayerAudio.playing]). `playWhenReady`, not `isPlaying`, so
 *   buffering after a resume never shows a pause glyph. Not at the end, nor under Épisode
 *   suivant or Options: those panels own the screen, and the centre stays empty;
 * - a paused viewer is not waiting, so never the ring while paused, under a panel or not
 *   (a quality or audio change in Options while paused rebuffers without it);
 * - [Kind.LOADING] while buffering with the intent to play: the loading ring, as before,
 *   also under Options or Épisode suivant;
 * - [Kind.NONE] otherwise.
 *
 * [flashesPlay]: a play glyph fades once only when the pause glyph goes straight to nothing
 * with `playWhenReady` true (a resume). Not when Options opens while paused, and not into
 * the ring.
 */
object CentreGlyph {
    enum class Kind { NONE, PAUSED, LOADING }

    fun of(
        playWhenReady: Boolean,
        callSilenced: Boolean,
        buffering: Boolean,
        ended: Boolean,
        resumePrompt: Boolean,
        nextUp: Boolean,
        optionsOpen: Boolean,
    ): Kind = when {
        resumePrompt -> Kind.NONE
        !playWhenReady || callSilenced -> if (ended || nextUp || optionsOpen) Kind.NONE else Kind.PAUSED
        buffering -> Kind.LOADING
        else -> Kind.NONE
    }

    fun flashesPlay(before: Kind, after: Kind, playWhenReady: Boolean): Boolean =
        before == Kind.PAUSED && after == Kind.NONE && playWhenReady
}

/**
 * The mobile timeline (UX grilling 2026-11-11, B4): a touch at x on a bar [width] px wide
 * names a position, and the thumb sits at the x of a position. Clamped to the bar at both
 * ends. The box timeline takes no touch and has no thumb.
 */
object TimelineScrub {
    /** Position under [x] on a bar [width] px wide; null when the duration or width is unknown. */
    fun positionAt(x: Float, width: Float, durationMs: Long): Long? {
        if (durationMs <= 0 || width <= 0f) return null
        return (x.coerceIn(0f, width).toDouble() / width * durationMs).roundToLong()
    }

    /** Thumb offset in px for [positionMs]; 0 when the duration is unknown. */
    fun xOf(positionMs: Long, durationMs: Long, width: Float): Float {
        if (durationMs <= 0) return 0f
        return (positionMs.coerceIn(0L, durationMs).toDouble() / durationMs * width).toFloat()
    }

    /**
     * What is left of a box [gap] (dp) next to the timeline once its [touch] area (dp) stands
     * centred around a [content] line (dp): the touch area covers part of the gap, never more.
     */
    fun gapOutside(gap: Float, touch: Float, content: Float): Float = maxOf(0f, gap - (touch - content) / 2)
}

/**
 * Player controls on the design system (Q26–Q30): title, timeline, then −10 s, +10 s,
 * Recommencer, Sous-titres, Options (Q2). No button has focus when they appear: OK plays
 * or pauses (B3). Down enters the row on « −10 s » ([rowRequester]); Up leaves it.
 * [onRowFocusChanged] reports whether a row button holds focus.
 * Mobile: the timeline takes a tap and a drag ([ScrubTimeline], B4); [onSeekTo] seeks there and
 * [onScrubbing] reports a drag in progress. Both are ignored on the box. Below the transparent
 * top of the scrim, the panel takes every tap on mobile: a near miss on the titles, the clocks
 * or the space beside the buttons never reaches the video (which would play or pause).
 */
@Composable
internal fun PlayerControls(
    showTitle: String?,
    episodeTitle: String,
    positionMs: Long,
    durationMs: Long,
    subtitlesOn: Boolean?,
    rowRequester: FocusRequester,
    onRowFocusChanged: (Boolean) -> Unit,
    onInteract: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onRestart: () -> Unit,
    onToggleSubtitles: () -> Unit,
    onOptions: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onScrubbing: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mobile = LocalMobile.current
    val scrub = if (mobile) scrubSpacing() else null
    Column(
        modifier
            .fillMaxWidth()
            // Scrim, not shadow: the video fades into the ground under the controls.
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to Rb.ground.copy(alpha = 0.7f), 1f to Rb.ground.copy(alpha = 0.92f)))
            .padding(top = 72.dp)
            // Mobile: stray taps stay in the panel, like the Options rows; the buttons and the
            // timeline take theirs first. The transparent top above stays video.
            .then(if (mobile) Modifier.pointerInput(Unit) { detectTapGestures { } } else Modifier)
            .padding(start = Rb.safeHorizontal, end = Rb.safeHorizontal, bottom = Rb.safeVertical),
    ) {
        Text(
            showTitle ?: episodeTitle,
            style = RbType.railTitle,
            color = Rb.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showTitle != null && episodeTitle.isNotBlank() && episodeTitle != showTitle) {
            Text(episodeTitle, style = RbType.meta, color = Rb.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (scrub != null) {
            ScrubTimeline(
                positionMs = positionMs,
                durationMs = durationMs,
                touch = scrub.touch,
                onSeekTo = onSeekTo,
                onScrubbing = onScrubbing,
                modifier = Modifier.padding(top = scrub.timelineGap),
            )
        } else {
            Timeline(positionMs, durationMs, Modifier.padding(top = 14.dp))
        }
        Row(
            Modifier
                .padding(top = scrub?.rowGap ?: 16.dp)
                .onFocusChanged { onRowFocusChanged(it.hasFocus) }
                .mobileHorizontalScroll(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PanelButton(
                label = stringResource(R.string.control_back10),
                icon = null,
                requester = rowRequester,
                onFocused = onInteract,
                onClick = { onInteract(); onSeekBack() },
            )
            PanelButton(
                label = stringResource(R.string.control_fwd10),
                icon = null,
                requester = remember { FocusRequester() },
                onFocused = onInteract,
                onClick = { onInteract(); onSeekForward() },
            )
            PanelButton(
                label = stringResource(R.string.control_restart),
                icon = Icons.Filled.Refresh,
                requester = remember { FocusRequester() },
                onFocused = onInteract,
                onClick = { onInteract(); onRestart() },
            )
            if (subtitlesOn != null) {
                PanelButton(
                    label = stringResource(R.string.subtitle_toggle),
                    // State by shape, not color: check when on, cross when off.
                    icon = if (subtitlesOn) Icons.Filled.Check else Icons.Filled.Close,
                    requester = remember { FocusRequester() },
                    onFocused = onInteract,
                    onClick = { onInteract(); onToggleSubtitles() },
                )
            }
            PanelButton(
                label = stringResource(R.string.options_title),
                icon = Icons.Filled.Settings,
                requester = remember { FocusRequester() },
                onFocused = onInteract,
                onClick = onOptions,
            )
        }
    }
}

/** Where the mobile timeline sits: its touch area, and the gaps above it and above the row. */
private class ScrubSpacing(val touch: Dp, val timelineGap: Dp, val rowGap: Dp)

/**
 * Mobile only: a touch area of 48 real dp around the timeline (B4). It covers part of the box's
 * 14 dp gap above the timeline and 16 dp gap above the row; whatever it leaves stays, so the
 * timeline never sits closer to the title or the buttons than on the box.
 */
@Composable
private fun scrubSpacing(): ScrubSpacing {
    val touch = ceil(CompactCanvas.canvasDp(RailMetrics.MIN_TOUCH_DP, true, LocalConfiguration.current.screenHeightDp)).dp
    val clockLine = with(LocalDensity.current) { RbType.meta.lineHeight.toDp() }
    return ScrubSpacing(
        touch = touch,
        timelineGap = TimelineScrub.gapOutside(14f, touch.value, clockLine.value).dp,
        rowGap = TimelineScrub.gapOutside(16f, touch.value, clockLine.value).dp,
    )
}

/** Position · bar · duration. The bar is the Progression, hence amber (One Amber Rule). */
@Composable
private fun Timeline(positionMs: Long, durationMs: Long, modifier: Modifier) {
    val ratio = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val clock = RbType.meta.copy(fontFeatureSettings = "tnum")
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(Playhead.format(positionMs), style = clock, color = Rb.text)
        Box(
            Modifier
                .padding(horizontal = 16.dp)
                .weight(1f)
                .height(6.dp)
                .background(Rb.track, Rb.pillShape),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(ratio).background(Rb.accent, Rb.pillShape))
        }
        Text(if (durationMs > 0) Playhead.format(durationMs) else "", style = clock, color = Rb.textSecondary)
    }
}

/**
 * The mobile timeline (B4): the box's position · bar · duration, plus a thumb, a tap that seeks
 * where it lands and a drag that seeks once, on release. While a drag holds, the fill, the thumb
 * and the left clock follow the finger and a time bubble names the position above the thumb.
 * [touch] is the height of the touch area, 48 real dp; the 16 dp either side of the bar take
 * touches too (they clamp to its ends).
 *
 * Design (impeccable, DESIGN.md « Player » → « Controls », 2026-11-11):
 * - thumb: a 16 dp amber disc, no ring. It is the head of the Progression fill, so the One
 *   Amber Rule holds; a ring would read as focus, and it never grows (only focus lifts);
 * - bubble: the position in Meta tnum, Paper White on a Raised Slate pill (12 x 4 dp); its
 *   bottom 8 dp above the thumb, centred on it but held inside the bar's span, hence inside
 *   the safe frame at both ends. Shown only while a drag holds.
 */
@Composable
private fun ScrubTimeline(
    positionMs: Long,
    durationMs: Long,
    touch: Dp,
    onSeekTo: (Long) -> Unit,
    onScrubbing: (Boolean) -> Unit,
    modifier: Modifier,
) {
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    // A drag holds the controls whether or not it names a position (unknown duration).
    var dragging by remember { mutableStateOf(false) }
    var barPx by remember { mutableStateOf(0f) }
    val duration by rememberUpdatedState(durationMs)
    val seekTo by rememberUpdatedState(onSeekTo)
    val scrubbing by rememberUpdatedState(onScrubbing)
    // Controls taken off mid-drag (Épisode suivant, a new Épisode): the drag ends with them,
    // or the auto-hide would stay held.
    DisposableEffect(Unit) {
        onDispose { if (dragging) scrubbing(false) }
    }
    val shown = scrubMs ?: positionMs
    val ratio = if (durationMs > 0) (shown.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val clock = RbType.meta.copy(fontFeatureSettings = "tnum")
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(Playhead.format(shown), style = clock, color = Rb.text)
        Box(
            Modifier
                .weight(1f)
                .height(touch)
                // Keyed on Unit, reading the duration live: a key change would cancel a drag
                // without onDragCancel and leave the controls held.
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            val inset = SCRUB_INSET.toPx()
                            TimelineScrub.positionAt(offset.x - inset, size.width - 2 * inset, duration)?.let(seekTo)
                        },
                    )
                }
                .pointerInput(Unit) {
                    fun at(x: Float): Long? {
                        val inset = SCRUB_INSET.toPx()
                        return TimelineScrub.positionAt(x - inset, size.width - 2 * inset, duration)
                    }
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            scrubMs = at(offset.x)
                            dragging = true
                            scrubbing(true)
                        },
                        onDragEnd = {
                            scrubMs?.let(seekTo)
                            scrubMs = null
                            dragging = false
                            scrubbing(false)
                        },
                        onDragCancel = {
                            scrubMs = null
                            dragging = false
                            scrubbing(false)
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            at(change.position.x)?.let { scrubMs = it }
                        },
                    )
                }
                .padding(horizontal = SCRUB_INSET)
                .onSizeChanged { barPx = it.width.toFloat() },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(6.dp).background(Rb.track, Rb.pillShape)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(ratio).background(Rb.accent, Rb.pillShape))
            }
            if (durationMs > 0) {
                Box(
                    Modifier
                        .offset { IntOffset((TimelineScrub.xOf(shown, durationMs, barPx) - SCRUB_THUMB.toPx() / 2).roundToInt(), 0) }
                        .size(SCRUB_THUMB)
                        .background(Rb.accent, CircleShape),
                )
            }
            val held = scrubMs
            if (held != null) {
                Text(
                    Playhead.format(held),
                    style = clock,
                    color = Rb.text,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .layout { measurable, constraints ->
                            val bubble = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                            val centre = TimelineScrub.xOf(held, duration, barPx)
                            val maxX = maxOf(0, constraints.maxWidth - bubble.width)
                            val x = (centre - bubble.width / 2f).roundToInt().coerceIn(0, maxX)
                            // Bar centre is the box centre; the bubble ends 8 dp over the thumb.
                            val y = constraints.maxHeight / 2 - (SCRUB_THUMB / 2 + SCRUB_BUBBLE_GAP).roundToPx() - bubble.height
                            layout(bubble.width, bubble.height) { bubble.place(x, y) }
                        }
                        .background(Rb.surfaceRaised, Rb.pillShape)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
        Text(if (durationMs > 0) Playhead.format(durationMs) else "", style = clock, color = Rb.textSecondary)
    }
}

/** The 16 dp between a clock and the bar, part of the touch area. */
private val SCRUB_INSET = 16.dp

/** Thumb diameter on the 6 dp bar. */
private val SCRUB_THUMB = 16.dp

/** Space between the thumb and the time bubble above it. */
private val SCRUB_BUBBLE_GAP = 8.dp

/** Pause glyph (two bars, 24 dp grid): material-icons-core ships no Pause. */
internal val PauseIcon: ImageVector by lazy {
    ImageVector.Builder(name = "Pause", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .path(fill = SolidColor(Color.Black)) {
            moveTo(6f, 5f); lineTo(10f, 5f); lineTo(10f, 19f); lineTo(6f, 19f); close()
            moveTo(14f, 5f); lineTo(18f, 5f); lineTo(18f, 19f); lineTo(14f, 19f); close()
        }
        .build()
}

/**
 * The centre of the video ([CentreGlyph], B2): [CentreGlyph.Kind.PAUSED] holds the pause glyph,
 * [CentreGlyph.Kind.LOADING] is the loading ring, and each [playFlash] increment fades a play
 * glyph out once. Decorative: the controls and the state of the video say it already, so no
 * content description, and no pointer input (taps reach the video underneath).
 *
 * Design (DESIGN.md, Player « Centre glyph »): a Paper White glyph at the ring's 48 dp on an
 * 80 dp ground 60 % disc, the backing of the seek flash and the back arrow, so it reads over a
 * white frame (Contrast Floor, ≥ 3:1) without a shadow. The pause glyph appears at once, like
 * the pause itself; the play glyph starts opaque and fades in [PLAYER_FLASH_MS] on FastOutSlowIn
 * (slow start, so the resume registers, then a soft tail; no scale, no bounce). A Compose
 * tween, so « Supprimer les animations » makes it instant. Anything else taking the centre ends
 * the fade at once, so a half-faded play glyph never returns after the ring.
 */
@Composable
internal fun PlayerCentre(kind: CentreGlyph.Kind, playFlash: Int, modifier: Modifier = Modifier) {
    val flash = remember { Animatable(0f) }
    LaunchedEffect(playFlash) {
        // A tick of 0 is no flash: also clears one cut short by a new Épisode.
        if (playFlash == 0) {
            flash.snapTo(0f)
            return@LaunchedEffect
        }
        flash.snapTo(1f)
        flash.animateTo(0f, tween(PLAYER_FLASH_MS, easing = FastOutSlowInEasing))
    }
    // The pause glyph or the ring takes the centre: a fade in flight stops there (snapTo
    // cancels the running animation).
    LaunchedEffect(kind) { if (kind != CentreGlyph.Kind.NONE) flash.snapTo(0f) }
    // Recomposes only when the flash starts or ends, not on every frame of the fade.
    val flashing by remember { derivedStateOf { flash.value > 0f } }
    when (kind) {
        CentreGlyph.Kind.PAUSED -> CentreDisc(PauseIcon, modifier)
        CentreGlyph.Kind.LOADING -> LoadingRing(modifier)
        // Alpha read in the layer: the fade redraws without recomposing. No flash, no disc.
        CentreGlyph.Kind.NONE -> if (flashing) CentreDisc(Icons.Filled.PlayArrow, modifier.graphicsLayer { alpha = flash.value })
    }
}

@Composable
private fun CentreDisc(glyph: ImageVector, modifier: Modifier) {
    Box(modifier.size(CENTRE_DISC).background(VideoScrim, CircleShape), contentAlignment = Alignment.Center) {
        Icon(glyph, contentDescription = null, tint = Rb.text, modifier = Modifier.size(CENTRE_GLYPH))
    }
}

/** The loading ring's footprint: one size for whatever holds the centre. */
private val CENTRE_GLYPH = 48.dp

/** Ground disc around the glyph: 16 dp of backing on each side. */
private val CENTRE_DISC = 80.dp

/** How long the player's flashes last: the seek flash shows, the play glyph fades. */
internal const val PLAYER_FLASH_MS = 600
