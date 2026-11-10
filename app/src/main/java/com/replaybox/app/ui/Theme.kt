package com.replaybox.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Replaybox design tokens: the streaming standard, tuned for a 55" TV at
 * 3 m in a dim room (PRODUCT.md). The Bbox renders 960x540 dp (320 dpi), so
 * 1 dp = 2 px on the 1080p panel. Contrast on [Rb.ground]: text 17.3:1,
 * textSecondary 10.2:1, textTertiary 7.5:1 (AAA). textTertiary stays on the
 * ground only (6.8:1 on surface). Focus white and the amber accent exceed
 * 3:1 against every surface and track they sit on.
 */
object Rb {
    // Ground and tonal surfaces, cool near-black (never pure black: OLED smear, harsh edges).
    val ground = Color(0xFF0E1013)
    val surface = Color(0xFF181B20)
    val surfaceRaised = Color(0xFF262A31)
    val track = Color(0xFF3A3F48)

    // Text.
    val text = Color(0xFFF3F4F6)
    val textSecondary = Color(0xFFB8BEC8)
    val textTertiary = Color(0xFF9CA3AE)

    /** Focus is white: outline + scale, never colour alone. */
    val focus = Color(0xFFFFFFFF)
    val onFocus = Color(0xFF0E1013)

    /** The single accent: Progression and the current nav destination, nothing else. */
    val accent = Color(0xFFF2B544)

    val error = Color(0xFFFF9C8F)

    // Geometry (dp). Overscan-safe insets are 5 % of 960x540 on a TV; phones and tablets
    // have no overscan and take smaller margins (LocalInsets, mobile milestone PR iii).
    val safeHorizontal: Dp
        @Composable @ReadOnlyComposable get() = LocalInsets.current.horizontal
    val safeVertical: Dp
        @Composable @ReadOnlyComposable get() = LocalInsets.current.vertical
    val railCollapsed = 72.dp
    val railExpanded = 232.dp
    val contentStart = 104.dp
    val cardGap = 20.dp // equals the rail gutter: the card before the focused one scrolls fully off
    val focusOutline = 3.dp
    const val FOCUS_SCALE = 1.1f
    const val BUTTON_FOCUS_SCALE = 1.06f

    val cardShape = RoundedCornerShape(10.dp)
    val buttonShape = RoundedCornerShape(8.dp)
    val pillShape = RoundedCornerShape(50)

    // Motion (ms). Compose scales these with the system animator setting.
    const val FOCUS_MS = 200
    const val BACKDROP_MS = 300
}

/** Screen-edge margins: the TV overscan-safe ones, or the smaller mobile ones. */
data class Insets(val horizontal: Dp, val vertical: Dp)

val TvInsets = Insets(48.dp, 27.dp)
val MobileInsets = Insets(24.dp, 16.dp)
val LocalInsets = staticCompositionLocalOf { TvInsets }

/** Type scale in sp; floor 14 sp, body 18 sp (PRODUCT.md, 3 m viewing). */
object RbType {
    val display = TextStyle(fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
    /** Long titles: two lines at this size instead of one truncated display line. */
    val displayCompact = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
    val railTitle = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
    val body = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal)
    val bodyStrong = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
    val meta = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal)
    val cardTitle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
    val label = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
}

private val DarkColors = darkColorScheme(
    primary = Rb.focus,
    onPrimary = Rb.onFocus,
    background = Rb.ground,
    onBackground = Rb.text,
    surface = Rb.surface,
    onSurface = Rb.text,
    surfaceVariant = Rb.surfaceRaised,
    onSurfaceVariant = Rb.textSecondary,
    secondary = Rb.accent,
    error = Rb.error,
)

private val RbTypography = Typography(
    headlineLarge = RbType.display,
    headlineMedium = RbType.display.copy(fontSize = 32.sp, lineHeight = 38.sp),
    headlineSmall = RbType.railTitle.copy(fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = RbType.railTitle,
    titleMedium = RbType.bodyStrong,
    bodyLarge = RbType.body,
    bodyMedium = RbType.meta,
    bodySmall = RbType.label,
    labelLarge = RbType.bodyStrong,
    labelMedium = RbType.meta,
    labelSmall = RbType.label,
)

@Composable
fun ReplayboxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = RbTypography,
        content = content,
    )
}

@Composable
fun ReplayboxThemeSurface(content: @Composable () -> Unit) {
    ReplayboxTheme {
        // No Material Surface sits under the screens, so the default content
        // colour (black) would leak into every Text that does not set one.
        CompositionLocalProvider(LocalContentColor provides Rb.text) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Rb.ground),
            ) { content() }
        }
    }
}
