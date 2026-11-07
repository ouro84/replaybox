package com.replaybox.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.replaybox.R

enum class RailDestination { HOME, GENRES, SEARCH, SETTINGS }

/**
 * Left navigation rail (Google TV / Netflix pattern): icons only while the
 * content has focus, expanded with labels over a solid panel once focus
 * moves left into it. Entering always lands on the current destination.
 */
@OptIn(ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NavRail(
    current: RailDestination,
    onNavigate: (RailDestination) -> Unit,
    modifier: Modifier = Modifier,
    onExpandedChange: (Boolean) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(
        targetValue = if (expanded) Rb.railExpanded else Rb.railCollapsed,
        animationSpec = tween(Rb.FOCUS_MS),
        label = "railWidth",
    )
    val currentRequester = remember { FocusRequester() }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            .background(if (expanded) Rb.surface else Rb.ground)
            .onFocusChanged {
                if (expanded != it.hasFocus) onExpandedChange(it.hasFocus)
                expanded = it.hasFocus
            }
            .focusProperties { enter = { currentRequester } }
            .focusGroup()
            .padding(top = Rb.safeVertical + 72.dp, start = 12.dp, end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val items = listOf(
            Triple(RailDestination.HOME, Icons.Filled.Home, R.string.nav_home),
            Triple(RailDestination.GENRES, GenresIcon, R.string.nav_genres),
            Triple(RailDestination.SEARCH, Icons.Filled.Search, R.string.nav_search),
            Triple(RailDestination.SETTINGS, Icons.Filled.Settings, R.string.nav_settings),
        )
        for ((destination, icon, label) in items) {
            RailItem(
                icon = icon,
                label = stringResource(label),
                selected = destination == current,
                expanded = expanded,
                modifier = if (destination == current) Modifier.focusRequester(currentRequester) else Modifier,
                onClick = { onNavigate(destination) },
            )
        }
    }
}

@Composable
private fun RailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    expanded: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> Rb.focus
        selected -> Rb.surfaceRaised
        else -> null
    }
    val tint = when {
        focused -> Rb.onFocus
        selected -> Rb.accent
        else -> Rb.textSecondary
    }
    // Focus is never shown by color alone (PRODUCT.md): the white pill also lifts.
    val scale by animateFloatAsState(if (focused) Rb.BUTTON_FOCUS_SCALE else 1f, tween(Rb.FOCUS_MS), label = "railItem")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick)
            .then(if (background != null) Modifier.background(background, Rb.pillShape) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = if (expanded) null else label, tint = tint)
        }
        AnimatedVisibility(visible = expanded, enter = fadeIn(tween(Rb.FOCUS_MS)), exit = fadeOut(tween(100))) {
            Row {
                Spacer(Modifier.width(16.dp))
                Text(
                    text = label,
                    style = RbType.bodyStrong,
                    color = if (focused) Rb.onFocus else if (selected) Rb.text else Rb.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Material « Category » (filled): triangle, circle and square, the Material icon for
 * categories. Drawn from its path because the extended icon set is not a dependency.
 */
private val GenresIcon: ImageVector = materialIcon(name = "Filled.Category") {
    materialPath {
        moveTo(12f, 2f)
        lineToRelative(-5.5f, 9f)
        horizontalLineToRelative(11f)
        close()
    }
    materialPath {
        moveTo(17.5f, 17.5f)
        moveToRelative(-4.5f, 0f)
        arcToRelative(4.5f, 4.5f, 0f, true, true, 9f, 0f)
        arcToRelative(4.5f, 4.5f, 0f, true, true, -9f, 0f)
    }
    materialPath {
        moveTo(3f, 13.5f)
        horizontalLineToRelative(8f)
        verticalLineToRelative(8f)
        horizontalLineTo(3f)
        close()
    }
}
