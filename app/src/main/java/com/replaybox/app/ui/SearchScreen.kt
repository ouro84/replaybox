package com.replaybox.app.ui

import androidx.compose.runtime.withFrameNanos
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.itemsIndexed
import com.replaybox.R
import com.replaybox.core.model.Show
import com.replaybox.core.model.ShowId
import com.replaybox.core.data.SearchRanking
import com.replaybox.core.model.Titles
import com.replaybox.provider.api.Provider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/**
 * Cross-chaîne search (SPEC §Recherche): debounce 300 ms, minimum 3
 * characters, Provider.search() results fused by normalized title
 * (case/accent-insensitive). A failing Chaîne contributes nothing instead of
 * failing the query.
 */
object SearchFusion {

    const val DEBOUNCE_MS = 300L
    const val MIN_CHARS = 3

    fun fuse(query: String, providerResults: List<List<Show>>): List<Show> {
        val wanted = Titles.normalize(query)
        val byTitle = LinkedHashMap<String, Show>()
        for (shows in providerResults) {
            for (show in shows) {
                if (!Titles.normalize(show.title).contains(wanted)) continue
                byTitle.putIfAbsent(show.title.normalizedKey(), show)
            }
        }
        return SearchRanking.rank(query, byTitle.values.toList())
    }

    private fun String.normalizedKey(): String = Titles.normalize(this)

    /**
     * Chaîne of each kept result: the Provider whose result survived the
     * fusion. France TV search only returns cached (listed) Programmes, so the
     * listing remains the source for them; this credits remote TF1+ results,
     * which no listing holds.
     */
    fun origins(fused: List<Show>, channelIds: List<String>, providerResults: List<List<Show>>): Map<ShowId, String> {
        val origin = HashMap<ShowId, String>()
        providerResults.forEachIndexed { index, shows ->
            val channelId = channelIds.getOrNull(index) ?: return@forEachIndexed
            for (show in shows) origin.putIfAbsent(show.id, channelId)
        }
        return fused.mapNotNull { show -> origin[show.id]?.let { show.id to it } }.toMap()
    }

    /**
     * Fused results plus the Chaîne each came from. [failed]: every Provider
     * failed, which is not « no result » (decision Round 8 Q4).
     */
    data class Hits(val shows: List<Show>, val origins: Map<ShowId, String>, val failed: Boolean = false)

    suspend fun search(providers: List<Provider>, query: String): Hits = coroutineScope {
        val trimmed = query.trim()
        if (trimmed.length < MIN_CHARS) return@coroutineScope Hits(emptyList(), emptyMap())
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
        val results = outcomes.map { it.orEmpty() }
        val fused = fuse(trimmed, results)
        Hits(
            shows = fused,
            origins = origins(fused, providers.map { it.channel.id }, results),
            failed = outcomes.isNotEmpty() && outcomes.all { it == null },
        )
    }
}

/**
 * Search state hoisted above the back stack: opening a Programme and coming back keeps the query,
 * the results and the focused card (MainActivity holds one instance per visit of Search).
 */
class SearchUiState {
    var query by mutableStateOf("")
    var results by mutableStateOf<List<Show>>(emptyList())
    var channels by mutableStateOf<Map<ShowId, String>>(emptyMap())
    var origins by mutableStateOf<Map<ShowId, String>>(emptyMap())
    var failed by mutableStateOf(false)

    /** The query whose results are on screen; null before the first search. */
    var searchedQuery: String? = null

    /** The result card opened last; focus returns to it. */
    var focusedId: String? = null

    private val requesters = HashMap<String, FocusRequester>()

    fun requester(id: String): FocusRequester = requesters.getOrPut(id) { FocusRequester() }

    /** Coming back with the same query reuses the results; a new query or Réessayer searches. */
    fun needsSearch(fromRetry: Boolean): Boolean = fromRetry || query != searchedQuery

    /** The card to focus on arrival, or null for the field (first visit: typing). */
    fun initialFocus(): String? = focusedId?.takeIf { id -> results.any { it.id.value == id } }
}

