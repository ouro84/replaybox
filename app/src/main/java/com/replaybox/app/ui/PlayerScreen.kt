package com.replaybox.app.ui

import androidx.compose.runtime.withFrameNanos
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.background
import com.replaybox.core.model.Episode
import com.replaybox.core.data.NextUpWindow
import com.replaybox.core.data.SeasonEpisode
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.Icons
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import com.replaybox.R
import com.replaybox.core.account.AccountException
import com.replaybox.provider.m6.M6Parsers
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
        val licenseResponseField: String? = null,
    ) : PlayerState
    /** [playback]: the Flux resolved but failed while playing. */
    data class Error(val message: String, val accountRequired: Boolean, val playback: Boolean = false) : PlayerState
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
    onPlayNext: (Episode) -> Unit = {},
    /** The Épisode ended with no Épisode suivant to play (none, or cancelled): leave the player. */
    onFinished: () -> Unit = {},
    /** Mobile back arrow in the controls: leaves the player at once. */
    onBack: () -> Unit = {},
) {
    val provider = Providers.resolveEpisode(episodeId.value)
    val context = LocalContext.current
    val isM6 = episodeId.value.startsWith(M6Parsers.ID_PREFIX)
    var state by remember { mutableStateOf<PlayerState>(PlayerState.Resolving) }
    var attempt by remember { mutableStateOf(0) }
    val recovery = remember { PlaybackRecovery() }

    // Épisode suivant, looked up while the Flux resolves and plays (the next Saison's
    // Épisodes load now), so the panel never waits on the network. Needs the Programme.
    var next by remember { mutableStateOf<SeasonEpisode?>(null) }
    LaunchedEffect(episodeId) {
        val showId = progressContext?.showId ?: return@LaunchedEffect
        next = withContext(Dispatchers.IO) {
            runCatching { ReplayboxApplication.catalog.nextEpisode(provider, showId, episodeId) }.getOrNull()
        }
    }

    suspend fun resolve() {
        state = PlayerState.Resolving
        var failures = 0
        while (true) {
            try {
                val stream = provider.stream(episodeId)
                val resumeAtMs = recovery.resumeAtMs ?: ReplayboxApplication.catalog.resumeAtFor(episodeId)
                state = PlayerState.Ready(
                    stream.manifestUrl,
                    stream.type,
                    stream.licenseUrl,
                    stream.licenseHeaders,
                    stream.subtitleUrl,
                    stream.subtitleLanguage,
                    resumeAtMs,
                    stream.licenseResponseField,
                )
                return
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                failures += 1
                if (failures >= 2) {
                    val account = error is StreamResolutionException && error.accountOrDrmRequired
                    // Account steps (M6+ upfront token) explain themselves in French.
                    val message = if (error is AccountException) {
                        accountErrorText(context, ProviderGroups.byId(ProviderGroups.M6)?.name ?: "M6+", error)
                    } else {
                        error.message ?: "error"
                    }
                    state = PlayerState.Error(message, account)
                    return
                }
            }
        }
    }

    LaunchedEffect(attempt) { resolve() }

    when (val current = state) {
        is PlayerState.Resolving -> Box(Modifier.fillMaxSize().background(Rb.ground)) {
            LoadingRing(Modifier.align(Alignment.Center))
            // Mobile: a way out while the Flux resolves (no controls yet, no navigation bar).
            if (LocalMobile.current) PlayerBackArrow(onBack, Modifier.align(Alignment.TopStart))
        }
        is PlayerState.Error -> Box(Modifier.fillMaxSize()) {
            val retry = remember { FocusRequester() }
            LaunchedEffect(current) { runCatching { retry.requestFocus() } }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = RailMetrics.Tv.contentStart, end = Rb.safeHorizontal, top = Rb.safeVertical + 72.dp),
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
                        current.playback -> stringResource(R.string.player_error_playback)
                        !current.accountRequired -> current.message.ifBlank { stringResource(R.string.player_error_generic) }
                        isM6 -> stringResource(R.string.player_error_m6_account)
                        ReplayboxApplication.tf1Auth.hasCredentials() -> stringResource(R.string.player_error_tf1_content)
                        else -> stringResource(R.string.player_error_tf1_account)
                    },
                    style = RbType.body,
                    color = Rb.textSecondary,
                    modifier = Modifier.padding(top = 12.dp).textWidth(640.dp),
                )
                Box(Modifier.padding(top = 24.dp)) {
                    PanelButton(
                        label = stringResource(R.string.retry),
                        icon = Icons.Filled.Refresh,
                        requester = retry,
                        onClick = {
                            recovery.onUserRetry()
                            attempt += 1
                        },
                    )
                }
            }
            // Mobile: « Retour » next to « Réessayer », where the controls' arrow would be.
            if (LocalMobile.current) PlayerBackArrow(onBack, Modifier.align(Alignment.TopStart))
        }
        is PlayerState.Ready -> PlayerSurface(
            episodeId = episodeId,
            manifestUrl = current.manifestUrl,
            type = current.type,
            licenseUrl = current.licenseUrl,
            licenseHeaders = current.licenseHeaders,
            subtitleUrl = current.subtitleUrl,
            subtitleLanguage = current.subtitleLanguage,
            licenseResponseField = current.licenseResponseField,
            resumeAtMs = current.resumeAtMs,
            progressContext = progressContext,
            // After a recovery, resume at once (Round 8 Q2): no second prompt.
            autoResume = autoResume || recovery.resumeAtMs != null,
            next = next,
            onPlayNext = onPlayNext,
            onFinished = onFinished,
            onBack = onBack,
            onPlaying = { recovery.onPlaying() },
            onPlaybackError = { positionMs ->
                when (recovery.onError(positionMs)) {
                    PlaybackRecovery.Action.RETRY -> attempt += 1
                    PlaybackRecovery.Action.SHOW_ERROR ->
                        state = PlayerState.Error(message = "", accountRequired = false, playback = true)
                }
            },
        )
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
    licenseResponseField: String?,
    resumeAtMs: Long?,
    progressContext: ProgressContext?,
    autoResume: Boolean,
    next: SeasonEpisode?,
    onPlayNext: (Episode) -> Unit,
    onFinished: () -> Unit,
    onBack: () -> Unit,
    onPlaying: () -> Unit,
    /** Position to resume at, or null while the resume prompt is still pending. */
    onPlaybackError: (positionMs: Long?) -> Unit,
) {
    val context = LocalContext.current
    val catalog = ReplayboxApplication.catalog
    val scope = remember { CoroutineScope(Dispatchers.Main.immediate) }
    var playerReleased by remember { mutableStateOf(false) }
    // Set when Épisode suivant takes over: the last save records this Épisode as finished.
    var movedOn by remember { mutableStateOf(false) }

    val mobile = LocalMobile.current
    val player = remember(manifestUrl) {
        ExoPlayer.Builder(context)
            .apply {
                // JSON-wrapped licence (M6+ drmtoday): own DRM session manager. Raw licences
                // (TF1) keep the default path driven by the MediaItem DrmConfiguration.
                if (licenseUrl != null && licenseResponseField != null) {
                    setMediaSourceFactory(jsonLicenseMediaSourceFactory(context, licenseUrl, licenseHeaders, licenseResponseField))
                }
            }
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            // Audio focus (a call, another player) pauses playback; unplugged headphones too,
            // on mobile only (PlayerAudio.pausesWhenNoisy).
            .setAudioAttributes(PlayerAudio.attributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(PlayerAudio.pausesWhenNoisy(mobile))
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
                // Remembered quality cap (Q3, Q8): a ceiling, Auto below it.
                ReplayboxApplication.playbackPreferences.load().maxHeight?.let { cap ->
                    trackSelectionParameters = trackSelectionParameters.buildUpon()
                        .setMaxVideoSize(Int.MAX_VALUE, cap)
                        .build()
                }
                if (autoResume && resumeAtMs != null) seekTo(resumeAtMs)
                prepare()
                // With a saved Progression the Reprendre / Débuter choice gates
                // playback, unless Reprendre was already chosen; otherwise the
                // Flux starts immediately.
                playWhenReady = resumeAtMs == null || autoResume
            }
    }

    // Tracks and Options (decisions Q3-Q10): every choice comes from the
    // tracks the player has. Audio, quality and subtitles (on/off and kind,
    // PRD story 2) follow the remembered preferences; subtitles start on,
    // French first (Q7), until the viewer chooses otherwise.
    var preferences by remember { mutableStateOf(ReplayboxApplication.playbackPreferences.load()) }
    var trackRefs by remember(manifestUrl) { mutableStateOf(emptyList<TrackRef>()) }
    var audioKind by remember(manifestUrl) { mutableStateOf<AudioKind?>(null) }
    var subtitleKind by remember(manifestUrl) { mutableStateOf<SubtitleKind?>(null) }
    var subtitlesOn by remember(manifestUrl) { mutableStateOf(preferences.subtitlesOn) }
    val subtitleStyle by ReplayboxApplication.subtitleStyle.style.collectAsState()
    var speed by remember(manifestUrl) { mutableStateOf(1f) }
    var audioDefaulted by remember(manifestUrl) { mutableStateOf(false) }
    var textDefaulted by remember(manifestUrl) { mutableStateOf(false) }
    val hasTextTracks = trackRefs.any { it.type == TrackKind.TEXT }

    fun select(ref: TrackRef, type: Int) {
        val group = player.currentTracks.groups.getOrNull(ref.group) ?: return
        // Audio keeps every supported bitrate of the group (adaptive); text is
        // one track. Unsupported formats are never forced (a mixed AAC/EC-3
        // group would fail on a box without EC-3).
        val indices = if (type == C.TRACK_TYPE_AUDIO) {
            (0 until group.length).filter { group.isTrackSupported(it) }.ifEmpty { listOf(ref.index) }
        } else {
            listOf(ref.index)
        }
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, indices))
            .build()
    }

    fun chooseAudio(choice: AudioChoice, remember: Boolean) {
        select(choice.track, C.TRACK_TYPE_AUDIO)
        audioKind = choice.kind
        if (remember) {
            preferences = preferences.withAudio(choice.kind)
            ReplayboxApplication.playbackPreferences.save(preferences)
        }
    }

    fun chooseSubtitle(choice: SubtitleChoice?, remember: Boolean) {
        if (remember) {
            preferences = preferences.withSubtitle(choice?.kind, hasTextTracks)
            ReplayboxApplication.playbackPreferences.save(preferences)
        }
        if (choice == null) {
            subtitlesOn = false
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        } else {
            subtitlesOn = true
            subtitleKind = choice.kind
            select(choice.track, C.TRACK_TYPE_TEXT)
        }
    }

    fun toggleSubtitles() {
        val choices = PlaybackChoices.subtitles(trackRefs)
        chooseSubtitle(
            if (subtitlesOn) null
            else choices.firstOrNull { it.kind == subtitleKind }
                ?: PlaybackChoices.subtitleToApply(choices, true, preferences.subtitleKind),
            remember = true,
        )
    }

    fun chooseQuality(cap: Int?) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .apply { if (cap == null) clearVideoSizeConstraints() else setMaxVideoSize(Int.MAX_VALUE, cap) }
            .build()
        preferences = preferences.copy(maxHeight = cap)
        ReplayboxApplication.playbackPreferences.save(preferences)
    }

    fun chooseSpeed(value: Float) {
        speed = value
        player.setPlaybackSpeed(value)
    }

    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val refs = tracks.toTrackRefs()
                trackRefs = refs
                val audio = PlaybackChoices.audio(refs)
                if (!audioDefaulted && audio.isNotEmpty()) {
                    audioDefaulted = true
                    // Look up what plays among all audio tracks, not the deduped choices.
                    val playing = refs.firstOrNull { it.type == TrackKind.AUDIO && tracks.groups[it.group].isSelected }
                    val toApply = PlaybackChoices.audioToApply(audio, preferences.audio, playing)
                    if (toApply != null) chooseAudio(toApply, remember = false) else audioKind = playing?.let(PlaybackChoices::kindOf)
                }
                val text = PlaybackChoices.subtitles(refs)
                if (!textDefaulted && text.isNotEmpty()) {
                    textDefaulted = true
                    chooseSubtitle(
                        PlaybackChoices.subtitleToApply(text, preferences.subtitlesOn, preferences.subtitleKind),
                        remember = false,
                    )
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
        val durationSeconds = player.duration.takeIf { it > 0 }?.let { (it / 1000).toInt() }
        // Moving on to Épisode suivant ends this one, even 10 s early: it reads watched.
        val positionSeconds = if (movedOn && durationSeconds != null) durationSeconds.toLong() else player.currentPosition / 1000
        if (positionSeconds <= 0) return
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
    // Mobile: a portrait window covers the player with « Tourne l'appareil » and pauses it.
    // Back in landscape it stays paused; the pause has already shown the controls (PauseReveal).
    val blocked = portraitBlocked()
    LaunchedEffect(blocked) { if (blocked) player.pause() }

    val playerViewRef = remember { mutableStateOf<PlayerView?>(null) }

    var showResumeChoice by remember(manifestUrl) { mutableStateOf(resumeAtMs != null && !autoResume) }

    // Épisode suivant (NextUpWindow): over the last 10 s while the video plays on. The
    // countdown is the time left in the video; « Annuler » / Back hide the panel until the
    // playhead leaves the window. At the end, the next Épisode plays, unless cancelled (or
    // none): then the player leaves, back to the Programme page.
    var nextUpCancelled by remember(manifestUrl) { mutableStateOf(false) }
    var nextUpSeconds by remember(manifestUrl) { mutableStateOf<Int?>(null) }
    var optionsOpen by remember(manifestUrl) { mutableStateOf(false) }
    val nextUp = nextUpSeconds != null && next != null
    LaunchedEffect(player, next) {
        while (true) {
            val position = player.currentPosition
            val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
            nextUpCancelled = NextUpWindow.stillCancelled(position, duration, nextUpCancelled)
            val visible = !showResumeChoice && NextUpWindow.visible(position, duration, nextUpCancelled, next != null)
            nextUpSeconds = if (visible) NextUpWindow.secondsLeft(position, duration) else null
            delay(NEXT_UP_TICK_MS)
        }
    }
    fun playNext(episode: Episode) {
        movedOn = true
        onPlayNext(episode)
    }
    val onEnded by rememberUpdatedState {
        val upcoming = next
        if (upcoming != null && !nextUpCancelled) playNext(upcoming.episode) else onFinished()
    }
    DisposableEffect(player) {
        val endListener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_ENDED) onEnded()
            }
        }
        player.addListener(endListener)
        onDispose { player.removeListener(endListener) }
    }
    fun cancelNextUp() {
        nextUpCancelled = true
        nextUpSeconds = null
    }
    BackHandler(enabled = nextUp) { cancelNextUp() }

    // Controls (decisions Q26–Q30): a Compose overlay replaces the Media3 touch
    // controller. Hidden, the root holds focus and reads the remote keys.
    val rootFocus = remember { FocusRequester() }
    val rowFocus = remember { FocusRequester() }
    var controlsVisible by remember(manifestUrl) { mutableStateOf(false) }
    // A control row button holds focus (PlayerKeys.Focus.ROW); never true once hidden.
    var focusInRow by remember(manifestUrl) { mutableStateOf(false) }
    LaunchedEffect(controlsVisible) { if (!controlsVisible) focusInRow = false }
    var interaction by remember { mutableStateOf(0) }
    var isPlaying by remember(manifestUrl) { mutableStateOf(false) }
    var positionMs by remember(manifestUrl) { mutableStateOf(0L) }
    var durationMs by remember(manifestUrl) { mutableStateOf(0L) }
    var buffering by remember(manifestUrl) { mutableStateOf(false) }
    // The centre glyph's inputs (CentreGlyph): the play intent (player.playWhenReady, named
    // apart from the listener parameter it mirrors), not isPlaying, and the end.
    var playIntent by remember(manifestUrl) { mutableStateOf(player.playWhenReady) }
    var ended by remember(manifestUrl) { mutableStateOf(false) }
    // Held silent by a call (transient audio focus loss); Media3 resumes when it ends.
    var callSilenced by remember(manifestUrl) { mutableStateOf(false) }
    fun togglePlayPause() {
        if (PlayerAudio.playing(player.isPlaying, callSilenced)) player.pause() else player.play()
    }
    DisposableEffect(player) {
        val playingListener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) onPlaying()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == androidx.media3.common.Player.STATE_BUFFERING
                ended = playbackState == androidx.media3.common.Player.STATE_ENDED
            }

            // Network cut, expired signed URL, decoder failure: hand over to
            // PlaybackRecovery with the position to resume at.
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                onPlaybackError(if (showResumeChoice) null else player.currentPosition.coerceAtLeast(0))
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
    // Any pause reveals the controls (PauseReveal). Listened on the player, not the key
    // handler: Compose lets the media keys and HDMI-CEC through to the MediaSession, which
    // pauses the player itself, and backgrounding pauses without a key at all. Declared
    // after the MediaSession effect, so it is removed before the player is released.
    val currentNextUp by rememberUpdatedState(nextUp)
    DisposableEffect(player) {
        val pauseListener = object : androidx.media3.common.Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                playIntent = playWhenReady
                val reveal = PauseReveal.shows(
                    playWhenReady = playWhenReady,
                    endOfMedia = reason == androidx.media3.common.Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM,
                    resumePrompt = showResumeChoice,
                    nextUp = currentNextUp,
                    optionsOpen = optionsOpen,
                )
                if (reveal) showControls()
            }

            // A ringing call holds playback silent without a pause: show the controls too.
            override fun onPlaybackSuppressionReasonChanged(reason: Int) {
                callSilenced = reason == androidx.media3.common.Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
                val reveal = PlayerAudio.callReveals(
                    transientFocusLoss = callSilenced,
                    resumePrompt = showResumeChoice,
                    nextUp = currentNextUp,
                    optionsOpen = optionsOpen,
                )
                if (reveal) showControls()
            }
        }
        player.addListener(pauseListener)
        onDispose { player.removeListener(pauseListener) }
    }
    LaunchedEffect(controlsVisible) {
        while (controlsVisible) {
            positionMs = player.currentPosition
            durationMs = player.duration.coerceAtLeast(0)
            delay(500)
        }
    }
    // A drag on the mobile timeline holds the controls (B4); its release restarts the 5 s.
    var scrubbing by remember(manifestUrl) { mutableStateOf(false) }
    // Auto-hide 5 s after the last key while playing; paused, they stay.
    LaunchedEffect(controlsVisible, interaction, isPlaying, scrubbing) {
        if (controlsVisible && isPlaying && !scrubbing) {
            delay(5_000)
            controlsVisible = false
        }
    }
    // The panel takes over: the controls and Options make way, as at the end before.
    LaunchedEffect(nextUp) {
        if (nextUp) {
            optionsOpen = false
            controlsVisible = false
        }
    }
    // Centre glyph (CentreGlyph, B2): a play glyph fades once when a pause ends in a resume.
    val glyph = CentreGlyph.of(playIntent, callSilenced, buffering, ended, showResumeChoice, nextUp, optionsOpen)
    var lastGlyph by remember(manifestUrl) { mutableStateOf(CentreGlyph.Kind.NONE) }
    var playFlash by remember { mutableStateOf(0) }
    LaunchedEffect(glyph) {
        if (CentreGlyph.flashesPlay(lastGlyph, glyph, playIntent)) playFlash++
        lastGlyph = glyph
    }
    LaunchedEffect(controlsVisible, nextUp, showResumeChoice, optionsOpen) {
        withFrameNanos { }
        // The Options panel focuses its own selected chip.
        if (nextUp || showResumeChoice || optionsOpen) return@LaunchedEffect
        // No row button has focus when the controls appear (B3): the root reads OK.
        runCatching { rootFocus.requestFocus() }
    }
    BackHandler(enabled = controlsVisible && !nextUp) { controlsVisible = false }

    fun openOptions() {
        controlsVisible = false
        optionsOpen = true
    }

    // The side a double-tap just sought, shown 600 ms; a new double-tap restarts it.
    var flash by remember { mutableStateOf(PlayerTouch.Seek.NONE) }
    var flashTick by remember { mutableStateOf(0) }
    LaunchedEffect(flashTick) {
        if (flashTick == 0) return@LaunchedEffect
        delay(SEEK_FLASH_MS)
        flash = PlayerTouch.Seek.NONE
    }

    fun closeOptions() {
        optionsOpen = false
        showControls()
    }
    // Declared after the controls handler: with the panel open, Back closes it
    // first. Never while Épisode suivant shows: there Back cancels the countdown.
    BackHandler(enabled = optionsOpen && !nextUp) { closeOptions() }
    // Legibility setting (Paramètres > Sous-titres or Options), applied live.
    LaunchedEffect(subtitleStyle) { playerViewRef.value?.subtitleView?.applyStyle(subtitleStyle) }
    // Captions climb above the controls panel while it is shown, never under it.
    // France TV WebVTT cues carry explicit line positions, which ignore the
    // SubtitleView bottom padding: the whole caption layer is lifted instead.
    LaunchedEffect(controlsVisible, optionsOpen) {
        val lift = when {
            optionsOpen -> CAPTIONS_ABOVE_OPTIONS
            controlsVisible -> CAPTIONS_ABOVE_CONTROLS
            else -> 0f
        }
        playerViewRef.value?.subtitleView?.let { captions ->
            captions.animate()
                .translationY(-captions.height * lift)
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
                val focus = PlayerKeys.focus(controlsVisible, optionsOpen, focusInRow)
                when (PlayerKeys.action(event.nativeKeyEvent.keyCode, focus)) {
                    PlayerKeys.Action.NONE -> false
                    PlayerKeys.Action.SHOW -> { showControls(); true }
                    PlayerKeys.Action.SEEK_BACK -> { player.seekBack(); showControls(); true }
                    PlayerKeys.Action.SEEK_FORWARD -> { player.seekForward(); showControls(); true }
                    PlayerKeys.Action.PLAY_PAUSE -> {
                        togglePlayPause()
                        showControls()
                        true
                    }
                    PlayerKeys.Action.ENTER_ROW -> { runCatching { rowFocus.requestFocus() }; true }
                    PlayerKeys.Action.LEAVE_ROW -> { runCatching { rootFocus.requestFocus() }; true }
                    PlayerKeys.Action.TOGGLE_SUBTITLES -> hasTextTracks.also { if (it) toggleSubtitles() }
                    PlayerKeys.Action.OPTIONS -> { if (optionsOpen) closeOptions() else openOptions(); true }
                }
            }
    ) {
        // Idle focus holder, a sibling of the overlays and never their parent:
        // Compose maps Back to FocusDirection.Exit, which moves focus to the
        // nearest focusable ancestor and swallows the key. With a focusable
        // root, the first Back only moved focus to it and BackHandler needed a
        // second press. The root above still reads every key (preview pass).
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(rootFocus)
                .focusable(),
        )
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    // Compose draws the controls; the view only renders video and captions.
                    useController = false
                    subtitleView?.applyStyle(subtitleStyle)
                    isFocusable = false
                    descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    playerViewRef.value = this
                }
            },
            // The box sleeps after screen_off_timeout without a key press (Bbox: 4 h).
            // Hold the screen on while an Episode plays; paused or ended, it may sleep.
            update = { view -> view.keepScreenOn = isPlaying },
        )
        // Mobile: taps on the video (PlayerTouch). Under the overlays, so their buttons
        // take their own taps. The box has no such layer: the remote keys above drive it.
        if (LocalMobile.current) {
            val touchState by rememberUpdatedState(
                TouchState(optionsOpen, showResumeChoice || nextUp),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                val now = touchState
                                when (PlayerTouch.tap(now.optionsOpen, now.prompt)) {
                                    PlayerTouch.Tap.NONE -> Unit
                                    PlayerTouch.Tap.TOGGLE_PLAY -> togglePlayPause()
                                    PlayerTouch.Tap.CLOSE_OPTIONS -> closeOptions()
                                }
                            },
                            onDoubleTap = { offset ->
                                val now = touchState
                                val seek = PlayerTouch.doubleTap(offset.x, size.width.toFloat(), now.optionsOpen, now.prompt)
                                if (seek != PlayerTouch.Seek.NONE) {
                                    if (seek == PlayerTouch.Seek.BACK) player.seekBack() else player.seekForward()
                                    positionMs = player.currentPosition
                                    interaction++
                                    flash = seek
                                    flashTick++
                                }
                            },
                        )
                    },
            )
            SeekFlash(flash, Modifier.fillMaxSize())
        }
        // One centre slot (CentreGlyph): the pause glyph while paused, else the Flux resolution's
        // ring while buffering (Round 8 Q3). Under the overlays and the back arrow, over the tap
        // layer; it takes no pointer input, so taps reach the video.
        PlayerCentre(glyph, playFlash, Modifier.align(Alignment.Center))
        val upcoming = next
        val secondsLeft = nextUpSeconds
        if (upcoming != null && secondsLeft != null) {
            NextUp(
                next = upcoming,
                secondsLeft = secondsLeft,
                onPlay = { playNext(upcoming.episode) },
                onCancel = { cancelNextUp() },
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
        } else if (optionsOpen) {
            OptionsPanel(
                rows = optionRows(
                    tracks = trackRefs,
                    audioKind = audioKind,
                    subtitleKind = subtitleKind.takeIf { subtitlesOn },
                    maxHeight = preferences.maxHeight,
                    speed = speed,
                    style = subtitleStyle,
                    onAudio = { chooseAudio(it, remember = true) },
                    onSubtitle = { chooseSubtitle(it, remember = true) },
                    onStyle = { ReplayboxApplication.subtitleStyle.set(it) },
                    onQuality = { chooseQuality(it) },
                    onSpeed = { chooseSpeed(it) },
                ),
                modifier = Modifier.align(Alignment.BottomStart),
                // Mobile: a tap between two chips stays in the panel; above the rows it closes it.
                rowsModifier = if (mobile) Modifier.pointerInput(Unit) { detectTapGestures { } } else Modifier,
            )
        } else if (controlsVisible) {
            PlayerControls(
                showTitle = progressContext?.showTitle,
                episodeTitle = progressContext?.episodeTitle ?: "",
                positionMs = positionMs,
                durationMs = durationMs,
                subtitlesOn = if (hasTextTracks) subtitlesOn else null,
                rowRequester = rowFocus,
                onRowFocusChanged = { focusInRow = it },
                onInteract = { interaction++ },
                onSeekBack = { player.seekBack(); positionMs = player.currentPosition },
                onSeekForward = { player.seekForward(); positionMs = player.currentPosition },
                onRestart = { player.seekTo(0); player.play(); positionMs = 0 },
                onToggleSubtitles = { toggleSubtitles() },
                onOptions = { openOptions() },
                onSeekTo = { player.seekTo(it); positionMs = it; interaction++ },
                onScrubbing = { scrubbing = it; interaction++ },
                modifier = Modifier.align(Alignment.BottomStart),
            )
            // Mobile only, with the controls: not over Options, the resume prompt or Épisode suivant.
            if (mobile) PlayerBackArrow(onBack, Modifier.align(Alignment.TopStart))
        }
    }
}

