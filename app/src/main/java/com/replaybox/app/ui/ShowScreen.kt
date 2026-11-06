package com.replaybox.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyListState
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import com.replaybox.R
import com.replaybox.core.model.ChannelShow
import com.replaybox.core.model.Episode
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.EpisodeProgress
import com.replaybox.core.model.ProgressContext
import com.replaybox.core.model.ResumeItem
import com.replaybox.core.model.Season
import com.replaybox.core.model.SeasonId
import com.replaybox.core.model.ShowId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Per-Programme UI state kept by MainActivity across trips to the player, so
 * Back from playback lands on the Épisode just watched (DESIGN.md: focus is
 * never lost).
 */
class ShowUiState {
    val list = TvLazyListState()
    var focusedKey by mutableStateOf<String?>(null)
    var selectedSeason by mutableStateOf<SeasonId?>(null)
    private val requesters = HashMap<String, FocusRequester>()

    fun requester(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }
}

private const val KEY_PLAY = "show:play"
private const val KEY_PIN = "show:pin"
private fun episodeKey(episode: Episode) = "ep:${episode.id.value}"

/** Items before the first Épisode row: hero, then Saisons pills or the "Épisodes" title. */
private const val FIRST_EPISODE_INDEX = 2

/** What the hero's primary action plays: the Reprendre target, else the first listed Épisode. */
private sealed interface PlayTarget {
    data class Resume(val item: ResumeItem) : PlayTarget
    data class Start(val episode: Episode) : PlayTarget
}

/**
 * Programme page: a hero (key art, title, Chaîne · Genre, synopsis, one-press
 * play) over the Saisons and the Épisode list, each row carrying its
 * Progression. Everything but the Saisons/Épisodes calls comes from the cache.
 */
