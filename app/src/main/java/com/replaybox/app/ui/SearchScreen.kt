package com.replaybox.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvGridItemSpan
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.itemsIndexed
import com.replaybox.R
import com.replaybox.core.data.SearchRanking
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.Genre
import com.replaybox.core.model.ProgressContext
import com.replaybox.core.model.ResumeItem
import com.replaybox.core.model.Show
import com.replaybox.core.model.ShowId
import com.replaybox.core.model.Titles
import com.replaybox.provider.api.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Cross-chaîne search (SPEC §Recherche): from 1 character, every Provider asked at once, results
 * fused by normalized title (case/accent-insensitive). A failing Chaîne contributes nothing
 * instead of failing the query.
 */
object SearchFusion {

    /** Local search is instant; the pause only spares one search per key while typing fast. */
    const val DEBOUNCE_MS = 150L

    fun fuse(query: String, providerResults: List<List<Show>>): List<Show> {
        val wanted = Titles.normalize(query)
        val byTitle = LinkedHashMap<String, Show>()
        for (shows in providerResults) {
            for (show in shows) {
                if (!Titles.normalize(show.title).contains(wanted)) continue
                byTitle.putIfAbsent(Titles.normalize(show.title), show)
            }
        }
        return SearchRanking.rank(query, byTitle.values.toList())
    }

    /** Every Chaîne's answer to [query], kept apart so the Search filters apply before fusion. */
    suspend fun search(providers: List<Provider>, query: String): SearchHits = coroutineScope {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@coroutineScope SearchHits(trimmed, emptyList())
        val outcomes: List<List<Show>?> = providers.map { provider ->
            async {
                try {
                    provider.search(trimmed)
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null // this Provider failed; the others still count
                }
            }
        }.awaitAll()
        SearchHits(trimmed, providers.mapIndexed { index, provider -> ChannelHits(provider.channel.id, outcomes[index]) })
    }
}

/** Four posters per row fill the results column (472 dp) with the 20 dp card gap. */
private const val SEARCH_GRID_COLUMNS = 4
private val KEYBOARD_WIDTH = 300.dp
private val KEY_HEIGHT = 44.dp
private val KEY_GAP = 8.dp

/** Left edge of the results column: the keyboard, then 36 dp of air. */
private val RESULTS_START = 104.dp + KEYBOARD_WIDTH + 36.dp
private val BEST_ART_WIDTH = 208.dp
/** Below the top inset (TV overscan on the box: 35 dp as before; less on mobile). */
private val TOP: Dp
    @Composable @ReadOnlyComposable get() = Rb.safeVertical + 8.dp

/**
 * Search (DESIGN.md « Search »): the D-pad keyboard on the left, on the right the typed query,
 * the Chaîne-group and Genre chips, the best result and a grid of four posters per row. The
 * right column is one lazy grid whose header, best result and status line are full-width items.
 */