/**
 * Mobile « Retour » with the controls, while the Flux resolves and on the error page: the rail's
 * back arrow at the same size, on a ground 60 % circle like the seek flash so it reads over any
 * frame. One tap leaves the player; no pressed state.
 */
@Composable
private fun PlayerBackArrow(onBack: () -> Unit, modifier: Modifier) {
    val rail = LocalRail.current
    Box(
        modifier
            .padding(start = Rb.safeHorizontal, top = Rb.safeVertical)
            .size(rail.item)
            .background(VideoScrim, CircleShape)
            // No pressed state (DESIGN.md): no square ripple around the circle.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onBack,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.ArrowBack,
            contentDescription = stringResource(R.string.back),
            tint = Rb.text,
            modifier = Modifier.size(rail.icon),
        )
    }
}

/** Ground at 60 %: the seek flash, the back arrow and the centre glyph read over any frame. */
internal val VideoScrim = Rb.ground.copy(alpha = 0.6f)

/** What a tap on the video needs to decide (read through rememberUpdatedState). */
private data class TouchState(val optionsOpen: Boolean, val prompt: Boolean)

private const val SEEK_FLASH_MS = 600L

/** « −10 s » on the left or « +10 s » on the right after a double-tap; nothing otherwise. */
@Composable
private fun SeekFlash(seek: PlayerTouch.Seek, modifier: Modifier) {
    if (seek == PlayerTouch.Seek.NONE) return
    Box(modifier.padding(horizontal = RailMetrics.Tv.contentStart)) {
        Text(
            text = stringResource(if (seek == PlayerTouch.Seek.BACK) R.string.control_back10 else R.string.control_fwd10),
            style = RbType.displayCompact,
            color = Rb.text,
            modifier = Modifier
                .align(if (seek == PlayerTouch.Seek.BACK) Alignment.CenterStart else Alignment.CenterEnd)
                .background(VideoScrim, Rb.pillShape)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        )
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

/** How often the playhead is checked against the Épisode suivant window. */
private const val NEXT_UP_TICK_MS = 250L

/**
 * Épisode suivant panel (DESIGN.md world), over the last 10 s of the video: the next
 * Épisode's numbering (« Saison 3 · Épisode 1 ») and title, [secondsLeft] before it starts;
 * « Lire maintenant » is focused, « Annuler » or Back lets this Épisode finish.
 */
@Composable
private fun NextUp(next: SeasonEpisode, secondsLeft: Int, onPlay: () -> Unit, onCancel: () -> Unit, modifier: Modifier) {
    val play = remember { FocusRequester() }
    val episode = next.episode
    LaunchedEffect(episode.id) {
        withFrameNanos { }
        runCatching { play.requestFocus() }
    }
    Column(
        modifier = modifier
            .padding(end = Rb.safeHorizontal, bottom = Rb.safeVertical + 24.dp)
            .textWidth(420.dp)
            .background(Rb.surface, Rb.cardShape)
            .padding(24.dp),
    ) {
        Text(stringResource(R.string.next_up_title), style = RbType.railTitle, color = Rb.text)
        longNumbering(next.season?.number, episode.number)?.let {
            Text(it, style = RbType.meta, color = Rb.textSecondary, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
        }
        Text(
            episode.title,
            style = RbType.body,
            color = Rb.text,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            pluralStringResource(R.plurals.next_up_countdown, secondsLeft, secondsLeft),
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

/** Captions lift while the Options panel is open (taller than the controls). */
private const val CAPTIONS_ABOVE_OPTIONS = 0.62f

/** Media3 tracks to Media3-free [TrackRef]s; unsupported tracks are never offered (Q10). */
private fun androidx.media3.common.Tracks.toTrackRefs(): List<TrackRef> =
    groups.flatMapIndexed { groupIndex, group ->
        val kind = when (group.type) {
            C.TRACK_TYPE_AUDIO -> TrackKind.AUDIO
            C.TRACK_TYPE_TEXT -> TrackKind.TEXT
            C.TRACK_TYPE_VIDEO -> TrackKind.VIDEO
            else -> return@flatMapIndexed emptyList()
        }
        (0 until group.length).filter { group.isTrackSupported(it) }.map { index ->
            val format = group.getTrackFormat(index)
            TrackRef(
                type = kind,
                group = groupIndex,
                index = index,
                language = format.language,
                label = format.label,
                describesVideo = format.roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO != 0,
                height = format.height.coerceAtLeast(0),
            )
        }
    }

/** Fallback name for a language outside the France TV codes: label, else its French name. */
private fun otherName(track: TrackRef): String =
    track.label?.takeIf { it.isNotBlank() }
        ?: track.language?.let { java.util.Locale(it).getDisplayLanguage(java.util.Locale.FRENCH) }
            ?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }
        ?: "?"

/** Rows of the Options panel; a row with nothing to choose is left out (Q6, Q10). */
@Composable
private fun optionRows(
    tracks: List<TrackRef>,
    audioKind: AudioKind?,
    subtitleKind: SubtitleKind?,
    maxHeight: Int?,
    speed: Float,
    style: SubtitleStyle,
    onAudio: (AudioChoice) -> Unit,
    onSubtitle: (SubtitleChoice?) -> Unit,
    onStyle: (SubtitleStyle) -> Unit,
    onQuality: (Int?) -> Unit,
    onSpeed: (Float) -> Unit,
): List<OptionRow> {
    val rows = mutableListOf<OptionRow>()
    val audio = PlaybackChoices.audio(tracks)
    if (audio.size >= 2) {
        rows += OptionRow(
            stringResource(R.string.options_audio),
            audio.map { choice ->
                val label = when (choice.kind) {
                    AudioKind.FRENCH -> stringResource(R.string.audio_french)
                    AudioKind.ORIGINAL -> stringResource(R.string.audio_original)
                    AudioKind.DESCRIPTION -> stringResource(R.string.audio_description)
                    AudioKind.OTHER -> otherName(choice.track)
                }
                OptionChip(label, selected = choice.kind == audioKind && choice.kind != AudioKind.OTHER) { onAudio(choice) }
            },
        )
    }
    val subtitles = PlaybackChoices.subtitles(tracks)
    if (subtitles.isNotEmpty()) {
        rows += OptionRow(
            stringResource(R.string.options_subtitles),
            listOf(OptionChip(stringResource(R.string.subtitles_off), selected = subtitleKind == null) { onSubtitle(null) }) +
                subtitles.map { choice ->
                    val label = when (choice.kind) {
                        SubtitleKind.FRENCH -> stringResource(R.string.audio_french)
                        SubtitleKind.HEARING_IMPAIRED -> stringResource(R.string.subtitles_hearing)
                        SubtitleKind.OTHER -> otherName(choice.track)
                    }
                    OptionChip(label, selected = choice.kind == subtitleKind && choice.kind != SubtitleKind.OTHER) { onSubtitle(choice) }
                },
        )
        rows += OptionRow(
            stringResource(R.string.subtitle_size),
            SubtitleSize.values().map { size ->
                OptionChip(sizeLabel(size), selected = size == style.size) { onStyle(style.copy(size = size)) }
            },
        )
        rows += OptionRow(
            stringResource(R.string.subtitle_background),
            SubtitleBackground.values().map { background ->
                OptionChip(backgroundLabel(background), selected = background == style.background) {
                    onStyle(style.copy(background = background))
                }
            },
        )
    }
    val qualities = PlaybackChoices.qualities(tracks)
    if (qualities.isNotEmpty()) {
        val shown = PlaybackChoices.selectedQuality(qualities, maxHeight)
        rows += OptionRow(
            stringResource(R.string.options_quality),
            listOf(OptionChip(stringResource(R.string.quality_auto), selected = shown == null) { onQuality(null) }) +
                qualities.map { height ->
                    OptionChip(stringResource(R.string.quality_height, height), selected = shown == height) { onQuality(height) }
                },
        )
    }
    rows += OptionRow(
        stringResource(R.string.options_speed),
        PlaybackChoices.SPEEDS.map { value ->
            OptionChip(PlaybackChoices.speedLabel(value), selected = value == speed) { onSpeed(value) }
        },
    )
    return rows
}