@Composable
fun ShowScreen(
    showId: ShowId,
    title: String,
    state: ShowUiState,
    /** Third argument: true for the hero « Reprendre » (resume without asking). */
    onOpenEpisode: (Episode, ProgressContext, Boolean) -> Unit,
) {
    val provider = Providers.resolveShow(showId.value)
    val catalog = Providers.catalog
    val channelIds = remember { Providers.all.map { it.channel.id } }
    val channelNames = remember { Providers.all.associate { it.channel.id to it.channel.name } }
    val updates by catalog.updates.collectAsState()
    val scope = rememberCoroutineScope()

    var listed by remember { mutableStateOf<ChannelShow?>(null) }
    var seasons by remember { mutableStateOf<List<Season>?>(null) }
    var episodes by remember { mutableStateOf<List<Episode>?>(null) }
    var progress by remember { mutableStateOf<Map<EpisodeId, EpisodeProgress>>(emptyMap()) }
    var resume by remember { mutableStateOf<ResumeItem?>(null) }
    var pinned by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(showId) {
        listed = catalog.listedShow(showId, channelIds)
        pinned = catalog.isFavorite(showId)
    }
    LaunchedEffect(showId, attempt) {
        failed = false
        try {
            val loaded = provider.seasons(showId)
            seasons = loaded
            if (state.selectedSeason == null || loaded.none { it.id == state.selectedSeason }) {
                state.selectedSeason = loaded.firstOrNull()?.id
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        }
    }
    LaunchedEffect(seasons, state.selectedSeason, attempt) {
        // A Programme without Saisons lists its Épisodes under a null Saison.
        val loadedSeasons = seasons ?: return@LaunchedEffect
        if (loadedSeasons.isNotEmpty() && state.selectedSeason == null) return@LaunchedEffect
        episodes = null
        failed = false
        try {
            val loaded = provider.episodes(state.selectedSeason)
            // Progression first, then the rows: they never paint without bars.
            progress = catalog.episodeProgress(loaded.map { it.id })
            episodes = loaded
        } catch (e: CancellationException) {
            // Superseded by another Saison: not an error.
            throw e
        } catch (e: Exception) {
            failed = true
        }
    }
    // Progression and the Reprendre target re-read on every catalog tick, so
    // returning from the player shows the position just saved.
    LaunchedEffect(episodes, updates) {
        resume = catalog.resumeFor(showId, channelIds)
        progress = catalog.episodeProgress(episodes.orEmpty().map { it.id })
    }

    val list = episodes
    val playTarget: PlayTarget? = resume?.let { PlayTarget.Resume(it) }
        ?: list?.firstOrNull()?.let { PlayTarget.Start(it) }

    fun open(episode: Episode) {
        state.focusedKey = episodeKey(episode)
        onOpenEpisode(episode, ProgressContext(showId, title, episode.title, episode.thumbnailUrl), false)
    }

    fun play(target: PlayTarget) {
        state.focusedKey = KEY_PLAY
        when (target) {
            is PlayTarget.Start -> target.episode.let { episode ->
                onOpenEpisode(episode, ProgressContext(showId, title, episode.title, episode.thumbnailUrl), false)
            }
            is PlayTarget.Resume -> {
                val item = target.item
                val episode = list?.firstOrNull { it.id == item.episodeId }
                    ?: Episode(
                        item.episodeId, null, item.episodeTitle.ifBlank { item.showTitle },
                        null, null, item.durationSeconds, item.thumbnailUrl,
                    )
                onOpenEpisode(episode, ProgressContext(showId, title, episode.title, episode.thumbnailUrl ?: item.thumbnailUrl), true)
            }
        }
    }

    // Focus is placed on arrival, after Back to the hero and after Réessayer,
    // never on a Saison reload (focus stays on the pill the user chose).
    var restorePending by remember { mutableStateOf(true) }
    val ready = list != null || failed
    LaunchedEffect(ready, restorePending) {
        if (!ready || !restorePending) return@LaunchedEffect
        val key = state.focusedKey
        val index = list?.indexOfFirst { episodeKey(it) == key } ?: -1
        if (index >= 0) state.list.scrollToItem(FIRST_EPISODE_INDEX + index)
        withFrameNanos { }
        showFocusCandidates(key, failed).firstOrNull { runCatching { state.requester(it).requestFocus() }.isSuccess }
        restorePending = false
    }

    // Back: from the list, return to the hero; from the hero, leave the page.
    val atTop = (state.focusedKey == null || state.focusedKey == KEY_PLAY || state.focusedKey == KEY_PIN) &&
        state.list.firstVisibleItemIndex == 0
    BackHandler(enabled = !atTop) {
        scope.launch {
            state.list.scrollToItem(0)
            state.focusedKey = null
            restorePending = true
        }
    }

    val show = listed?.show
    val scrolled = state.list.firstVisibleItemIndex > 0 || state.list.firstVisibleItemScrollOffset > 0
    val fade by animateFloatAsState(if (scrolled) 1f else 0f, tween(Rb.BACKDROP_MS), label = "scrolled")
    Box(Modifier.fillMaxSize()) {
        Backdrop(url = show?.backdropUrl ?: show?.posterUrl)
        // Rows scroll over the art: sink it under 92 % ground (Contrast Floor Rule).
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = fade }
                .background(Rb.ground.copy(alpha = 0.92f)),
        )
        TvLazyColumn(
            state = state.list,
            modifier = Modifier.fillMaxSize(),
            // The play action sits ~220-245 dp down: aligning focus at 0.45 keeps
            // the whole hero on screen while it is focused (scroll clamps at 0).
            pivotOffsets = PivotOffsets(parentFraction = 0.45f),
            contentPadding = PaddingValues(bottom = Rb.safeVertical),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "hero") {
                Hero(
                    title = title,
                    meta = listOfNotNull(
                        listed?.channelId?.let { channelNames[it] },
                        show?.genres?.firstOrNull()?.genreLabel(),
                    ).joinToString(" · ").ifBlank { null },
                    synopsis = show?.synopsis,
                    playTarget = playTarget,
                    pinned = pinned,
                    state = state,
                    onPlay = { target -> play(target) },
                    onPin = { scope.launch { pinned = catalog.toggleFavorite(showId) } },
                )
            }
            item(key = "seasons") {
                val seasonList = seasons.orEmpty()
                if (seasonList.size > 1) {
                    SeasonPills(seasonList, state.selectedSeason) { state.selectedSeason = it }
                } else {
                    Text(
                        text = stringResource(R.string.episodes_title),
                        style = RbType.railTitle,
                        color = Rb.text,
                        modifier = Modifier.padding(start = Rb.contentStart, top = 8.dp),
                    )
                }
            }
            when {
                failed -> item(key = "error") {
                    LoadError(state.requester(KEY_RETRY)) {
                        // Park focus on Épingler (always on screen) while the
                        // error row is replaced by skeletons, then restore.
                        runCatching { state.requester(KEY_PIN).requestFocus() }
                        state.focusedKey = null
                        restorePending = true
                        attempt++
                    }
                }
                list == null -> items(3) { EpisodeSkeleton() }
                list.isEmpty() -> item(key = "empty") {
                    Text(
                        text = stringResource(R.string.empty_episodes),
                        style = RbType.body,
                        color = Rb.textSecondary,
                        modifier = Modifier.padding(start = Rb.contentStart, top = 8.dp),
                    )
                }
                else -> items(list, key = { episodeKey(it) }) { episode ->
                    EpisodeRow(
                        episode = episode,
                        progress = progress[episode.id],
                        requester = state.requester(episodeKey(episode)),
                        onFocused = { state.focusedKey = episodeKey(episode) },
                        onClick = { open(episode) },
                    )
                }
            }
        }
        // Once the list scrolls, content fades into the ground at the top edge
        // instead of being cut mid-line (The No Pure Black Rule: ground, not #000).
        Box(
            Modifier
                .fillMaxWidth()
                .height(TOP_FADE)
                .graphicsLayer { alpha = fade }
                .background(Brush.verticalGradient(0f to Rb.ground, 0.62f to Rb.ground, 1f to Rb.ground.copy(alpha = 0f))),
        )
    }
}