@Composable
fun SearchScreen(
    state: SearchUiState,
    onOpenShow: (ShowId, String) -> Unit,
    onPlay: (EpisodeId, String, ProgressContext) -> Unit,
) {
    val catalog = Providers.catalog
    // Mobile: the system keyboard in a field, results full width (grilling 2026-11-10).
    val mobile = LocalMobile.current
    // Read on mobile only: on the box the insets listener is never installed.
    @OptIn(ExperimentalLayoutApi::class)
    val imeVisible = mobile && WindowInsets.isImeVisible
    // Read once per visit, like Home: Providers.all follows the M6+ account.
    val providers = remember { Providers.all }
    val channelIds = remember { providers.map { it.channel.id } }
    val channelNames = remember { providers.associate { it.channel.id to it.channel.name } }
    val groups = remember { GenreFilters.available(ProviderGroups.all, channelIds) }
    val scope = rememberCoroutineScope()
    var searching by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }
    var handledRetry by remember { mutableStateOf(0) }

    // The selected group, or « Tout » once it is no longer listed (M6+ account removed).
    fun currentGroup(): ChannelFilter = GenreFilters.selected(groups, state.filter.groupId)
    fun currentFilter(): SearchFilter = state.filter.copy(groupId = currentGroup().groupId)
    fun computeView(): SearchView {
        val group = currentGroup()
        val ids = if (group.groupId == null) null else group.channelIds.toSet()
        return SearchViews.of(state.query, currentFilter(), ids, state.hits, state.browse, searching)
    }
    fun resumeShown(view: SearchView): Boolean =
        view.best != null && state.bestResume?.showId == view.best.show.id

    val filter = currentFilter()
    // Fusion runs over thousands of titles for a short query: only when its inputs change.
    val view = remember(state.query, filter, state.hits, state.browse, searching) { computeView() }
    val best = view.best
    val resume = state.bestResume?.takeIf { best != null && it.showId == best.show.id }

    /** Focuses [key]; when its element is gone, [fallback] (null: focus stays where it is). */
    fun focusOn(key: String, fallback: String? = SearchFocus.KEY_A) {
        val moved = runCatching { state.requester(key).requestFocus() }.isSuccess
        if (!moved && fallback != null) runCatching { state.requester(fallback).requestFocus() }
    }

    /** Item index of [key] in the results grid: the header is 0, posters follow the best result or status. */
    fun itemIndex(key: String, shown: SearchView): Int? = when {
        SearchFocus.isResult(key) -> shown.grid.indexOfFirst { SearchFocus.result(it.show.id.value) == key }
            .takeIf { it >= 0 }
            ?.let { it + 1 + if (shown.best != null || shown.status != null) 1 else 0 }
        SearchFocus.isKey(key) -> null
        else -> 0
    }

    /** RIGHT from the keyboard's last column (SearchUiState.rightEntry), scrolling there first. */
    fun enterRight() {
        scope.launch {
            val shown = computeView()
            val key = state.rightEntry(shown, resumeShown(shown))
            val index = itemIndex(key, shown)
            if (index != null && state.grid.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                state.grid.scrollToItem(index)
                withFrameNanos { }
            }
            focusOn(key, fallback = null)
        }
    }

    val toKeyboard: () -> Boolean = {
        focusOn(state.lastKey)
        true
    }
    val bestFirstKey = if (resume != null) SearchFocus.BEST_RESUME else SearchFocus.BEST_EPISODES
    val firstPosterKey = view.grid.firstOrNull()?.let { SearchFocus.result(it.show.id.value) }
    // DOWN from the Genre chips: best result, then the grid, then « Réessayer »; never the keyboard.
    val belowChips: () -> Boolean = {
        val target = when {
            best != null -> bestFirstKey
            firstPosterKey != null -> firstPosterKey
            view.status == SearchStatus.UNAVAILABLE -> SearchFocus.RETRY
            else -> null
        }
        if (target != null) focusOn(target, fallback = null)
        true
    }
    val stay: () -> Boolean = { true }

    // Search from 1 character; the previous results stay on screen while the next query runs.
    LaunchedEffect(state.query, retry) {
        val fromRetry = retry != handledRetry
        handledRetry = retry
        if (!state.needsSearch(fromRetry)) {
            searching = false
            return@LaunchedEffect
        }
        searching = true
        if (!fromRetry) delay(SearchFusion.DEBOUNCE_MS)
        state.hits = SearchFusion.search(providers, state.query)
        searching = false
        if (fromRetry) {
            // « Réessayer » left composition while loading: focus goes back into the results.
            withFrameNanos { }
            val shown = computeView()
            focusOn(state.rightEntry(shown, resumeShown(shown)), fallback = state.lastKey)
        }
    }
    // Empty query with a filter: the cached grid of that filter, like « Tout voir ».
    LaunchedEffect(state.query.isBlank(), filter) {
        if (state.query.isNotBlank() || !filter.isActive || state.browse?.filter == filter) return@LaunchedEffect
        val ids = currentGroup().channelIds
        val shows = withContext(Dispatchers.IO) { catalog.browse(ids, filter.genre) }
        state.browse = BrowseResult(filter, shows.map { SearchEntry(it.show, it.channelId) })
    }
    // Captions name the Chaîne listing each result; otherwise the Provider that returned it
    // (remote TF1+ results). Never a guess.
    LaunchedEffect(state.hits) {
        val ids = state.hits?.channels.orEmpty().flatMap { it.shows.orEmpty() }.map { it.id }.distinct()
        if (ids.isNotEmpty()) state.listedChannels = catalog.channelsOf(ids, channelIds)
    }
    // « Reprendre » on the best result: read again on each arrival (back from the player).
    LaunchedEffect(best?.show?.id) {
        val id = best?.show?.id ?: return@LaunchedEffect
        state.bestResume = catalog.resumeFor(id, channelIds)
    }
    // First visit: « A ». Back from a Programme page or the player: the element focused last.
    LaunchedEffect(Unit) {
        val shownBest = best
        if (state.focusKey == SearchFocus.BEST_RESUME && shownBest != null) {
            state.bestResume = catalog.resumeFor(shownBest.show.id, channelIds)
        }
        if (mobile) return@LaunchedEffect // no D-pad focus to place on mobile
        withFrameNanos { }
        val shown = computeView()
        focusOn(state.initialFocus(shown, resumeShown(shown)))
    }
    // Back from the results column returns to the keyboard; from the keyboard it leaves Search.
    // Mobile: Back closes the IME (system), then leaves Search.
    BackHandler(enabled = !mobile && state.inRightColumn) { focusOn(state.lastKey) }

    Box(Modifier.fillMaxSize()) {
        if (!mobile) SearchKeys(
            state = state,
            modifier = Modifier.padding(start = Rb.contentStart, top = TOP).width(KEYBOARD_WIDTH),
            onType = { key ->
                val grid = state.grid
                if (state.type(key) && (grid.firstVisibleItemIndex > 0 || grid.firstVisibleItemScrollOffset > 0)) {
                    scope.launch { grid.scrollToItem(0) }
                }
            },
            onRight = { enterRight() },
        )
        Column(Modifier.fillMaxSize()) {
        if (mobile) {
            SearchField(
                query = state.query,
                openKeyboard = state.opensKeyboardOnEntry,
                onQuery = { text ->
                    val grid = state.grid
                    if (state.setQuery(text) && (grid.firstVisibleItemIndex > 0 || grid.firstVisibleItemScrollOffset > 0)) {
                        scope.launch { grid.scrollToItem(0) }
                    }
                },
                modifier = Modifier.padding(start = Rb.contentStart, top = TOP, end = Rb.safeHorizontal),
            )
        }
        TvLazyVerticalGrid(
            state = state.grid,
            columns = TvGridCells.Fixed(if (mobile) POSTER_GRID_COLUMNS else SEARCH_GRID_COLUMNS),
            // The grid starts one gutter early so a focused poster's lift and ring are not clipped.
            modifier = Modifier
                .fillMaxSize()
                .touchScroll(state.grid, Orientation.Vertical)
                .padding(start = (if (mobile) Rb.contentStart else RESULTS_START) - GUTTER),
            contentPadding = PaddingValues(
                start = GUTTER,
                end = Rb.safeHorizontal,
                top = if (mobile) 4.dp else TOP,
                bottom = Rb.safeVertical,
            ),
            horizontalArrangement = Arrangement.spacedBy(Rb.cardGap),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            item(key = "header", span = { TvGridItemSpan(maxLineSpan) }) {
                // Mobile: while the keyboard is open the chips step aside, so results show above it.
                if (!mobile || !imeVisible) Column {
                    if (!mobile) QueryLine(state.query)
                    Row(
                        // Room above and below for the 1.06 lift of a focused chip.
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        groups.forEachIndexed { index, chip ->
                            val key = SearchFocus.groupChip(chip.groupId)
                            FilterChip(
                                label = stringResource(filterLabel(chip.groupId)),
                                selected = chip.groupId == filter.groupId,
                                requester = state.requester(key),
                                onFocused = { state.focus(key) },
                                modifier = Modifier.dpad(left = if (index == 0) toKeyboard else null, up = stay),
                                onClick = { state.filter = state.filter.copy(groupId = chip.groupId) },
                            )
                        }
                    }
                    Row(
                        // Pulled 4 dp left with 4 dp of inner padding: the scroll clip never cuts a lifted chip.
                        modifier = Modifier
                            .offset(x = (-4).dp)
                            .horizontalScroll(rememberScrollState())
                            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        (listOf<Genre?>(null) + Genre.values()).forEachIndexed { index, genre ->
                            val key = SearchFocus.genreChip(genre)
                            FilterChip(
                                label = genre?.genreLabel() ?: stringResource(R.string.filter_all),
                                selected = filter.genre == genre,
                                requester = state.requester(key),
                                onFocused = { state.focus(key) },
                                modifier = Modifier.dpad(left = if (index == 0) toKeyboard else null, down = belowChips),
                                onClick = { state.filter = state.filter.copy(genre = genre) },
                            )
                        }
                    }
                }
            }
            if (best != null) {
                item(key = "best", span = { TvGridItemSpan(maxLineSpan) }) {
                    BestResultCard(
                        entry = best,
                        channel = (state.listedChannels[best.show.id] ?: best.channelId)?.let { channelNames[it] },
                        resume = resume,
                        state = state,
                        toKeyboard = toKeyboard,
                        down = {
                            if (firstPosterKey != null) focusOn(firstPosterKey, fallback = null)
                            true
                        },
                        onResume = { item ->
                            onPlay(item.episodeId, item.episodeTitle, ProgressContext(item.showId, item.showTitle, item.episodeTitle, item.thumbnailUrl))
                        },
                        onEpisodes = { onOpenShow(best.show.id, best.show.title) },
                    )
                }
            }
            view.status?.let { status ->
                item(key = "status", span = { TvGridItemSpan(maxLineSpan) }) {
                    StatusLine(
                        status = status,
                        retryRequester = state.requester(SearchFocus.RETRY),
                        retryModifier = Modifier.dpad(left = toKeyboard, down = stay),
                        onRetryFocused = { state.focus(SearchFocus.RETRY) },
                        onRetry = { retry += 1 },
                    )
                }
            }
            itemsIndexed(view.grid, key = { _, entry -> entry.show.id.value }) { index, entry ->
                val key = SearchFocus.result(entry.show.id.value)
                PosterCaptionCard(
                    show = entry.show,
                    channel = (state.listedChannels[entry.show.id] ?: entry.channelId)?.let { channelNames[it] },
                    focusRequesters = listOf(state.requester(key)),
                    onFocused = { state.focus(key) },
                    modifier = Modifier.dpad(
                        left = if (index % SEARCH_GRID_COLUMNS == 0) toKeyboard else null,
                        // The first row goes up to the best result, not past it to a chip.
                        up = if (index < SEARCH_GRID_COLUMNS && best != null) {
                            {
                                focusOn(bestFirstKey, fallback = null)
                                true
                            }
                        } else {
                            null
                        },
                    ),
                    onClick = {
                        state.focus(key)
                        onOpenShow(entry.show.id, entry.show.title)
                    },
                )
            }
        }
        }
    }
}

