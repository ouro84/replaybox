package com.replaybox.app.ui

import android.content.res.Configuration
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

/** True on phones and tablets, false on the box. Provided once by MainActivity. */
val LocalMobile: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/** Scrolls by touch on mobile; on the box the screen stays a plain column, as before. */
@Composable
fun Modifier.mobileVerticalScroll(): Modifier =
    if (LocalMobile.current) verticalScroll(rememberScrollState()) else this
