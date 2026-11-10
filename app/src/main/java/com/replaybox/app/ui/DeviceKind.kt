package com.replaybox.app.ui

import android.content.res.Configuration
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * One APK for the box and for phones/tablets (mobile milestone, 2026-11-10). Every touch
 * behaviour is gated on [LocalMobile]; on a TV it is false and the D-pad paths run unchanged.
 */
object DeviceKind {

    /** Mobile is anything the system does not report as a television. */
    fun isMobile(uiModeType: Int): Boolean = uiModeType != Configuration.UI_MODE_TYPE_TELEVISION

    /** A window taller than wide (split screen, system override of `sensorLandscape`). */
    fun isPortraitWindow(widthDp: Int, heightDp: Int): Boolean = heightDp > widthDp
}

/**
 * Layout of the window (mobile milestone PR iii): the box keeps the TV layout; a tablet keeps it
 * without overscan margins; a landscape phone (height < 480 dp) gets the compact canvas.
 */
enum class LayoutClass {
    TV, TABLET, COMPACT;

    companion object {
        const val COMPACT_MAX_HEIGHT_DP = 480

        fun of(mobile: Boolean, heightDp: Int): LayoutClass = when {
            !mobile -> TV
            heightDp < COMPACT_MAX_HEIGHT_DP -> COMPACT
            else -> TABLET
        }
    }
}

/** True on phones and tablets, false on the box. Provided once by MainActivity. */
val LocalMobile: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/** Scrolls by touch on mobile; on the box the screen stays a plain column, as before. */
@Composable
fun Modifier.mobileVerticalScroll(): Modifier =
    if (LocalMobile.current) verticalScroll(rememberScrollState()) else this

/**
 * Drag and fling on mobile for a TV lazy list or grid ([state] is its TvLazyListState or
 * TvLazyGridState). tv-foundation 1.0.0-alpha07 has no pointer input of its own: its
 * `userScrollEnabled` (true by default) drives focus scrolling and must stay untouched on the box.
 */
@Composable
fun Modifier.touchScroll(state: ScrollableState, orientation: Orientation): Modifier =
    if (LocalMobile.current) scrollable(state, orientation, reverseDirection = true) else this