/**
 * Explicit D-pad moves on top of the geometric focus search: a handler returning true consumes
 * the key. Placed before the focus target in the modifier chain, so it sees the key first.
 */
private fun Modifier.dpad(
    left: (() -> Boolean)? = null,
    right: (() -> Boolean)? = null,
    up: (() -> Boolean)? = null,
    down: (() -> Boolean)? = null,
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    val handler = when (event.key) {
        Key.DirectionLeft -> left
        Key.DirectionRight -> right
        Key.DirectionUp -> up
        Key.DirectionDown -> down
        else -> null
    }
    handler?.invoke() ?: false
}

/**
 * The D-pad keyboard: six columns of keys (A–Z, 0–9), then « Espace » and « Effacer » three
 * columns wide. Moves stay inside it except RIGHT from the last column ([onRight]).
 */
@Composable
private fun SearchKeys(
    state: SearchUiState,
    modifier: Modifier,
    onType: (KeyboardKey) -> Unit,
    onRight: () -> Unit,
) {
    val stay: () -> Boolean = { true }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(KEY_GAP)) {
        SearchKeyboard.rows.forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                row.forEachIndexed { column, key ->
                    KeyButton(
                        label = when (key) {
                            is KeyboardKey.Letter -> key.char.toString()
                            KeyboardKey.Space -> stringResource(R.string.key_space)
                            KeyboardKey.Delete -> stringResource(R.string.key_delete)
                        },
                        requester = state.requester(key.focusKey),
                        onFocused = { state.focus(key.focusKey) },
                        modifier = Modifier
                            .weight(1f)
                            .dpad(
                                left = if (column == 0) stay else null,
                                right = if (SearchKeyboard.isLastColumn(key)) {
                                    {
                                        onRight()
                                        true
                                    }
                                } else {
                                    null
                                },
                                // Never out of the top or bottom edge into the results column.
                                up = if (rowIndex == 0) stay else null,
                                down = if (rowIndex == SearchKeyboard.rows.lastIndex) stay else null,
                            ),
                        onClick = { onType(key) },
                    )
                }
            }
        }
    }
}

