package com.replaybox.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.text.Cue
import androidx.media3.ui.SubtitleView
import coil.compose.AsyncImage
import com.replaybox.R
import com.replaybox.app.ReplayboxApplication

/**
 * Paramètres > Sous-titres: size and band, with a live preview drawn by the
 * player's own SubtitleView over a real key art. The preview follows the
 * focused choice; OK keeps it (saved at once, the player follows live).
 */
@Composable
fun SubtitleSettingsScreen() {
    val store = ReplayboxApplication.subtitleStyle
    val saved by store.style.collectAsState()
    var focusedSize by remember { mutableStateOf<SubtitleSize?>(null) }
    var focusedBackground by remember { mutableStateOf<SubtitleBackground?>(null) }
    val preview = SubtitleStyle(focusedSize ?: saved.size, focusedBackground ?: saved.background)
    var backdrop by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val channelIds = Providers.all.map { it.channel.id }
        backdrop = runCatching { ReplayboxApplication.catalog.continueWatching(channelIds, limit = 1) }
            .getOrNull()?.firstOrNull()?.let { it.backdropUrl ?: it.thumbnailUrl }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = Rb.contentStart, top = Rb.safeVertical + 8.dp, end = Rb.safeHorizontal),
    ) {
        Text(stringResource(R.string.settings_subtitles), style = RbType.displayCompact, color = Rb.text)
        Spacer(Modifier.size(16.dp))
        SubtitlePreview(preview, backdrop)
        Spacer(Modifier.size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ChoiceRow(
                title = stringResource(R.string.subtitle_size),
                choices = SubtitleSize.values().toList(),
                selected = saved.size,
                label = { sizeLabel(it) },
                initialFocus = true,
                onFocused = { focusedSize = it },
                onSelect = { store.set(saved.copy(size = it)) },
            )
            ChoiceRow(
                title = stringResource(R.string.subtitle_background),
                choices = SubtitleBackground.values().toList(),
                selected = saved.background,
                label = { backgroundLabel(it) },
                initialFocus = false,
                onFocused = { focusedBackground = it },
                onSelect = { store.set(saved.copy(background = it)) },
            )
        }
    }
}

/** A 16:9 still with one two-line cue, rendered exactly as in the player. */
@Composable
private fun SubtitlePreview(style: SubtitleStyle, backdrop: String?) {
    val text = stringResource(R.string.subtitle_preview_text)
    Box(
        Modifier
            .size(PREVIEW_WIDTH, PREVIEW_WIDTH * 9 / 16)
            .clip(Rb.cardShape)
            .background(Brush.verticalGradient(listOf(Rb.surfaceRaised, Rb.track))),
    ) {
        if (backdrop != null) {
            AsyncImage(model = backdrop, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context -> SubtitleView(context) },
            update = { view ->
                view.applyStyle(style)
                view.setCues(listOf(Cue.Builder().setText(text).build()))
            },
        )
    }
}

/** Label column plus panel buttons; the selected choice carries a check, never colour alone. */
@Composable
private fun <T> ChoiceRow(
    title: String,
    choices: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    initialFocus: Boolean,
    onFocused: (T?) -> Unit,
    onSelect: (T) -> Unit,
) {
    val initial = remember { FocusRequester() }
    if (initialFocus) LaunchedEffect(Unit) { runCatching { initial.requestFocus() } }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.onFocusChanged { if (!it.hasFocus) onFocused(null) },
    ) {
        Text(title, style = RbType.bodyStrong, color = Rb.textSecondary, modifier = Modifier.width(LABEL_WIDTH))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            choices.forEach { choice ->
                key(choice) {
                    PanelButton(
                        label = label(choice),
                        icon = if (choice == selected) Icons.Filled.Check else null,
                        requester = if (choice == selected) initial else remember { FocusRequester() },
                        onFocused = { onFocused(choice) },
                        onClick = { onSelect(choice) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun sizeLabel(size: SubtitleSize): String = stringResource(
    when (size) {
        SubtitleSize.NORMAL -> R.string.subtitle_size_normal
        SubtitleSize.LARGE -> R.string.subtitle_size_large
        SubtitleSize.XLARGE -> R.string.subtitle_size_xlarge
    },
)

@Composable
internal fun backgroundLabel(background: SubtitleBackground): String = stringResource(
    when (background) {
        SubtitleBackground.OUTLINE -> R.string.subtitle_bg_outline
        SubtitleBackground.SEMI -> R.string.subtitle_bg_semi
        SubtitleBackground.OPAQUE -> R.string.subtitle_bg_opaque
    },
)

private val PREVIEW_WIDTH = 400.dp
private val LABEL_WIDTH = 180.dp
