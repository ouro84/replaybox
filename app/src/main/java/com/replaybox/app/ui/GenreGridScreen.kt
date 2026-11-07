package com.replaybox.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvGridItemSpan
import androidx.tv.foundation.lazy.grid.TvLazyGridState
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import com.replaybox.R
import com.replaybox.core.model.ChannelShow
import com.replaybox.core.model.Genre
import com.replaybox.core.model.ShowId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * « Tout voir » state hoisted above the back stack (MainActivity keeps one per opening): Back
 * from a Programme page finds the same Chaîne filter, scroll and focused card.
 */
class GenreGridUiState(val genre: Genre) {
    val grid = TvLazyGridState()

    /** Selected Chaîne group (ProviderGroups id); null is « Tout ». Changes on OK only. */
    var groupId by mutableStateOf<String?>(null)

    /** The grid under the selected filter; null until the first read (skeleton). */
    var items by mutableStateOf<List<ChannelShow>?>(null)

    /** Focus key of the element focused last: a Programme id or a [chipKey]. */
    var focusKey: String? = null

    private val requesters = HashMap<String, FocusRequester>()

    fun requester(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    /**
     * Where focus goes once the grid is on screen: the element focused last when it is still
     * shown (back from a Programme page), else the first Programme, else the selected chip.
     */
    fun initialFocus(): String {
        val shown = items.orEmpty()
        val last = focusKey
        return when {
            last != null && (isChip(last) || shown.any { it.show.id.value == last }) -> last
            shown.isNotEmpty() -> shown.first().show.id.value
            else -> chipKey(groupId)
        }
    }

    companion object {
        private const val CHIP_PREFIX = "chip:"

        fun chipKey(groupId: String?): String = CHIP_PREFIX + (groupId ?: "all")

        fun isChip(key: String): Boolean = key.startsWith(CHIP_PREFIX)
    }
}

/**
 * The « Tout voir » grid of a Genre: every Programme of the Genre on every listed Chaîne, from
 * the cache only (CatalogStore.genreGrid: recency, then title), under one row of Chaîne-group
 * chips. Title and chips are full-width items of the grid, so they scroll away with it.
 */
@Composable
fun GenreGridScreen(state: GenreGridUiState, onOpenShow: (ShowId, String) -> Unit) {
    val catalog = Providers.catalog
    val updates by catalog.updates.collectAsState()
    // Read once per visit, like Home: Providers.all follows the M6+ account.
    val providers = remember { Providers.all }
    val channelIds = remember { providers.map { it.channel.id } }
    val channelNames = remember { providers.associate { it.channel.id to it.channel.name } }
    val filters = remember { GenreFilters.available(ProviderGroups.all, channelIds) }
    val filter = GenreFilters.selected(filters, state.groupId)

    // Re-read when the filter changes or a refresh lands; the previous list stays on screen meanwhile.
    LaunchedEffect(filter, updates) {
        state.items = withContext(Dispatchers.IO) { catalog.genreGrid(state.genre, filter.channelIds) }
    }
    val shown = state.items
    LaunchedEffect(shown != null) {
        if (shown == null) return@LaunchedEffect
        withFrameNanos { }
        runCatching { state.requester(state.initialFocus()).requestFocus() }
    }

    TvLazyVerticalGrid(
        state = state.grid,
        columns = TvGridCells.Fixed(POSTER_GRID_COLUMNS),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Rb.contentStart,
            end = Rb.safeHorizontal,
            top = Rb.safeVertical + 8.dp,
            bottom = Rb.safeVertical,
        ),
        horizontalArrangement = Arrangement.spacedBy(Rb.cardGap),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item(key = "header", span = { TvGridItemSpan(maxLineSpan) }) {
            Column {
                Text(state.genre.genreLabel(), style = RbType.displayCompact, color = Rb.text)
                Row(
                    // Room above and below for the 1.06 lift of a focused chip.
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    for (chip in filters) {
                        val key = GenreGridUiState.chipKey(chip.groupId)
                        ChannelChip(
                            label = stringResource(filterLabel(chip.groupId)),
                            selected = chip == filter,
                            requester = state.requester(key),
                            onFocused = { state.focusKey = key },
                            onClick = { state.groupId = chip.groupId },
                        )
                    }
                }
            }
        }
        when {
            shown == null -> items(SKELETON_POSTERS) { SkeletonPoster() }
            shown.isEmpty() -> item(key = "empty", span = { TvGridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.genre_grid_empty),
                    style = RbType.body,
                    color = Rb.textSecondary,
                )
            }
            else -> items(shown, key = { it.show.id.value }) { entry ->
                val id = entry.show.id.value
                PosterCaptionCard(
                    show = entry.show,
                    channel = channelNames[entry.channelId],
                    focusRequesters = listOf(state.requester(id)),
                    onFocused = { state.focusKey = id },
                    onClick = {
                        state.focusKey = id
                        onOpenShow(entry.show.id, entry.show.title)
                    },
                )
            }
        }
    }
}

/** Two rows of placeholders while the cache is read (no spinner, DESIGN.md « Skeleton »). */
private const val SKELETON_POSTERS = 12

private fun filterLabel(groupId: String?): Int = when (groupId) {
    null -> R.string.filter_all
    ProviderGroups.TF1 -> R.string.filter_tf1
    ProviderGroups.FRANCETV -> R.string.filter_francetv
    else -> R.string.filter_m6
}

/**
 * Chaîne-group chip. Selection happens on OK, never on focus (like the Saison pills): crossing
 * the row reloads nothing. Selected: raised slate plus a check, never colour alone. Focused:
 * white fill and the 1.06 lift.
 */
@Composable
private fun ChannelChip(
    label: String,
    selected: Boolean,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) Rb.BUTTON_FOCUS_SCALE else 1f, tween(Rb.FOCUS_MS), label = "chip")
    val background = when {
        focused -> Rb.focus
        selected -> Rb.surfaceRaised
        else -> null
    }
    val color = when {
        focused -> Rb.onFocus
        selected -> Rb.text
        else -> Rb.textSecondary
    }
    Row(
        modifier = Modifier
            .scale(scale)
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .tvClickable(onClick)
            .then(if (background != null) Modifier.background(background, Rb.pillShape) else Modifier)
            .height(40.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = RbType.bodyStrong, color = color, maxLines = 1)
    }
}

/** A poster slot with its caption lines, in slate surface (DESIGN.md « Skeleton »). */
@Composable
private fun SkeletonPoster() {
    Column {
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).background(Rb.surface, Rb.cardShape))
        Box(
            Modifier
                .padding(top = 18.dp)
                .fillMaxWidth(0.8f)
                .height(16.dp)
                .background(Rb.surface, Rb.pillShape),
        )
    }
}
