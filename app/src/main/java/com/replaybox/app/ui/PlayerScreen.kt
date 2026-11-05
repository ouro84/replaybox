package com.replaybox.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface PlayerState {
    object Resolving : PlayerState
    data class Ready(
        val manifestUrl: String,
        val type: StreamType,
        val licenseUrl: String?,
        val licenseHeaders: Map<String, String>,
        val subtitleUrl: String?,
        val subtitleLanguage: String?,
        val resumeAtMs: Long?,
    ) : PlayerState
    data class Error(val message: String, val accountRequired: Boolean) : PlayerState
}

@Composable
fun PlayerScreen(episodeId: EpisodeId, title: String) {
    val provider = Providers.resolveEpisode(episodeId.value)
    var state by remember { mutableStateOf<PlayerState>(PlayerState.Resolving) }
    var attempt by remember { mutableStateOf(0) }

    suspend fun resolve() {
        state = PlayerState.Resolving
        var failures = 0
        while (true) {
            try {
                val stream = provider.stream(episodeId)
                val resumeAtMs = ReplayboxApplication.catalog.resumeAtFor(episodeId)
                state = PlayerState.Ready(
                    stream.manifestUrl,
                    stream.type,
                    stream.licenseUrl,
                    stream.licenseHeaders,
                    stream.subtitleUrl,
                    stream.subtitleLanguage,
                    resumeAtMs,
                )
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
            episodeId = episodeId,
            manifestUrl = current.manifestUrl,
            type = current.type,
            licenseUrl = current.licenseUrl,
            licenseHeaders = current.licenseHeaders,
            subtitleUrl = current.subtitleUrl,
            subtitleLanguage = current.subtitleLanguage,
            resumeAtMs = current.resumeAtMs,
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
private fun PlayerSurface(
    episodeId: EpisodeId,
    manifestUrl: String,
    type: StreamType,
    licenseUrl: String?,
    licenseHeaders: Map<String, String>,
    subtitleUrl: String?,
    subtitleLanguage: String?,
    resumeAtMs: Long?,
) {
    val context = LocalContext.current
    val catalog = ReplayboxApplication.catalog
    val scope = remember { CoroutineScope(Dispatchers.Main.immediate) }
    var playerReleased by remember { mutableStateOf(false) }

    val player = remember(manifestUrl) {
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .apply {
                val builder = MediaItem.Builder().setUri(manifestUrl).setMimeType(
                    when (type) {
                        StreamType.DASH -> MimeTypes.APPLICATION_MPD
                        StreamType.HLS -> MimeTypes.APPLICATION_M3U8
                    },
                )
                if (licenseUrl != null) {
                    builder.setDrmConfiguration(
                        MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                            .setLicenseUri(licenseUrl)
                            .setLicenseRequestHeaders(licenseHeaders)
                            .build(),
                    )
                }
                // France TV strips text tracks from the manifest itself (SPEC):
                // captions are side-loaded from meta.subtitles (WebVTT).
                if (subtitleUrl != null) {
                    builder.setSubtitleConfigurations(
                        listOf(
                            MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitleUrl))
                                .setMimeType(MimeTypes.TEXT_VTT)
                                .setLanguage(subtitleLanguage)
                                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                .build(),
                        ),
                    )
                }
                setMediaItem(builder.build())
                prepare()
                // With a saved Progression the Reprendre / Débuter choice gates
                // playback; otherwise the Flux starts immediately.
                playWhenReady = resumeAtMs == null
            }
    }

    var showResumeChoice by remember(manifestUrl) { mutableStateOf(resumeAtMs != null) }

    // Sous-titres FR activés par défaut (choix produit PRD), toggle un
    // appui. The chip only appears when a text track exists — TF1 keeps
    // captions inside the manifest, France TV via the side-loaded WebVTT.
    var hasTextTracks by remember(manifestUrl) { mutableStateOf(false) }
    var subtitlesOn by remember(manifestUrl) { mutableStateOf(true) }

    fun selectFirstTextTrack() {
        val group = player.currentTracks.groups
            .firstOrNull { it.type == C.TRACK_TYPE_TEXT } ?: return
        // Prefer the French track: France TV captions carry lang "qsm",
        // TF1 exposes "fr"; fall back to the first track.
        var index = 0
        for (i in 0 until group.length) {
            val language = group.getTrackFormat(i).language
            if (language == "qsm" || language?.startsWith("fr") == true) {
                index = i
                break
            }
        }
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .addOverride(
                androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, index),
            )
            .build()
    }

    fun toggleSubtitles() {
        subtitlesOn = !subtitlesOn
        if (subtitlesOn) {
            selectFirstTextTrack()
        } else {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        }
    }

    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                hasTextTracks = tracks.containsType(C.TRACK_TYPE_TEXT)
                val selected = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_TEXT }
                    .any { group -> (0 until group.length).any { group.isTrackSelected(it) } }
                // Guarantee the default-on product choice even when the
                // Chaîne does not flag the caption track as default.
                if (hasTextTracks && subtitlesOn && !selected) {
                    selectFirstTextTrack()
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    fun saveProgress() {
        if (playerReleased || player.currentPosition <= 0) return
        val durationSeconds = player.duration.takeIf { it > 0 }?.let { (it / 1000).toInt() }
        scope.launch { catalog.saveProgress(episodeId, player.currentPosition / 1000, durationSeconds) }
    }

    // Progression (glossary): device-local playhead, saved every 5 s while
    // playing, at pause, when backgrounded and when leaving the Épisode.
    LaunchedEffect(player) {
        while (true) {
            delay(5_000)
            if (player.isPlaying) saveProgress()
        }
    }
    // Routes the TV remote media keys (play/pause/ffwd/rewind) to the player.
    // Disposal saves the Progression BEFORE the player is released (Home on
    // the remote backgrounds the activity without leaving the composition).
    DisposableEffect(player) {
        val session = MediaSession.Builder(context, player).build()
        onDispose {
            saveProgress()
            playerReleased = true
            scope.cancel()
            session.release()
            player.release()
        }
    }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(player, lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                player.pause()
                saveProgress()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val playerViewRef = remember { mutableStateOf<PlayerView?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // TV remote, one press: OK toggles subtitles while the player
            // controls are hidden (the captions key is unreliable across
            // remotes; the Android player view swallows D-pad focus, so the
            // chip alone is not reachable either). With controls shown, OK
            // keeps its play/pause meaning.
            .onPreviewKeyEvent { event ->
                val ok = event.type == KeyEventType.KeyDown &&
                    event.nativeKeyEvent.repeatCount == 0 &&
                    (
                        event.key == Key.DirectionCenter ||
                            event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_CAPTIONS
                        )
                if (ok && !showResumeChoice && hasTextTracks &&
                    playerViewRef.value?.isControllerFullyVisible != true
                ) {
                    toggleSubtitles()
                    true
                } else {
                    false
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = true
                    controllerShowTimeoutMs = 5000
                    playerViewRef.value = this
                }
            },
        )
        if (showResumeChoice && resumeAtMs != null) {
            ResumeChoice(
                resumeAtMs = resumeAtMs,
                onResume = {
                    player.seekTo(resumeAtMs)
                    player.playWhenReady = true
                    showResumeChoice = false
                },
                onStartOver = {
                    player.seekTo(0)
                    player.playWhenReady = true
                    showResumeChoice = false
                },
            )
        } else if (hasTextTracks) {
            SubtitleChip(
                enabled = subtitlesOn,
                onToggle = { toggleSubtitles() },
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

@Composable
private fun SubtitleChip(enabled: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text = stringResource(R.string.subtitle_toggle),
        style = MaterialTheme.typography.labelMedium,
        color = when {
            enabled -> MaterialTheme.colorScheme.primary
            focused -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        },
        modifier = modifier
            .padding(24.dp)
            .clickable { onToggle() }
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = 2.dp,
                color = when {
                    enabled || focused -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                },
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ResumeChoice(resumeAtMs: Long, onResume: () -> Unit, onStartOver: () -> Unit) {
    val resumeButton = remember { FocusRequester() }
    LaunchedEffect(Unit) { resumeButton.requestFocus() }
    val minutes = (resumeAtMs / 60_000).toInt()
    val formatted = "%d min".format(minutes)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.resume_position, formatted),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onResume, modifier = Modifier.focusRequester(resumeButton)) {
                Text(stringResource(R.string.resume_button))
            }
            Button(onClick = onStartOver) {
                Text(stringResource(R.string.start_over))
            }
        }
    }
}
