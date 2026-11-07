package com.replaybox.app.ui

import androidx.compose.material.icons.filled.Refresh
import android.view.KeyEvent as NativeKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyListState
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.replaybox.R
import com.replaybox.app.ReplayboxApplication
import com.replaybox.app.update.UpdateInstaller
import com.replaybox.app.update.UpdateState
import com.replaybox.core.data.CatalogStore
import com.replaybox.core.data.GenreRail
import com.replaybox.core.model.ChannelShow
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.Genre
import com.replaybox.core.model.ProgressContext
import com.replaybox.core.model.ResumeItem
import com.replaybox.core.model.ShowId
import com.replaybox.provider.m6.M6Channels
import com.replaybox.provider.tf1.Tf1Channels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Home state hoisted above the navigation so that coming back from a
 * Programme restores the scroll positions and the focused card (brief:
 * "focus restored on return").
 */
class HomeUiState {
    val rails = TvLazyListState()
    private val rows = HashMap<String, TvLazyListState>()
    private val requesters = HashMap<String, FocusRequester>()
    var focusedKey: String? = null
    var data: HomeData? = null
    var refreshed = false

    /** Chaînes whose listing this process refreshed: an M6+ sign-in adds new ones to refresh. */
    var refreshedChannels: Set<String> = emptySet()

    /** Every Chaîne failed the last refresh (Round 8 Q4). */
    var unavailable = false

    fun row(key: String): TvLazyListState = rows.getOrPut(key) { TvLazyListState() }
    fun requester(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }
    fun resetRows() = rows.clear()
}

data class HomeData(
    val resume: List<ResumeItem>,
    val favorites: List<ChannelShow>,
    val genres: List<GenreRail>,
    val stale: StaleNotice?,
)

data class StaleNotice(val asOf: Long, val sources: String, val sourceCount: Int)

/** What the top detail panel shows: the billboard item, or the focused card. */
private data class Featured(
    val title: String,
    /** One metadata line under the title: "France 2 · Émission du 25 octobre". */
    val meta: String?,
    val detail: String?,
    val backdropUrl: String?,
    /** Set for Reprendre items: remaining time and progress in the panel. */
    val resume: ResumeItem? = null,
)

private sealed interface Billboard {
    data class Resume(val item: ResumeItem) : Billboard
    data class Favorite(val entry: ChannelShow) : Billboard
}

private const val KEY_PRIMARY = "cta:primary"
private const val KEY_SECONDARY = "cta:secondary"

