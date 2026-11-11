package com.replaybox.app.ui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.replaybox.R

enum class RailDestination { HOME, GENRES, SEARCH, SETTINGS }

/** Where a rail destination lands: always on top of Home, so Back from it returns to Home. */
object RailNavigation {
    fun stackFor(destination: RailDestination): List<Screen> = when (destination) {
        RailDestination.HOME -> listOf(Screen.Home)
        RailDestination.GENRES -> listOf(Screen.Home, Screen.Genres)
        RailDestination.SEARCH -> listOf(Screen.Home, Screen.Search)
        RailDestination.SETTINGS -> listOf(Screen.Home, Screen.Settings)
    }

    /**
     * The stack after tapping [destination] on a rail shown over [stack]; null when that
     * destination is already on screen (re-tapping Recherche must not swap its state under the field).
     */
    fun next(stack: List<Screen>, destination: RailDestination): List<Screen>? =
        stackFor(destination).takeIf { it != stack }

    /**
     * The rail item to highlight over [stack]: the destination of the root-most screen that belongs
     * to one (Home and a Programme belong to none, so they fall back to Home). Null in the player,
     * which has no rail.
     */
    fun destinationFor(stack: List<Screen>): RailDestination? {
        if (stack.lastOrNull() is Screen.Player) return null
        return stack.firstNotNullOfOrNull { screen ->
            when (screen) {
                is Screen.Genres, is Screen.GenreGrid -> RailDestination.GENRES
                is Screen.Search -> RailDestination.SEARCH
                is Screen.Settings, is Screen.SubtitleSettings, is Screen.ProviderDetail, is Screen.About ->
                    RailDestination.SETTINGS
                is Screen.Home, is Screen.ShowDetail, is Screen.Player -> null
            }
        } ?: RailDestination.HOME
    }

    /** The back arrow shows wherever the rail does, except on the Home root. */
    fun showsBack(stack: List<Screen>): Boolean =
        destinationFor(stack) != null && stack != listOf(Screen.Home)

    /**
     * True when [destination]'s root is a strict prefix of [stack]: tapping it returns to that root
     * with its state (Back from a Programme opened from Recherche), not a fresh visit.
     */
    fun keepsState(stack: List<Screen>, destination: RailDestination): Boolean {
        val root = stackFor(destination)
        return stack.size > root.size && stack.subList(0, root.size) == root
    }
}

/**
 * The rail on every screen but Home (which draws its own) and the player, on mobile only: there is
 * no D-pad LEFT back to Home there, and the back arrow stands in for the hidden navigation bar.
 * Collapsed for good (touch never focuses it). On the box these screens stay as they are.
 */
@Composable
fun BoxScope.MobileRail(stack: List<Screen>, onNavigate: (RailDestination) -> Unit) {
    if (!LocalMobile.current) return
    RailNavigation.destinationFor(stack)?.let {
        NavRail(it, onNavigate, Modifier.align(Alignment.CenterStart), showBack = RailNavigation.showsBack(stack))
    }
}

/**
 * Left navigation rail (Google TV / Netflix pattern): icons only while the
 * content has focus, expanded with labels over a solid panel once focus
 * moves left into it. Entering always lands on the current destination.
 * With [showBack] a « Retour » arrow (system Back) sits in the slot above Accueil; the destinations
 * stay where they are without it.
 */
@OptIn(ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NavRail(
    current: RailDestination,
    onNavigate: (RailDestination) -> Unit,
    modifier: Modifier = Modifier,
    onExpandedChange: (Boolean) -> Unit = {},
    showBack: Boolean = false,
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
            // Mobile: the opaque rail takes the taps between and below its items; content scrolled
            // under it (a Programme's Saison pills) stays out of reach. Items still take their own.
            .then(if (LocalMobile.current) Modifier.pointerInput(Unit) { detectTapGestures { } } else Modifier)
            // The back slot comes out of the top padding: the destinations never move.
            .padding(
                top = Rb.safeVertical + LocalRail.current.topPadding(showBack),
                start = RailMetrics.INSET,
                end = RailMetrics.INSET,
            ),
        verticalArrangement = Arrangement.spacedBy(RailMetrics.GAP),
    ) {
        if (showBack) {
            val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            // Never selected and never the focus entry: Mist icon on the ground, no pill.
            RailItem(
                icon = Icons.Filled.ArrowBack,
                label = stringResource(R.string.back),
                selected = false,
                expanded = expanded,
                modifier = Modifier,
                onClick = { dispatcher?.onBackPressed() },
            )
        }
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
            .height(LocalRail.current.item)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick)
            .then(if (background != null) Modifier.background(background, Rb.pillShape) else Modifier)
            .padding(start = LocalRail.current.iconStart, end = RailMetrics.INSET),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconSize = LocalRail.current.icon
        Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = if (expanded) null else label,
                tint = tint,
                modifier = Modifier.size(iconSize),
            )
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