/** Top fade: solid over the 27 dp safe inset plus one cut line (~40 dp), then
 * dissolving before the first heading (~70 dp) so nothing legible is dimmed. */
private val TOP_FADE = 64.dp

private const val KEY_RETRY = "show:retry"

/**
 * Where focus lands once the page is ready: Réessayer first on an error,
 * else the remembered key (an Épisode or a hero action), the play action,
 * then Épingler (always present). Unavailable keys are skipped by the caller.
 */
internal fun showFocusCandidates(focusedKey: String?, failed: Boolean): List<String> =
    if (failed) {
        listOf(KEY_RETRY, KEY_PIN)
    } else {
        listOfNotNull(focusedKey, KEY_PLAY, KEY_PIN).distinct()
    }

@Composable
private fun Hero(
    title: String,
    meta: String?,
    synopsis: String?,
    playTarget: PlayTarget?,
    pinned: Boolean,
    state: ShowUiState,
    onPlay: (PlayTarget) -> Unit,
    onPin: () -> Unit,
) {
    Column(
        Modifier
            .padding(start = Rb.contentStart, top = Rb.safeVertical + 8.dp, end = Rb.safeHorizontal)
            .width(540.dp),
    ) {
        var compact by remember(title) { mutableStateOf(false) }
        Text(
            text = title,
            style = if (compact) RbType.displayCompact else RbType.display,
            color = Rb.text,
            maxLines = if (compact) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!compact && it.hasVisualOverflow) compact = true },
        )
        meta?.let {
            Text(
                it,
                style = RbType.bodyStrong,
                color = Rb.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        synopsis?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = RbType.body,
                color = Rb.textSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Row(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (playTarget != null) {
                PanelButton(
                    label = stringResource(if (playTarget is PlayTarget.Resume) R.string.action_resume else R.string.action_play),
                    icon = Icons.Filled.PlayArrow,
                    requester = state.requester(KEY_PLAY),
                    onFocused = { state.focusedKey = KEY_PLAY },
                    onClick = { onPlay(playTarget) },
                )
            }
            PanelButton(
                label = stringResource(if (pinned) R.string.pin_remove else R.string.pin_add),
                icon = null,
                requester = state.requester(KEY_PIN),
                onFocused = { state.focusedKey = KEY_PIN },
                onClick = onPin,
            )
        }
        PlayTargetLine(playTarget)
    }
}

