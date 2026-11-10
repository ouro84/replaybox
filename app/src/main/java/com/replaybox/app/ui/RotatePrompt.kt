package com.replaybox.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.replaybox.R

/**
 * True when a phone or tablet shows the app in a window taller than wide, despite
 * `sensorLandscape` (split screen, system override). Never true on the box.
 */
@Composable
fun portraitBlocked(): Boolean {
    val configuration = LocalConfiguration.current
    return LocalMobile.current &&
        DeviceKind.isPortraitWindow(configuration.screenWidthDp, configuration.screenHeightDp)
}

/**
 * Safety net of the mobile milestone: covers the whole app and takes every touch while the
 * window is portrait. The screen underneath stays composed, so the player keeps its state.
 */
@Composable
fun RotatePrompt() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Rb.ground)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.rotate_title), style = RbType.displayCompact, color = Rb.text, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.rotate_body),
            style = RbType.body,
            color = Rb.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
