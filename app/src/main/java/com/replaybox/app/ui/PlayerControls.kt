package com.replaybox.app.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.replaybox.R

/**
 * Remote keys over the player (decisions Q1, Q28). While the controls are
 * hidden the arrows seek, OK plays or pauses and other D-pad keys reveal the
 * controls; once shown, the D-pad belongs to the buttons. CC toggles the
 * subtitles and MENU opens Options (Q2) either way. Media keys stay with the
 * MediaSession.
 */
object PlayerKeys {
    enum class Action { NONE, SHOW, SEEK_BACK, SEEK_FORWARD, PLAY_PAUSE, TOGGLE_SUBTITLES, OPTIONS }

    fun action(keyCode: Int, controlsVisible: Boolean): Action {
        if (keyCode == KeyEvent.KEYCODE_CAPTIONS) return Action.TOGGLE_SUBTITLES
        if (keyCode == KeyEvent.KEYCODE_MENU) return Action.OPTIONS
        if (controlsVisible) return Action.NONE
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> Action.SEEK_BACK
            KeyEvent.KEYCODE_DPAD_RIGHT -> Action.SEEK_FORWARD
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> Action.PLAY_PAUSE
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> Action.SHOW
            else -> Action.NONE
        }
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
 * Player controls on the design system (Q26–Q30): title, timeline, then
 * Pause/Lecture (initial focus), −10 s, +10 s, Recommencer, Sous-titres,
 * Options (Q2).
 */
@Composable
internal fun PlayerControls(
    showTitle: String?,
    episodeTitle: String,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    subtitlesOn: Boolean?,
    playRequester: FocusRequester,
    onInteract: () -> Unit,
    onPlayPause: () -> Unit,
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
            Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PanelButton(
                label = stringResource(if (isPlaying) R.string.control_pause else R.string.action_play),
                icon = if (isPlaying) PauseIcon else Icons.Filled.PlayArrow,
                requester = playRequester,
                onFocused = onInteract,
                onClick = { onInteract(); onPlayPause() },
            )
            PanelButton(
                label = stringResource(R.string.control_back10),
                icon = null,
                requester = remember { FocusRequester() },
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