/** Names what the play action plays, so the button never hides its target. */
@Composable
private fun PlayTargetLine(target: PlayTarget?) {
    when (target) {
        null -> Unit
        is PlayTarget.Resume -> {
            val item = target.item
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                item.watchedRatio?.let { ProgressBar(it, width = 120.dp, modifier = Modifier.padding(end = 12.dp)) }
                Text(
                    text = listOfNotNull(item.episodeTitle.ifBlank { null }, remainingText(item.remainingSeconds))
                        .joinToString(" · "),
                    style = RbType.meta,
                    color = Rb.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        is PlayTarget.Start -> Text(
            text = target.episode.title,
            style = RbType.meta,
            color = Rb.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

@Composable
private fun SeasonPills(seasons: List<Season>, selected: SeasonId?, onSelect: (SeasonId) -> Unit) {
    TvLazyRow(
        contentPadding = PaddingValues(start = Rb.contentStart, end = Rb.safeHorizontal, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(seasons, key = { it.id.value }) { season ->
            SeasonPill(season.title, season.id == selected) { onSelect(season.id) }
        }
    }
}

@Composable
private fun SeasonPill(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) Rb.BUTTON_FOCUS_SCALE else 1f, tween(Rb.FOCUS_MS), label = "season")
    val background = when {
        focused -> Rb.focus
        selected -> Rb.surfaceRaised
        else -> null
    }
    Text(
        text = label,
        style = RbType.bodyStrong,
        color = when {
            focused -> Rb.onFocus
            selected -> Rb.text
            else -> Rb.textSecondary
        },
        maxLines = 1,
        modifier = Modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick)
            .then(if (background != null) Modifier.background(background, Rb.pillShape) else Modifier)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

/** Row thumbnail width: the Home Reprendre card grown for a list of one per line. */
private val THUMB_WIDTH = 208.dp

/**
 * Inner inset of the focused row plate: the thumbnail stays at 104 dp while
 * its ring (1.1 lift + 3 dp gap + stroke) keeps >= 10 dp from the plate edge.
 */
private val ROW_INSET = 32.dp

/** Ring radius (14.5 dp) plus its distance to the plate, so corners stay concentric. */
private val ROW_SHAPE = RoundedCornerShape(26.dp)

@Composable
private fun EpisodeRow(
    episode: Episode,
    progress: EpisodeProgress?,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale = focusScale(focused)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Rb.contentStart - ROW_INSET, end = Rb.safeHorizontal)
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .tvClickable(onClick)
            .then(if (focused) Modifier.background(Rb.surfaceRaised, ROW_SHAPE) else Modifier)
            .padding(horizontal = ROW_INSET, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(ROW_INSET),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(THUMB_WIDTH)
                .scale(scale)
                .aspectRatio(16f / 9f)
                .cardFocus(focused)
                .background(Rb.surface),
        ) {
            Artwork(url = episode.thumbnailUrl, fallback = episode.title)
            val ratio = when {
                progress == null -> null
                progress.watched -> 1f
                else -> progress.watchedRatio
            }
            ratio?.let {
                ProgressBar(
                    ratio = it,
                    width = THUMB_WIDTH,
                    modifier = Modifier.align(Alignment.BottomStart),
                    fill = if (progress?.watched == true) Rb.textSecondary else Rb.accent,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                episode.title,
                style = RbType.bodyStrong,
                color = Rb.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            EpisodeMeta(episode, progress)
            episode.synopsis?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = RbType.body,
                    color = Rb.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/** "02/11/2026 · 3 min", then the Progression in words: remaining time, or ✓ Vu. */
@Composable
private fun EpisodeMeta(episode: Episode, progress: EpisodeProgress?) {
    val base = listOfNotNull(
        episode.broadcastDate,
        episode.durationSeconds?.let { stringResource(R.string.duration_minutes, (it + 59) / 60) },
    )
    val remaining = if (progress != null && !progress.watched) remainingText(progress.remainingSeconds) else null
    val line = (base + listOfNotNull(remaining)).joinToString(" · ")
    if (line.isEmpty() && progress?.watched != true) return
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(line, style = RbType.meta, color = Rb.textSecondary, maxLines = 1)
        if (progress?.watched == true) {
            if (line.isNotEmpty()) Text(" · ", style = RbType.meta, color = Rb.textSecondary)
            Icon(Icons.Filled.Check, contentDescription = null, tint = Rb.textSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.episode_watched), style = RbType.meta, color = Rb.textSecondary)
        }
    }
}

@Composable
private fun EpisodeSkeleton() {
    Row(
        Modifier.padding(start = Rb.contentStart, top = 14.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(GUTTER),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(THUMB_WIDTH).aspectRatio(16f / 9f).background(Rb.surface, Rb.cardShape))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(320.dp).height(18.dp).background(Rb.surface, Rb.pillShape))
            Box(Modifier.width(180.dp).height(16.dp).background(Rb.surface, Rb.pillShape))
        }
    }
}

@Composable
private fun LoadError(requester: FocusRequester, onRetry: () -> Unit) {
    Column(Modifier.padding(start = Rb.contentStart, top = 8.dp)) {
        Text(stringResource(R.string.show_load_error), style = RbType.body, color = Rb.text)
        Box(Modifier.padding(top = 16.dp)) {
            PanelButton(
                label = stringResource(R.string.retry),
                icon = Icons.Filled.Refresh,
                requester = requester,
                onClick = onRetry,
            )
        }
    }
}
