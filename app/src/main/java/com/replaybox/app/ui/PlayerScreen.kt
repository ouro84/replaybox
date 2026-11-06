package com.replaybox.app.ui

import androidx.compose.runtime.withFrameNanos
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.background
import com.replaybox.core.model.Episode
import com.replaybox.core.data.NextEpisodePolicy
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.Icons
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.width
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
import com.replaybox.core.model.ProgressContext
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
/**
 * [autoResume]: the user already chose « Reprendre » (billboard, Reprendre
 * card, Programme hero), so playback resumes at once instead of asking again.
 */
fun PlayerScreen(
    episodeId: EpisodeId,
    title: String,
    progressContext: ProgressContext? = null,
    autoResume: Boolean = false,
    /** Displayed list of a numbered Saison (empty otherwise): drives Épisode suivant. */
    queue: List<Episode> = emptyList(),
    onPlayNext: (Episode) -> Unit = {},
) {
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
        is PlayerState.Error -> {
            val retry = remember { FocusRequester() }
            LaunchedEffect(current) { runCatching { retry.requestFocus() } }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = Rb.contentStart, end = Rb.safeHorizontal, top = Rb.safeVertical + 72.dp),
            ) {
                Text(title, style = RbType.bodyStrong, color = Rb.textSecondary, maxLines = 1)
                Text(
                    stringResource(R.string.player_error_title),
                    style = RbType.displayCompact,
                    color = Rb.text,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = when {
                        !current.accountRequired -> current.message.ifBlank { stringResource(R.string.player_error_generic) }
                        ReplayboxApplication.tf1Auth.hasCredentials() -> stringResource(R.string.player_error_tf1_content)
                        else -> stringResource(R.string.player_error_tf1_account)
                    },
                    style = RbType.body,
                    color = Rb.textSecondary,
                    modifier = Modifier.padding(top = 12.dp).width(640.dp),
                )
                Box(Modifier.padding(top = 24.dp)) {
                    PanelButton(
                        label = stringResource(R.string.retry),
                        icon = Icons.Filled.Refresh,
                        requester = retry,
                        onClick = { attempt += 1 },
                    )
                }
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
            progressContext = progressContext,
            autoResume = autoResume,
            next = NextEpisodePolicy.next(queue, episodeId),
            onPlayNext = onPlayNext,
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
        Text(message, style = RbType.body, color = Rb.textSecondary)
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
    progressContext: ProgressContext?,
    autoResume: Boolean,
    next: Episode?,
    onPlayNext: (Episode) -> Unit,
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
                if (autoResume && resumeAtMs != null) seekTo(resumeAtMs)
                prepare()
                // With a saved Progression the Reprendre / Débuter choice gates
                // playback, unless Reprendre was already chosen; otherwise the
                // Flux starts immediately.
                playWhenReady = resumeAtMs == null || autoResume
            }
    }

    var showResumeChoice by remember(manifestUrl) { mutableStateOf(resumeAtMs != null && !autoResume) }

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
        if (playerReleased) return
        // Snapshot now (the player may be released right after), write on the
        // application scope so leaving the screen never cancels the write.
        val positionSeconds = player.currentPosition / 1000
        if (positionSeconds <= 0) return
        val durationSeconds = player.duration.takeIf { it > 0 }?.let { (it / 1000).toInt() }
        ReplayboxApplication.progress.record(episodeId, positionSeconds, durationSeconds, progressContext)
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

    // Épisode suivant: shown when the Flux ends and a next Épisode exists.
    var nextUp by remember(manifestUrl) { mutableStateOf(false) }
    DisposableEffect(player, next) {
        val endListener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_ENDED && next != null) nextUp = true
            }
        }
        player.addListener(endListener)
        onDispose { player.removeListener(endListener) }
    }
    BackHandler(enabled = nextUp) { nextUp = false }

    // Controls (decisions Q26–Q30): a Compose overlay replaces the Media3 touch
    // controller. Hidden, the root holds focus and reads the remote keys.
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    var controlsVisible by remember(manifestUrl) { mutableStateOf(false) }
    var interaction by remember { mutableStateOf(0) }
    var isPlaying by remember(manifestUrl) { mutableStateOf(false) }
    var positionMs by remember(manifestUrl) { mutableStateOf(0L) }
    var durationMs by remember(manifestUrl) { mutableStateOf(0L) }
    DisposableEffect(player) {
        val playingListener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(playingListener)
        onDispose { player.removeListener(playingListener) }
    }

    fun showControls() {
        positionMs = player.currentPosition
        durationMs = player.duration.coerceAtLeast(0)
        controlsVisible = true
        interaction++
    }
    LaunchedEffect(controlsVisible) {
        while (controlsVisible) {
            positionMs = player.currentPosition
            durationMs = player.duration.coerceAtLeast(0)
            delay(500)
        }
    }
    // Auto-hide 5 s after the last key while playing; paused, they stay.
    LaunchedEffect(controlsVisible, interaction, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(5_000)
            controlsVisible = false
        }
    }
    LaunchedEffect(controlsVisible, nextUp, showResumeChoice) {
        withFrameNanos { }
        if (nextUp || showResumeChoice) return@LaunchedEffect
        runCatching { if (controlsVisible) playFocus.requestFocus() else rootFocus.requestFocus() }
    }
    BackHandler(enabled = controlsVisible && !nextUp) { controlsVisible = false }
    // Captions climb above the controls panel while it is shown, never under it.
    // France TV WebVTT cues carry explicit line positions, which ignore the
    // SubtitleView bottom padding: the whole caption layer is lifted instead.
    LaunchedEffect(controlsVisible) {
        playerViewRef.value?.subtitleView?.let { captions ->
            captions.animate()
                .translationY(if (controlsVisible) -captions.height * CAPTIONS_ABOVE_CONTROLS else 0f)
                .setDuration(Rb.FOCUS_MS.toLong())
                .start()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || showResumeChoice || nextUp) return@onPreviewKeyEvent false
                interaction++
                when (PlayerKeys.action(event.nativeKeyEvent.keyCode, controlsVisible)) {
                    PlayerKeys.Action.NONE -> false
                    PlayerKeys.Action.SHOW -> { showControls(); true }
                    PlayerKeys.Action.SEEK_BACK -> { player.seekBack(); showControls(); true }
                    PlayerKeys.Action.SEEK_FORWARD -> { player.seekForward(); showControls(); true }
                    PlayerKeys.Action.PLAY_PAUSE -> {
                        if (player.isPlaying) player.pause() else player.play()
                        showControls()
                        true
                    }
                    PlayerKeys.Action.TOGGLE_SUBTITLES -> hasTextTracks.also { if (it) toggleSubtitles() }
                }
            }
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    // Compose draws the controls; the view only renders video and captions.
                    useController = false
                    isFocusable = false
                    descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    playerViewRef.value = this
                }
            },
        )
        if (nextUp && next != null) {
            NextUp(
                episode = next,
                onPlay = { onPlayNext(next) },
                onCancel = { nextUp = false },
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        } else if (showResumeChoice && resumeAtMs != null) {
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
        } else if (controlsVisible) {
            PlayerControls(
                showTitle = progressContext?.showTitle,
                episodeTitle = progressContext?.episodeTitle ?: "",
                positionMs = positionMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                subtitlesOn = if (hasTextTracks) subtitlesOn else null,
                playRequester = playFocus,
                onInteract = { interaction++ },
                onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                onSeekBack = { player.seekBack(); positionMs = player.currentPosition },
                onSeekForward = { player.seekForward(); positionMs = player.currentPosition },
                onRestart = { player.seekTo(0); player.play(); positionMs = 0 },
                onToggleSubtitles = { toggleSubtitles() },
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}

@Composable
private fun ResumeChoice(resumeAtMs: Long, onResume: () -> Unit, onStartOver: () -> Unit) {
    val resumeButton = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { resumeButton.requestFocus() } }
    val formatted = Playhead.format(resumeAtMs)
    // Same panel as Épisode suivant: bottom left, slate surface, safe frame.
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = Rb.safeHorizontal, bottom = Rb.safeVertical + 24.dp)
                .background(Rb.surface, Rb.cardShape)
                .padding(24.dp),
        ) {
            Text(stringResource(R.string.resume_position, formatted), style = RbType.railTitle, color = Rb.text)
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PanelButton(
                    label = stringResource(R.string.resume_button),
                    icon = Icons.Filled.PlayArrow,
                    requester = resumeButton,
                    onClick = onResume,
                )
                PanelButton(
                    label = stringResource(R.string.start_over),
                    icon = null,
                    requester = remember { FocusRequester() },
                    onClick = onStartOver,
                )
            }
        }
    }
}