@Composable
fun SearchScreen(state: SearchUiState, onOpenShow: (ShowId, String) -> Unit) {
    var query by state::query
    var results by state::results
    var searching by remember { mutableStateOf(false) }
    var channels by state::channels
    var origins by state::origins
    val resultsFocus = remember { FocusRequester() }
    val fieldFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    var failed by state::failed
    var retry by remember { mutableStateOf(0) }
    var handledRetry by remember { mutableStateOf(0) }
    // First visit means typing: the field takes focus. Back from a Programme: its card does.
    LaunchedEffect(Unit) {
        val back = state.initialFocus()
        if (back != null) {
            withFrameNanos { }
            runCatching { state.requester(back).requestFocus() }
        } else {
            runCatching { fieldFocus.requestFocus() }
        }
    }
    val channelIds = remember { Providers.all.map { it.channel.id } }
    val channelNames = remember { Providers.all.associate { it.channel.id to it.channel.name } }

    LaunchedEffect(query, retry) {
        val value = query
        failed = false // a new query never shows the previous one's failure
        // Loading from the first moment, so neither the debounce nor a retry
        // ever reads « Aucun Programme trouvé » (Round 8 Q4).
        if (value.trim().length >= SearchFusion.MIN_CHARS) searching = true
        val fromRetry = retry != handledRetry
        handledRetry = retry
        if (!state.needsSearch(fromRetry)) {
            searching = false
            return@LaunchedEffect
        }
        if (!fromRetry) delay(SearchFusion.DEBOUNCE_MS)
        if (value.trim().length < SearchFusion.MIN_CHARS) {
            results = emptyList()
            state.searchedQuery = value
            searching = false
            return@LaunchedEffect
        }
        searching = true
        val hits = SearchFusion.search(Providers.all, value)
        origins = hits.origins
        results = hits.shows
        failed = hits.failed
        state.searchedQuery = value
        state.focusedId = null
        searching = false
        if (fromRetry) {
            // Réessayer left composition during loading: put focus back.
            withFrameNanos { }
            runCatching { if (failed) retryFocus.requestFocus() else resultsFocus.requestFocus() }
        }
    }
    // Captions name the Chaîne only when the cache knows it (never a guess).
    LaunchedEffect(results) {
        // The listing names the Chaîne when it knows the Programme; otherwise the
        // Provider that returned it does (remote TF1+ results). Never a guess.
        val listed = Providers.catalog.channelsOf(results.map { it.id }, channelIds)
        channels = results.mapNotNull { show -> (listed[show.id] ?: origins[show.id])?.let { show.id to it } }.toMap()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = Rb.safeVertical + 8.dp)
            // Capture phase: the focused TextField consumes D-pad events
            // before bubbling, so DOWN is intercepted here to move focus to
            // the first result card (TV remote flow).
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionDown &&
                    when {
                        failed -> runCatching { retryFocus.requestFocus() }.isSuccess
                        results.isNotEmpty() -> runCatching { resultsFocus.requestFocus() }.isSuccess
                        else -> false
                    }
            },
    ) {
        SearchField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.padding(start = Rb.contentStart).width(540.dp).focusRequester(fieldFocus),
        )
        val trimmed = query.trim()
        val status = when {
            trimmed.length < SearchFusion.MIN_CHARS -> R.string.search_min_chars
            searching && results.isEmpty() -> R.string.loading
            failed -> R.string.search_unavailable
            results.isEmpty() -> R.string.search_no_results
            else -> null
        }
        if (status != null) {
            Text(
                text = stringResource(status),
                style = RbType.body,
                color = Rb.textSecondary,
                modifier = Modifier.padding(start = Rb.contentStart, top = 20.dp),
            )
            if (status == R.string.search_unavailable) {
                Box(Modifier.padding(start = Rb.contentStart, top = 20.dp)) {
                    PanelButton(
                        label = stringResource(R.string.retry),
                        icon = Icons.Filled.Refresh,
                        requester = retryFocus,
                        onClick = { retry += 1 },
                    )
                }
            }
        } else {
            TvLazyVerticalGrid(
                columns = TvGridCells.Fixed(POSTER_GRID_COLUMNS),
                // Top/side room for the 1.1 lift and the ring (DESIGN.md: rails).
                contentPadding = PaddingValues(
                    start = Rb.contentStart,
                    end = Rb.safeHorizontal,
                    top = 24.dp,
                    bottom = Rb.safeVertical,
                ),
                horizontalArrangement = Arrangement.spacedBy(Rb.cardGap),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                itemsIndexed(results, key = { _, show -> show.id.value }) { index, show ->
                    PosterCaptionCard(
                        show = show,
                        channel = channels[show.id]?.let { channelNames[it] },
                        focusRequesters = listOfNotNull(resultsFocus.takeIf { index == 0 }, state.requester(show.id.value)),
                        onClick = {
                            state.focusedId = show.id.value
                            onOpenShow(show.id, show.title)
                        },
                    )
                }
            }
        }
    }
}


@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = RbType.body.copy(color = Rb.text),
        cursorBrush = SolidColor(Rb.text),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .focusRing(focused, FIELD_RADIUS)
            .background(Rb.surfaceRaised, Rb.buttonShape)
            .height(52.dp),
        decorationBox = { inner ->
            Row(
                Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = Rb.textSecondary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_hint),
                            style = RbType.body,
                            color = Rb.textSecondary,
                        )
                    }
                    inner()
                }
            }
        },
    )
}

/** Matches Rb.buttonShape so the ring stays concentric with the field. */
private val FIELD_RADIUS = 8.dp
