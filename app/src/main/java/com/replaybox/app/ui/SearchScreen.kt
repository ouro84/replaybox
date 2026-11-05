package com.replaybox.app.ui

import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import coil.compose.AsyncImage
import com.replaybox.R
import com.replaybox.core.model.Genre
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
    val resultsFocus = remember { FocusRequester() }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp)
            // The focused TextField swallows D-pad events; unhandled DOWN
            // bubbles here — move to the first result card (TV remote flow).
            .onKeyEvent { event ->
                event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionDown &&
                    results.isNotEmpty() &&
                    run {
                        resultsFocus.requestFocus()
                        true
                    }
            },
    ) {
        SearchField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.padding(horizontal = 32.dp).fillMaxWidth(),
        )
        val trimmed = query.trim()
        when {
            trimmed.length < SearchFusion.MIN_CHARS -> Text(
                text = stringResource(R.string.search_min_chars),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
            )
            searching && results.isEmpty() -> Text(
                text = stringResource(R.string.loading),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
            )
            results.isEmpty() -> Text(
                text = stringResource(R.string.search_no_results),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
            )
            else -> TvLazyVerticalGrid(
                columns = TvGridCells.Fixed(6),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(results) { show ->
                    SearchResultCard(
                        show = show,
                        onOpenShow = onOpenShow,
                        focusRequester = resultsFocus,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun SearchResultCard(
    show: Show,
    onOpenShow: (ShowId, String) -> Unit,
    focusRequester: FocusRequester,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .focusRequester(focusRequester)
            .width(140.dp)
            .scale(if (focused) 1.05f else 1f)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable { onOpenShow(show.id, show.title) }
            .border(
                width = 2.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
            ),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
            AsyncImage(
                model = show.posterUrl,
                contentDescription = show.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = show.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
        val genreLabel = show.genres.firstOrNull()?.genreLabel().orEmpty()
        if (genreLabel.isNotEmpty()) {
            Text(
                text = genreLabel,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