/** Playback position as the player's own clock shows it: "0:45", "12:34", "1:02:05". */
object Playhead {
    fun format(positionMs: Long): String {
        val total = (positionMs / 1000).coerceAtLeast(0)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    }
}

/** Seconds before the next Épisode starts on its own. */
private const val NEXT_UP_SECONDS = 10

/**
 * End-of-Épisode panel (DESIGN.md world): Épisode suivant with a 10 s
 * countdown; « Lire maintenant » is focused, « Annuler » or Back stays put.
 */
@Composable
private fun NextUp(episode: Episode, onPlay: () -> Unit, onCancel: () -> Unit, modifier: Modifier) {
    val play = remember { FocusRequester() }
    var remaining by remember(episode.id) { mutableStateOf(NEXT_UP_SECONDS) }
    LaunchedEffect(episode.id) {
        runCatching { play.requestFocus() }
        while (remaining > 0) {
            delay(1_000)
            remaining -= 1
        }
        onPlay()
    }
    Column(
        modifier = modifier
            .padding(end = Rb.safeHorizontal, bottom = Rb.safeVertical + 24.dp)
            .width(420.dp)
            .background(Rb.surface, Rb.cardShape)
            .padding(24.dp),
    ) {
        Text(stringResource(R.string.next_up_title), style = RbType.railTitle, color = Rb.text)
        Text(
            episode.title,
            style = RbType.body,
            color = Rb.text,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            pluralStringResource(R.plurals.next_up_countdown, remaining, remaining),
            style = RbType.meta,
            color = Rb.textSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PanelButton(
                label = stringResource(R.string.next_up_play),
                icon = Icons.Filled.PlayArrow,
                requester = play,
                onClick = onPlay,
            )
            PanelButton(
                label = stringResource(R.string.cancel),
                icon = null,
                requester = remember { FocusRequester() },
                onClick = onCancel,
            )
        }
    }
}

/** Share of the screen height the captions rise by while the controls show (panel ≈ 30 %). */
private const val CAPTIONS_ABOVE_CONTROLS = 0.34f
