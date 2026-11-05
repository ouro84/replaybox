package com.replaybox.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import com.replaybox.R
import com.replaybox.app.ReplayboxApplication
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.StreamType
import com.replaybox.provider.tf1.StreamResolutionException

private sealed interface PlayerState {
    object Resolving : PlayerState
    data class Ready(
        val manifestUrl: String,
        val type: StreamType,
        val licenseUrl: String?,
        val licenseHeaders: Map<String, String>,
    ) : PlayerState
    data class Error(val message: String, val accountRequired: Boolean) : PlayerState
}

@Composable
fun PlayerScreen(episodeId: EpisodeId, title: String) {
    val provider = Providers.all.first()
    var state by remember { mutableStateOf<PlayerState>(PlayerState.Resolving) }
    var attempt by remember { mutableStateOf(0) }

    suspend fun resolve() {
        state = PlayerState.Resolving
        var failures = 0
        while (true) {
            try {
                val stream = provider.stream(episodeId)
                state = PlayerState.Ready(stream.manifestUrl, stream.type, stream.licenseUrl, stream.licenseHeaders)
                return
            } catch (error: Exception) {
                failures += 1
                if (failures >= 2) {
                    val account = error is StreamResolutionException && error.accountOrDrmRequired
                    state = PlayerState.Error(error.message ?: "error", account)
                    return
                }
            }
        }
    }

    LaunchedEffect(attempt) { resolve() }

    when (val current = state) {
        is PlayerState.Resolving -> CenteredMessage(stringResource(R.string.loading))
        is PlayerState.Error -> Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.player_error_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                text = when {
                    !current.accountRequired -> current.message.ifBlank { stringResource(R.string.player_error_generic) }
                    ReplayboxApplication.tf1Auth.hasCredentials() -> stringResource(R.string.player_error_tf1_content)
                    else -> stringResource(R.string.player_error_tf1_account)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            Button(onClick = { attempt += 1 }) {
                Text(stringResource(R.string.retry))
            }
        }
        is PlayerState.Ready -> PlayerSurface(
            manifestUrl = current.manifestUrl,
            type = current.type,
            licenseUrl = current.licenseUrl,
            licenseHeaders = current.licenseHeaders,
        )
    }
}

@Composable
private fun CenteredMessage(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun PlayerSurface(manifestUrl: String, type: StreamType, licenseUrl: String?, licenseHeaders: Map<String, String>) {
    val context = LocalContext.current
    val player = remember(manifestUrl) {
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .apply {
                val media = MediaItem.Builder().setUri(manifestUrl).setMimeType(
                    when (type) {
                        StreamType.DASH -> MimeTypes.APPLICATION_MPD
                        StreamType.HLS -> MimeTypes.APPLICATION_M3U8
                    },
                )
                if (licenseUrl != null) {
                    media.setDrmConfiguration(
                        MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                            .setLicenseUri(licenseUrl)
                            .setLicenseRequestHeaders(licenseHeaders)
                            .build(),
                    )
                }
                setMediaItem(media.build())
                prepare()
                playWhenReady = true
            }
    }
    // Routes the TV remote media keys (play/pause/ffwd/rewind) to the player.
    DisposableEffect(player) {
        val session = MediaSession.Builder(context, player).build()
        onDispose {
            session.release()
            player.release()
        }
    }
    // Home on the remote backgrounds the activity without leaving the composition:
    // pause on lifecycle pause so no audio leaks behind the launcher.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(player, lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                useController = true
                controllerShowTimeoutMs = 5000
            }
        },
    )
}
