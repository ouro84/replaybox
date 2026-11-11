package com.replaybox.app.ui

import android.view.KeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.replaybox.R

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
 * Options (Q2) whatever has focus. Media keys stay with the MediaSession.
 */
object PlayerKeys {
    enum class Focus { HIDDEN, CONTROLS, ROW, PANEL }
    enum class Action { NONE, SHOW, SEEK_BACK, SEEK_FORWARD, PLAY_PAUSE, ENTER_ROW, LEAVE_ROW, TOGGLE_SUBTITLES, OPTIONS }

    /** [focusInRow] is ignored once the controls are hidden: a stale value never counts. */
    fun focus(controlsVisible: Boolean, optionsOpen: Boolean, focusInRow: Boolean): Focus = when {
        optionsOpen -> Focus.PANEL
        !controlsVisible -> Focus.HIDDEN
        focusInRow -> Focus.ROW
        else -> Focus.CONTROLS
    }

    fun action(keyCode: Int, focus: Focus): Action {
        if (keyCode == KeyEvent.KEYCODE_CAPTIONS) return Action.TOGGLE_SUBTITLES
        if (keyCode == KeyEvent.KEYCODE_MENU) return Action.OPTIONS
        return when (focus) {
            Focus.PANEL -> Action.NONE
            Focus.ROW -> if (keyCode == KeyEvent.KEYCODE_DPAD_UP) Action.LEAVE_ROW else Action.NONE
            Focus.HIDDEN, Focus.CONTROLS -> when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> Action.SEEK_BACK
                KeyEvent.KEYCODE_DPAD_RIGHT -> Action.SEEK_FORWARD
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> Action.PLAY_PAUSE
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
 */
object PlayerTouch {
    enum class Tap { NONE, TOGGLE_PLAY, CLOSE_OPTIONS }
    enum class Seek { NONE, BACK, FORWARD }

    fun tap(optionsOpen: Boolean, prompt: Boolean): Tap = when {
        prompt -> Tap.NONE
        optionsOpen -> Tap.CLOSE_OPTIONS
        else -> Tap.TOGGLE_PLAY
    }

    fun doubleTap(x: Float, width: Float, optionsOpen: Boolean, prompt: Boolean): Seek = when {
        prompt || optionsOpen -> Seek.NONE
        x < width / 2 -> Seek.BACK
        else -> Seek.FORWARD
    }
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
 *   suivant or Options: those panels own the screen. A paused viewer is not waiting, so the
 *   pause glyph wins over the buffering ring;
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
        (!playWhenReady || callSilenced) && !ended && !nextUp && !optionsOpen -> Kind.PAUSED
        buffering -> Kind.LOADING
        else -> Kind.NONE
    }

    fun flashesPlay(before: Kind, after: Kind, playWhenReady: Boolean): Boolean =
        before == Kind.PAUSED && after == Kind.NONE && playWhenReady
}

/**
 * Player controls on the design system (Q26–Q30): title, timeline, then −10 s, +10 s,
 * Recommencer, Sous-titres, Options (Q2). No button has focus when they appear: OK plays
 * or pauses (B3). Down enters the row on « −10 s » ([rowRequester]); Up leaves it.
 * [onRowFocusChanged] reports whether a row button holds focus.
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            // Scrim, not shadow: the video fades into the ground under the controls.
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to Rb.ground.copy(alpha = 0.7f), 1f to Rb.ground.copy(alpha = 0.92f)))
            .padding(start = Rb.safeHorizontal, end = Rb.safeHorizontal, top = 72.dp, bottom = Rb.safeVertical),
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
        Timeline(positionMs, durationMs, Modifier.padding(top = 14.dp))
        Row(
            Modifier
                .padding(top = 16.dp)
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
 * the pause itself; the play glyph starts opaque and fades in [PLAY_FLASH_MS] on the standard
 * curve (slow start, so the resume registers, then a soft tail; no scale, no bounce). A Compose
 * tween, so « Supprimer les animations » makes it instant.
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
        flash.animateTo(0f, tween(PLAY_FLASH_MS, easing = FastOutSlowInEasing))
    }
    when (kind) {
        CentreGlyph.Kind.PAUSED -> CentreDisc(PauseIcon, modifier)
        CentreGlyph.Kind.LOADING -> LoadingRing(modifier)
        // Alpha read in the layer: the fade redraws without recomposing.
        CentreGlyph.Kind.NONE -> CentreDisc(Icons.Filled.PlayArrow, modifier.graphicsLayer { alpha = flash.value })
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

/** The play fade, as long as the seek flash. */
private const val PLAY_FLASH_MS = 600
