package com.replaybox.app.ui

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
        return byTitle.values.toList()
    }

    private fun String.normalizedKey(): String = Titles.normalize(this)

    suspend fun search(providers: List<Provider>, query: String): List<Show> = coroutineScope {
        val trimmed = query.trim()
        if (trimmed.length < MIN_CHARS) return@coroutineScope emptyList()
        val results = providers.map { provider ->
            async { runCatching { provider.search(trimmed) }.getOrDefault(emptyList()) }
        }.awaitAll()
        fuse(trimmed, results)
    }
}

@Composable
fun SearchScreen(onOpenShow: (ShowId, String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Show>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var channels by remember { mutableStateOf<Map<ShowId, String>>(emptyMap()) }
    val resultsFocus = remember { FocusRequester() }
    val channelIds = remember { Providers.all.map { it.channel.id } }
    val channelNames = remember { Providers.all.associate { it.channel.id to it.channel.name } }

    LaunchedEffect(query) {
        val value = query
        delay(SearchFusion.DEBOUNCE_MS)
        if (value.trim().length < SearchFusion.MIN_CHARS) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        results = SearchFusion.search(Providers.all, value)
        searching = false
    }
    // Captions name the Chaîne only when the cache knows it (never a guess).
    LaunchedEffect(results) {
        channels = Providers.catalog.channelsOf(results.map { it.id }, channelIds)
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
                    results.isNotEmpty() &&
                    runCatching { resultsFocus.requestFocus() }.isSuccess
            },
    ) {
        SearchField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.padding(start = Rb.contentStart).width(540.dp),
        )
        val trimmed = query.trim()
        val status = when {
            trimmed.length < SearchFusion.MIN_CHARS -> R.string.search_min_chars
            searching && results.isEmpty() -> R.string.loading
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
        } else {
            TvLazyVerticalGrid(
                columns = TvGridCells.Fixed(GRID_COLUMNS),
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
                    SearchResultCard(
                        show = show,
                        channel = channels[show.id]?.let { channelNames[it] },
                        focusRequester = resultsFocus.takeIf { index == 0 },
                        onClick = { onOpenShow(show.id, show.title) },
                    )
                }
            }
        }
    }
}

/** Six 3:4 posters of ~118 dp fill the 808 dp between the content start and the safe edge. */
private const val GRID_COLUMNS = 6

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

@Composable
private fun SearchResultCard(
    show: Show,
    channel: String?,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale = focusScale(focused)
    Column(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .scale(scale)
                .aspectRatio(3f / 4f)
                .cardFocus(focused)
                .background(Rb.surface),
        ) {
            Artwork(url = show.posterUrl, fallback = show.title)
        }
        Text(
            text = show.title,
            style = RbType.cardTitle,
            color = Rb.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 14.dp),
        )
        val meta = listOfNotNull(channel, show.genres.firstOrNull()?.genreLabel()).joinToString(" · ")
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                style = RbType.meta,
                color = Rb.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
