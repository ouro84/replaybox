package com.replaybox.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.replaybox.BuildConfig
import com.replaybox.R
import com.replaybox.app.ReplayboxApplication
import com.replaybox.app.update.CheckOutcome
import com.replaybox.app.update.UpdateInstaller
import com.replaybox.app.update.UpdateState
import kotlinx.coroutines.launch

/**
 * « À propos » in Paramètres: installed version and a manual update check.
 * The single button is the initial focus: « Installer » when an update is
 * downloaded, otherwise « Rechercher une mise à jour ».
 */
@Composable
fun AboutScreen() {
    val updater = ReplayboxApplication.updater
    val state by updater.state.collectAsState()
    val outcome by updater.lastCheck.collectAsState()
    val checking by updater.checking.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val button = remember { FocusRequester() }
    val ready = state as? UpdateState.Ready

    LaunchedEffect(ready != null) { runCatching { button.requestFocus() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = Rb.contentStart, top = Rb.safeVertical + 8.dp, end = Rb.safeHorizontal),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.settings_about), style = RbType.displayCompact, color = Rb.text)
        Spacer(Modifier.size(8.dp))
        // The launcher banner, the only place the logo shows in the app (M4 grilling).
        Image(
            painter = painterResource(R.drawable.ic_banner),
            contentDescription = stringResource(R.string.app_name),
            contentScale = ContentScale.Crop,
            // The banner's own margins: crop 22 dp top and bottom, and pull the
            // symbol back to the 104 dp text column (it starts 30 dp in at this width).
            modifier = Modifier
                .offset(x = (-30).dp)
                .width(240.dp)
                .height(90.dp),
        )
        Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = RbType.bodyStrong, color = Rb.text)
        val status = when {
            checking -> stringResource(R.string.about_checking)
            ready != null -> stringResource(R.string.about_ready, ready.version.toString())
            outcome == CheckOutcome.UP_TO_DATE -> stringResource(R.string.about_up_to_date)
            outcome == CheckOutcome.FAILED -> stringResource(R.string.about_failed)
            else -> null
        }
        status?.let { Text(it, style = RbType.body, color = Rb.textSecondary) }
        Row(Modifier.padding(top = 16.dp)) {
            if (ready != null) {
                PanelButton(
                    label = stringResource(R.string.update_install),
                    icon = null,
                    requester = button,
                    onClick = { UpdateInstaller.install(context, ready.apk) },
                )
            } else {
                PanelButton(
                    label = stringResource(R.string.about_check),
                    icon = null,
                    requester = button,
                    onClick = { if (!checking) scope.launch { updater.check() } },
                )
            }
        }
    }
}
