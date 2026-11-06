package com.replaybox.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.replaybox.R

// Building blocks shared by Home and the Programme page (DESIGN.md components).

/** Inner gutter between the content column edge and the rails, wide enough that
 * a focused card's scale and ring are never clipped by its row. */
internal val GUTTER = 20.dp

/** Remaining time in words ("34 min restantes"); null when the duration is unknown. */
@Composable
internal fun remainingText(remainingSeconds: Long?): String? {
    val seconds = remainingSeconds ?: return null
    return when {
        seconds < 60 -> stringResource(R.string.remaining_under_minute)
        seconds < 3600 -> (seconds / 60).toInt().let { pluralStringResource(R.plurals.remaining_minutes, it, it) }
        else -> stringResource(R.string.remaining_hours, (seconds / 3600).toInt(), ((seconds % 3600) / 60).toInt())
    }
}

/** 4 dp Progression pill: [fill] over the track. Never shown without words beside it. */
@Composable
internal fun ProgressBar(ratio: Float, width: Dp, modifier: Modifier = Modifier, fill: Color = Rb.accent) {
    Box(
        modifier
            .width(width)
            .height(4.dp)
            .background(Rb.track, Rb.pillShape),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(ratio)
                .background(fill, Rb.pillShape),
        )
    }
}

/** Key art anchored top right with the scrims that keep text beside it at AAA. */
@Composable
internal fun Backdrop(url: String?, width: Dp = 640.dp) {
    Box(Modifier.fillMaxSize()) {
        Crossfade(
            targetState = url,
            animationSpec = tween(Rb.BACKDROP_MS),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(width)
                .aspectRatio(16f / 9f),
            label = "backdrop",
        ) { target ->
            if (target != null) {
                AsyncImage(
                    model = target,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Scrims keep the detail text at AAA contrast over any key art.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .width(width)
                .aspectRatio(16f / 9f)
                .background(
                    Brush.horizontalGradient(
                        0f to Rb.ground,
                        0.3f to Rb.ground.copy(alpha = 0.92f),
                        0.55f to Rb.ground.copy(alpha = 0.6f),
                        0.85f to Rb.ground.copy(alpha = 0.1f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .width(width)
                .aspectRatio(16f / 9f)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Rb.ground.copy(alpha = 0.35f),
                        1f to Rb.ground,
                    ),
                ),
        )
    }
}

/** Panel button: raised slate at rest, white fill and 1.06 lift on focus. */
@Composable
internal fun PanelButton(
    label: String,
    icon: ImageVector?,
    requester: FocusRequester,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) Rb.BUTTON_FOCUS_SCALE else 1f, tween(Rb.FOCUS_MS), label = "btn")
    Row(
        modifier = Modifier
            .scale(scale)
            .focusRequester(requester)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .tvClickable(onClick)
            .background(if (isFocused) Rb.focus else Rb.surfaceRaised, Rb.buttonShape)
            .height(44.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (isFocused) Rb.onFocus else Rb.text
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = RbType.bodyStrong, color = color, maxLines = 1)
    }
}

/**
 * D-pad target with exactly one focus node and no Material indication.
 * `focusable()` + `clickable()` made two nodes: OK moved focus to the inner
 * one, which onFocusChanged above it never saw (white fill lost) and whose
 * ripple drew a grey rectangle. Focus visuals are ours (DESIGN.md).
 */
internal fun Modifier.tvClickable(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
}

/** Card lift: 1.1 while focused (The Lift Is Focus Rule). */
@Composable
internal fun focusScale(focused: Boolean): Float {
    val scale by animateFloatAsState(if (focused) Rb.FOCUS_SCALE else 1f, tween(Rb.FOCUS_MS), label = "card")
    return scale
}

/**
 * Focus ring drawn outside the art with a gap (never over the artwork or the
 * Progression bar), then the art clipped to the card shape.
 */
internal fun Modifier.cardFocus(focused: Boolean): Modifier =
    this
        .focusRing(focused, CARD_RADIUS)
        .clip(Rb.cardShape)

/**
 * The white focus ring, 3 dp outside an element whose corner radius is
 * [radius], kept concentric (The Concentric Ring Rule). Draws nothing at rest.
 */
internal fun Modifier.focusRing(focused: Boolean, radius: Dp): Modifier =
    drawWithContent {
        drawContent()
        if (focused) {
            val stroke = Rb.focusOutline.toPx()
            val reach = FOCUS_GAP.toPx() + stroke / 2
            drawRoundRect(
                color = Rb.focus,
                topLeft = Offset(-reach, -reach),
                size = Size(size.width + 2 * reach, size.height + 2 * reach),
                cornerRadius = CornerRadius(radius.toPx() + reach),
                style = Stroke(width = stroke),
            )
        }
    }

private val FOCUS_GAP = 3.dp
private val CARD_RADIUS = 10.dp

/** Key art, or a flat plate carrying the title when the Chaîne ships none. */
@Composable
internal fun Artwork(url: String?, fallback: String) {
    if (url == null) {
        Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.BottomStart) {
            Text(fallback, style = RbType.cardTitle, color = Rb.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        return
    }
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(Rb.FOCUS_MS).build(),
        contentDescription = fallback,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
}
