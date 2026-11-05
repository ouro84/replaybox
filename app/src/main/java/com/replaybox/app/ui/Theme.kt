package com.replaybox.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

private val DarkColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF1E88E5),
    background = androidx.compose.ui.graphics.Color(0xFF0D1B2A),
    surface = androidx.compose.ui.graphics.Color(0xFF13263B),
    onBackground = androidx.compose.ui.graphics.Color(0xFFECEFF1),
    onSurface = androidx.compose.ui.graphics.Color(0xFFECEFF1),
)

@Composable
fun ReplayboxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}

@Composable
fun ReplayboxThemeSurface(content: @Composable () -> Unit) {
    ReplayboxTheme {
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) { content() }
    }
}