/** A keyboard key: a panel button with a centred label (raised slate, white fill and 1.06 lift on focus). */
@Composable
private fun KeyButton(
    label: String,
    requester: FocusRequester,
    onFocused: () -> Unit,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) Rb.BUTTON_FOCUS_SCALE else 1f, tween(Rb.FOCUS_MS), label = "key")
    Box(
        modifier = modifier
            .scale(scale)
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .tvClickable(onClick)
            .background(if (focused) Rb.focus else Rb.surfaceRaised, Rb.buttonShape)
            .height(KEY_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = RbType.bodyStrong, color = if (focused) Rb.onFocus else Rb.text, maxLines = 1)
    }
}

/** The typed query in Display Compact (two lines before the ellipsis), or the Mist placeholder. */
@Composable
private fun QueryLine(query: String) {
    val empty = query.isEmpty()
    Text(
        text = if (empty) stringResource(R.string.search_hint) else query,
        style = RbType.displayCompact,
        color = if (empty) Rb.textSecondary else Rb.text,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The best result: 16:9 key art, the title, « Chaîne · Genre », then « Reprendre » (when an
 * Épisode is in progress) and « Épisodes ». Only the buttons take focus.
 */
@Composable
private fun BestResultCard(
    entry: SearchEntry,
    channel: String?,
    resume: ResumeItem?,
    state: SearchUiState,
    toKeyboard: () -> Boolean,
    down: () -> Boolean,
    onResume: (ResumeItem) -> Unit,
    onEpisodes: () -> Unit,
) {
    val show = entry.show
    Column {
        // Mobile: the art and title open the Programme, like « Épisodes ». Not on the box,
        // where it would add a focus stop before the buttons.
        Row(
            if (LocalMobile.current) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onEpisodes,
                )
            } else {
                Modifier
            },
        ) {
            Box(
                Modifier
                    .width(BEST_ART_WIDTH)
                    .aspectRatio(16f / 9f)
                    .clip(Rb.cardShape)
                    .background(Rb.surface),
            ) {
                Artwork(url = show.backdropUrl ?: show.posterUrl, fallback = show.title)
            }
            Column(Modifier.padding(start = 20.dp)) {
                Text(show.title, style = RbType.railTitle, color = Rb.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(channel, show.genres.firstOrNull()?.genreLabel()).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = RbType.meta,
                        color = Rb.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (resume != null) {
                    // What « Reprendre » plays, with the time left in words.
                    val line = listOfNotNull(resume.episodeTitle.takeIf { it.isNotBlank() }, remainingText(resume.remainingSeconds))
                        .joinToString(" · ")
                    if (line.isNotEmpty()) {
                        Text(
                            line,
                            style = RbType.meta,
                            color = Rb.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (resume != null) {
                PanelButton(
                    label = stringResource(R.string.action_resume),
                    icon = Icons.Filled.PlayArrow,
                    requester = state.requester(SearchFocus.BEST_RESUME),
                    onFocused = { state.focus(SearchFocus.BEST_RESUME) },
                    modifier = Modifier.dpad(left = toKeyboard, down = down),
                    onClick = { onResume(resume) },
                )
            }
            PanelButton(
                label = stringResource(R.string.action_episodes),
                icon = null,
                requester = state.requester(SearchFocus.BEST_EPISODES),
                onFocused = { state.focus(SearchFocus.BEST_EPISODES) },
                modifier = Modifier.dpad(left = if (resume == null) toKeyboard else null, down = down),
                onClick = onEpisodes,
            )
        }
    }
}

/** Hint, loading (only past 500 ms, so quick searches never flash it), empty or failure line. */
@Composable
private fun StatusLine(
    status: SearchStatus,
    retryRequester: FocusRequester,
    retryModifier: Modifier,
    onRetryFocused: () -> Unit,
    onRetry: () -> Unit,
) {
    var loadingShown by remember(status) { mutableStateOf(false) }
    if (status == SearchStatus.LOADING) {
        LaunchedEffect(Unit) {
            delay(LOADING_DELAY_MS)
            loadingShown = true
        }
    }
    Column {
        val text = when (status) {
            SearchStatus.HINT -> R.string.search_type_hint
            SearchStatus.LOADING -> R.string.loading.takeIf { loadingShown }
            SearchStatus.UNAVAILABLE -> R.string.search_unavailable
            SearchStatus.EMPTY -> R.string.search_no_results
        }
        if (text != null) Text(stringResource(text), style = RbType.body, color = Rb.textSecondary)
        if (status == SearchStatus.UNAVAILABLE) {
            Box(Modifier.padding(top = 20.dp)) {
                PanelButton(
                    label = stringResource(R.string.retry),
                    icon = Icons.Filled.Refresh,
                    requester = retryRequester,
                    onFocused = onRetryFocused,
                    modifier = retryModifier,
                    onClick = onRetry,
                )
            }
        }
    }
}
