package com.replaybox.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.replaybox.R
import com.replaybox.core.model.Genre

/**
 * Survives the trip to a « Tout voir » grid: Back lands on the tile that opened it.
 * MainActivity starts a new one at each entry from the nav rail (focus on the first tile).
 */
class GenresUiState {
    var openedGenre: Genre? = null

    /** The tile to focus on arrival: the one opened last, else the first Genre. */
    fun initialFocus(): Genre = openedGenre ?: Genre.values().first()
}

/**
 * The Genres page (nav rail « Genres »): the 9 canonical Genres in glossary order as a 3 × 3
 * grid of text tiles, no image and no count. A tile opens that Genre's « Tout voir » grid.
 */
@Composable
fun GenresScreen(state: GenresUiState, onOpenGenre: (Genre) -> Unit) {
    val requesters = remember { Genre.values().associateWith { FocusRequester() } }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { requesters.getValue(state.initialFocus()).requestFocus() }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .mobileVerticalScroll()
            .padding(start = Rb.contentStart, top = Rb.safeVertical + 8.dp, end = Rb.safeHorizontal),
    ) {
        Text(stringResource(R.string.genres_title), style = RbType.displayCompact, color = Rb.text)
        Spacer(Modifier.height(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(Rb.cardGap)) {
            for (row in Genre.values().toList().chunked(TILES_PER_ROW)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Rb.cardGap)) {
                    for (genre in row) {
                        GenreTile(
                            label = genre.genreLabel(),
                            requester = requesters.getValue(genre),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                state.openedGenre = genre
                                onOpenGenre(genre)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 3 × 3: three 256 dp tiles fill the 808 dp between the content start and the safe edge. */
private const val TILES_PER_ROW = 3

/** Three rows and their gaps end above the 27 dp bottom safe edge, ring included. */
private val TILE_HEIGHT = 112.dp

/** Raised Slate plate, Genre name in Rail Title; card focus (1.1 lift + concentric ring). */
@Composable
private fun GenreTile(label: String, requester: FocusRequester, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale = focusScale(focused)
    Box(
        modifier = modifier
            .height(TILE_HEIGHT)
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick)
            .scale(scale)
            .cardFocus(focused)
            .background(Rb.surfaceRaised)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(label, style = RbType.railTitle, color = Rb.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