@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpenShow: (ShowId, String) -> Unit,
    onPlay: (EpisodeId, String, ProgressContext) -> Unit,
    onNavigate: (RailDestination) -> Unit,
    onOpenGenre: (Genre) -> Unit,
) {
    val catalog = Providers.catalog
    val updates by catalog.updates.collectAsState()
    val updateState by ReplayboxApplication.updater.state.collectAsState()
    var data by remember { mutableStateOf(state.data) }
    var refreshing by remember { mutableStateOf(!state.refreshed) }
    var unavailable by remember { mutableStateOf(state.unavailable) }
    var refreshRun by remember { mutableStateOf(0) }
    var refocus by remember { mutableStateOf(0) }
    val retryFocus = remember { FocusRequester() }
    var focused by remember { mutableStateOf<Featured?>(null) }
    var removing by remember { mutableStateOf<ResumeItem?>(null) }
    val scope = rememberCoroutineScope()
    // Read once per visit: Providers.all follows the M6+ account (Paramètres), and Home
    // leaves composition while Paramètres is open.
    val providers = remember { Providers.all }
    val channelNames = remember { providers.associate { it.channel.id to it.channel.name } }

    // ADR 0002: background refresh once per process, Home listings first.
    // The rails compose instantly from the cache; catalog.updates re-ticks them.
    LaunchedEffect(refreshRun) {
        // Once per process, unless the viewer asks again (« Réessayer »); after an
        // M6+ sign-in, only the Chaînes not refreshed yet.
        val pending = if (state.refreshed && refreshRun == 0) {
            providers.filter { it.channel.id !in state.refreshedChannels }
        } else {
            providers
        }
        if (pending.isEmpty()) return@LaunchedEffect
        val full = pending.size == providers.size
        if (full) refreshing = true
        val failed = withContext(Dispatchers.IO) { catalog.refreshListings(pending) }
        state.refreshedChannels = state.refreshedChannels + pending.map { it.channel.id }
        if (full) {
            unavailable = providers.isNotEmpty() && failed == providers.size
            state.unavailable = unavailable
        }
        state.refreshed = true
        refreshing = false
        // Réessayer left composition with the skeleton: focus the loaded Home.
        if (refreshRun > 0 && !unavailable) refocus++
    }
    LaunchedEffect(updates) {
        val loaded = withContext(Dispatchers.IO) { loadHome(catalog) }
        state.data = loaded
        data = loaded
    }

    val current = data
    val billboard: Billboard? = current?.let {
        it.resume.firstOrNull()?.let(Billboard::Resume)
            ?: it.favorites.firstOrNull()?.let(Billboard::Favorite)
    }
    val billboardFeatured = billboard?.let { featuredOf(it, channelNames) }
    val shown = focused ?: billboardFeatured

    // Initial / restored focus once data is on screen: the remembered card,
    // else the billboard's primary action, else the first card. A key whose
    // card is gone (removed, delisted) falls through to the next candidate.
    val firstCardKey = current?.let { firstCardKey(it) }
    LaunchedEffect(current != null, billboard != null, refocus) {
        if (current == null) return@LaunchedEffect
        withFrameNanos { }
        val candidates = listOfNotNull(state.focusedKey, KEY_PRIMARY.takeIf { billboard != null }, firstCardKey)
        candidates.firstOrNull { key -> runCatching { state.requester(key).requestFocus() }.isSuccess }
    }

    // Back: from anywhere but the top of Home, return to the top; from the top, leave.
    val atTop = state.focusedKey == null ||
        state.focusedKey == KEY_PRIMARY ||
        state.focusedKey == KEY_SECONDARY ||
        (billboard == null && state.focusedKey == firstCardKey && state.rails.firstVisibleItemIndex == 0)
    BackHandler(enabled = !atTop) {
        scope.launch {
            state.rails.scrollToItem(0)
            state.resetRows()
            focused = null
            state.focusedKey = null
            refocus++
        }
    }

    fun track(key: String, featured: Featured?) {
        state.focusedKey = key
        focused = featured
    }

    Box(Modifier.fillMaxSize().background(Rb.ground)) {
        Backdrop(url = shown?.backdropUrl)
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = Rb.contentStart - GUTTER),
        ) {
            DetailPanel(
                featured = shown,
                billboard = billboard.takeIf { focused == null },
                state = state,
                stale = current?.stale,
                update = updateState as? UpdateState.Ready,
                onFocusButton = { key -> track(key, null) },
                onPlay = { item -> onPlay(item.episodeId, item.episodeTitle, item.toContext()) },
                onOpenShow = onOpenShow,
            )
            when {
                current == null || (current.genres.isEmpty() && refreshing) -> SkeletonRails()
                current.genres.isEmpty() && current.resume.isEmpty() && current.favorites.isEmpty() -> Column(
                    Modifier.padding(start = GUTTER, top = 16.dp),
                ) {
                    // A failed refresh is never presented as an empty catalogue.
                    Text(
                        text = stringResource(if (unavailable) R.string.catalog_unavailable else R.string.catalog_empty),
                        style = RbType.body,
                        color = Rb.textSecondary,
                    )
                    if (unavailable) {
                        LaunchedEffect(Unit) {
                            withFrameNanos { }
                            runCatching { retryFocus.requestFocus() }
                        }
                        Box(Modifier.padding(top = 20.dp)) {
                            PanelButton(
                                label = stringResource(R.string.retry),
                                icon = Icons.Filled.Refresh,
                                requester = retryFocus,
                                onClick = { refreshRun += 1 },
                            )
                        }
                    }
                }
                else -> TvLazyColumn(
                    state = state.rails,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    // Focused card sits ~45 dp below the top so its rail title stays visible.
                    pivotOffsets = PivotOffsets(parentFraction = 0.16f),
                    contentPadding = PaddingValues(bottom = Rb.safeVertical),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (current.resume.isNotEmpty()) {
                        item(key = "rail:resume") {
                            Rail(title = stringResource(R.string.rail_resume), rowState = state.row("resume")) {
                                items(current.resume, key = { it.episodeId.value }) { item ->
                                    val key = "resume:${item.episodeId.value}"
                                    val featured = resumeFeatured(item, channelNames)
                                    ResumeCard(
                                        item = item,
                                        requester = state.requester(key),
                                        onFocused = { track(key, featured) },
                                        onClick = { onPlay(item.episodeId, item.episodeTitle, item.toContext()) },
                                        onOptions = { removing = item },
                                    )
                                }
                            }
                        }
                    }
                    if (current.favorites.isNotEmpty()) {
                        item(key = "rail:favorites") {
                            Rail(title = stringResource(R.string.favorites_row), rowState = state.row("favorites")) {
                                items(current.favorites, key = { it.show.id.value }) { entry ->
                                    val key = "fav:${entry.show.id.value}"
                                    PosterCard(
                                        entry = entry,
                                        requester = state.requester(key),
                                        onFocused = { track(key, showFeatured(entry, channelNames)) },
                                        onClick = { onOpenShow(entry.show.id, entry.show.title) },
                                    )
                                }
                            }
                        }
                    }
                    items(current.genres, key = { "rail:${it.genre.name}" }) { rail ->
                        val genre = rail.genre
                        Rail(title = genre.genreLabel(), rowState = state.row(genre.name)) {
                            items(rail.shows, key = { it.show.id.value }) { entry ->
                                val key = "genre:${genre.name}:${entry.show.id.value}"
                                PosterCard(
                                    entry = entry,
                                    requester = state.requester(key),
                                    onFocused = { track(key, showFeatured(entry, channelNames)) },
                                    onClick = { onOpenShow(entry.show.id, entry.show.title) },
                                )
                            }
                            // Only a rail the cap cut gets the way to the full grid.
                            if (rail.hasMore) {
                                val key = seeAllKey(genre)
                                item(key = key) {
                                    SeeAllCard(
                                        requester = state.requester(key),
                                        onFocused = { track(key, Featured(genre.genreLabel(), null, null, null)) },
                                        onClick = { onOpenGenre(genre) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        var railOpen by remember { mutableStateOf(false) }
        val dim by animateFloatAsState(if (railOpen) 0.72f else 0f, tween(Rb.FOCUS_MS), label = "dim")
        if (dim > 0f) Box(Modifier.fillMaxSize().background(Rb.ground.copy(alpha = dim)))
        NavRail(
            current = RailDestination.HOME,
            onNavigate = onNavigate,
            modifier = Modifier.align(Alignment.CenterStart),
            onExpandedChange = { railOpen = it },
        )
    }

    removing?.let { item ->
        // Both actions take the card off the rail: focus falls back like after a removal.
        fun leaveRail(action: suspend () -> Unit) {
            removing = null
            scope.launch { action() }
            focused = null
            state.focusedKey = null
            refocus++
        }
        ActionDialog(
            title = item.showTitle,
            hint = item.episodeTitle.takeIf { it != item.showTitle },
            actions = listOf(
                DialogAction(stringResource(R.string.mark_watched)) {
                    leaveRail { catalog.markWatched(item.episodeId, item.durationSeconds, item.toContext()) }
                },
                DialogAction(stringResource(R.string.remove_resume_confirm)) {
                    leaveRail { catalog.removeFromContinueWatching(item.episodeId) }
                },
            ),
            onDismiss = { removing = null },
        )
    }
}

private suspend fun loadHome(catalog: CatalogStore): HomeData {
    val channelIds = Providers.all.map { it.channel.id }
    val now = System.currentTimeMillis()
    val staleSources = LinkedHashMap<String, Long>()
    for (id in channelIds) {
        val at = catalog.listingAt(id) ?: continue
        if (now - at > CatalogStore.TTL_MS) {
            val source = when {
                Tf1Channels.isGroup(id) -> "TF1"
                M6Channels.isGroup(id) -> "M6+"
                else -> "France TV"
            }
            staleSources[source] = minOf(at, staleSources[source] ?: at)
        }
    }
    return HomeData(
        resume = catalog.continueWatching(channelIds),
        favorites = catalog.favoriteChannelShows(channelIds),
        genres = catalog.genreRails(channelIds),
        stale = staleSources.takeIf { it.isNotEmpty() }?.let {
            StaleNotice(it.values.min(), it.keys.joinToString(" et "), it.size)
        },
    )
}

private fun firstCardKey(data: HomeData): String? =
    data.resume.firstOrNull()?.let { "resume:${it.episodeId.value}" }
        ?: data.favorites.firstOrNull()?.let { "fav:${it.show.id.value}" }
        ?: data.genres.firstOrNull()?.let { rail -> "genre:${rail.genre.name}:${rail.shows.first().show.id.value}" }

/** Home focus key of the « Tout voir » card of a Genre rail: Back from the grid lands on it. */
private fun seeAllKey(genre: Genre): String = "more:${genre.name}"

private fun ResumeItem.toContext() = ProgressContext(showId, showTitle, episodeTitle, thumbnailUrl)

private fun featuredOf(billboard: Billboard, names: Map<String, String>): Featured = when (billboard) {
    is Billboard.Resume -> resumeFeatured(billboard.item, names)
    is Billboard.Favorite -> showFeatured(billboard.entry, names)
}

private fun resumeFeatured(item: ResumeItem, names: Map<String, String>) = Featured(
    title = item.showTitle,
    meta = listOfNotNull(
        item.channelId?.let { names[it] },
        item.episodeTitle.takeIf { it.isNotBlank() && it != item.showTitle },
    ).joinToString(" · ").ifBlank { null },
    detail = null,
    backdropUrl = item.backdropUrl,
    resume = item,
)

private fun showFeatured(entry: ChannelShow, names: Map<String, String>) = Featured(
    title = entry.show.title,
    meta = listOfNotNull(names[entry.channelId], entry.show.genres.firstOrNull()?.genreLabel())
        .joinToString(" · ").ifBlank { null },
    detail = entry.show.synopsis,
    backdropUrl = entry.show.backdropUrl ?: entry.show.posterUrl,
)

// ---- Backdrop ----

private val PANEL_HEIGHT = 252.dp

// ---- Detail panel ----

@Composable
private fun DetailPanel(
    featured: Featured?,
    billboard: Billboard?,
    state: HomeUiState,
    stale: StaleNotice?,
    update: UpdateState.Ready?,
    onFocusButton: (String) -> Unit,
    onPlay: (ResumeItem) -> Unit,
    onOpenShow: (ShowId, String) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(PANEL_HEIGHT)
            .padding(start = GUTTER, top = Rb.safeVertical, end = Rb.safeHorizontal),
    ) {
        Column(Modifier.width(540.dp).align(Alignment.TopStart).padding(top = 8.dp)) {
            if (featured != null) {
                var compact by remember(featured.title) { mutableStateOf(false) }
                Text(
                    text = featured.title,
                    style = if (compact) RbType.displayCompact else RbType.display,
                    color = Rb.text,
                    maxLines = if (compact) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!compact && it.hasVisualOverflow) compact = true },
                )
                featured.meta?.let {
                    Text(
                        it,
                        style = RbType.bodyStrong,
                        color = Rb.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                featured.detail?.let {
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
            val focusedResume = featured?.resume
            if (billboard == null && focusedResume != null) {
                Box(Modifier.padding(top = 16.dp)) { ResumeProgress(focusedResume, inset = false) }
            }
            if (billboard != null) {
                Row(
                    modifier = Modifier.padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (billboard) {
                        is Billboard.Resume -> {
                            PanelButton(
                                label = stringResource(R.string.action_resume),
                                icon = Icons.Filled.PlayArrow,
                                requester = state.requester(KEY_PRIMARY),
                                onFocused = { onFocusButton(KEY_PRIMARY) },
                                onClick = { onPlay(billboard.item) },
                            )
                            PanelButton(
                                label = stringResource(R.string.action_episodes),
                                icon = null,
                                requester = state.requester(KEY_SECONDARY),
                                onFocused = { onFocusButton(KEY_SECONDARY) },
                                onClick = { onOpenShow(billboard.item.showId, billboard.item.showTitle) },
                            )
                            ResumeProgress(billboard.item)
                        }
                        is Billboard.Favorite -> PanelButton(
                            label = stringResource(R.string.action_episodes),
                            icon = null,
                            requester = state.requester(KEY_PRIMARY),
                            onFocused = { onFocusButton(KEY_PRIMARY) },
                            onClick = { onOpenShow(billboard.entry.show.id, billboard.entry.show.title) },
                        )
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.End) {
            stale?.let {
                Text(
                    text = stringResource(
                        if (it.sourceCount > 1) R.string.catalog_stale_many else R.string.catalog_stale_one,
                        shortDate(it.asOf),
                        it.sources,
                    ),
                    style = RbType.meta,
                    color = Rb.textSecondary,
                    modifier = Modifier
                        .background(Rb.ground.copy(alpha = 0.85f), Rb.pillShape)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            update?.let { UpdateChip(it) }
        }
    }
}

@Composable
private fun ResumeProgress(item: ResumeItem, inset: Boolean = true) {
    val ratio = item.watchedRatio ?: return
    Column(Modifier.padding(start = if (inset) 8.dp else 0.dp)) {
        remainingText(item.remainingSeconds)?.let { Text(it, style = RbType.meta, color = Rb.text) }
        ProgressBar(ratio, width = 120.dp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun UpdateChip(update: UpdateState.Ready) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    // Focus is never shown by color alone (PRODUCT.md): the white fill also lifts.
    val scale by animateFloatAsState(if (isFocused) Rb.BUTTON_FOCUS_SCALE else 1f, tween(Rb.FOCUS_MS), label = "chip")
    Row(
        modifier = Modifier
            .padding(top = 8.dp)
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .tvClickable { UpdateInstaller.install(context, update.apk) }
            .background(if (isFocused) Rb.focus else Rb.surfaceRaised, Rb.pillShape)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (isFocused) Rb.onFocus else Rb.text
        Text(stringResource(R.string.update_ready, update.version.toString()), style = RbType.meta, color = color)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.update_install), style = RbType.meta.copy(fontWeight = RbType.bodyStrong.fontWeight), color = color)
    }
}

// ---- Rails and cards ----

@Composable
private fun Rail(
    title: String,
    rowState: TvLazyListState,
    content: androidx.tv.foundation.lazy.list.TvLazyListScope.() -> Unit,
) {
    Column {
        Text(
            text = title,
            style = RbType.railTitle,
            color = Rb.text,
            modifier = Modifier.padding(start = GUTTER, top = 4.dp),
        )
        TvLazyRow(
            state = rowState,
            // The pivot places the *scaled* card (1.1, ~6 dp wider each side): 0.015 of
            // the ~876 dp row puts it at ~13 dp, so the card at rest sits at ~19 dp and
            // the previous card (gap 20 dp) ends under the clip edge, no sliver.
            // Measured on the Bbox: 0.021 left an 8 px sliver.
            pivotOffsets = PivotOffsets(parentFraction = 0.015f),
            contentPadding = PaddingValues(start = GUTTER, end = Rb.safeHorizontal, top = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(Rb.cardGap),
            content = content,
        )
    }
}

@Composable
private fun ResumeCard(
    item: ResumeItem,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onOptions: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale = focusScale(isFocused)
    Column(
        Modifier
            .width(176.dp)
            .focusRequester(requester)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onPreviewKeyEvent { event ->
                val code = event.nativeKeyEvent.keyCode
                if (event.type == KeyEventType.KeyDown && code == NativeKeyEvent.KEYCODE_MENU) {
                    onOptions()
                    true
                } else {
                    false
                }
            }
            .tvClickable(onClick),
    ) {
        Box(
            Modifier
                .scale(scale)
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .cardFocus(isFocused)
                .background(Rb.surfaceRaised),
        ) {
            Artwork(url = item.thumbnailUrl, fallback = item.showTitle)
            val ratio = item.watchedRatio
            if (ratio != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Rb.track),
                ) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(ratio).background(Rb.accent))
                }
            }
        }
        Text(
            text = item.showTitle,
            style = RbType.cardTitle,
            color = Rb.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 14.dp),
        )
        // An expiry pill replaces the remaining time; the bar above still shows Progression.
        val expiry = cardBadge(item.availableUntil, Instant.now())
        if (expiry != null) {
            FreshnessPill(expiry, modifier = Modifier.padding(top = 2.dp))
        } else {
            remainingText(item.remainingSeconds)?.let {
                Text(it, style = RbType.meta, color = Rb.textSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PosterCard(
    entry: ChannelShow,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale = focusScale(isFocused)
    Box(
        Modifier
            .width(116.dp)
            .aspectRatio(3f / 4f)
            .focusRequester(requester)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .tvClickable(onClick)
            .scale(scale)
            .cardFocus(isFocused)
            .background(Rb.surfaceRaised),
    ) {
        Artwork(url = entry.show.posterUrl, fallback = entry.show.title)
    }
}

/** « Tout voir » at the end of a truncated Genre rail: a poster-sized Raised Slate plate. */
@Composable
private fun SeeAllCard(requester: FocusRequester, onFocused: () -> Unit, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale = focusScale(isFocused)
    Box(
        Modifier
            .width(116.dp)
            .aspectRatio(3f / 4f)
            .focusRequester(requester)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .tvClickable(onClick)
            .scale(scale)
            .cardFocus(isFocused)
            .background(Rb.surfaceRaised)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.see_all),
            style = RbType.cardTitle,
            color = Rb.text,
            maxLines = 2,
        )
    }
}

/** Placeholder rails laid out exactly like [Rail], clipped to the screen. */
@Composable
private fun ColumnScope.SkeletonRails() {
    Column(
        Modifier.fillMaxWidth().weight(1f).clipToBounds(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SkeletonRail(titleWidth = 140.dp, cards = 6, cardWidth = 176.dp, ratio = 16f / 9f)
        SkeletonRail(titleWidth = 100.dp, cards = 8, cardWidth = 116.dp, ratio = 3f / 4f)
    }
}

@Composable
private fun SkeletonRail(titleWidth: Dp, cards: Int, cardWidth: Dp, ratio: Float) {
    Column {
        Box(
            Modifier
                .padding(start = GUTTER, top = 7.dp, bottom = 3.dp)
                .width(titleWidth)
                .height(16.dp)
                .background(Rb.surface, Rb.pillShape),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clipToBounds()
                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                .padding(start = GUTTER, top = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(Rb.cardGap),
        ) {
            repeat(cards) {
                Box(Modifier.width(cardWidth).aspectRatio(ratio).background(Rb.surface, Rb.cardShape))
            }
        }
    }
}

/** ADR 0002: stale cache is served with its date — "05/11". */
private fun shortDate(asOf: Long): String =
    DateTimeFormatter.ofPattern("dd/MM")
        .withZone(ZoneId.of("Europe/Paris"))
        .format(Instant.ofEpochMilli(asOf))

internal fun Genre.genreLabel(): String = when (this) {
    Genre.SERIES -> "Séries"
    Genre.MOVIES -> "Films"
    Genre.CRIME_THRILLER -> "Policier & Thriller"
    Genre.COMEDY -> "Comédie"
    Genre.DRAMA -> "Drame"
    Genre.DOCUMENTARY -> "Documentaire"
    Genre.YOUTH -> "Jeunesse"
    Genre.ENTERTAINMENT -> "Divertissement"
    Genre.SPORT_AND_INFO -> "Sport & Info"
}
