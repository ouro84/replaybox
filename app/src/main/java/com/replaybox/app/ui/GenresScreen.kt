package com.replaybox.app.ui

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.decode.DataSource
import coil.request.ImageRequest
import com.replaybox.R
import com.replaybox.core.model.Genre
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Survives the trip to a « Tout voir » grid: Back lands on the tile that opened it.
 * MainActivity starts a new one at each entry from the nav rail (focus on the first tile),
 * seeded with the previous visit's [covers] so the tiles do not flash back to text first.
 */
class GenresUiState(covers: Map<Genre, String> = emptyMap()) {
    var openedGenre: Genre? = null

    /** Each Genre's cover URL from the cached listings; a Genre without one keeps its text tile. */
    var covers: Map<Genre, String> by mutableStateOf(covers)

    /** The tile to focus on arrival: the one opened last, else the first Genre. */
    fun initialFocus(): Genre = openedGenre ?: Genre.values().first()
}

/**
 * The Genres page (nav rail « Genres »): the 9 canonical Genres in glossary order as a 3 × 3
 * grid of tiles, no count. A tile shows its Genre's cover once loaded (cache only) and opens
 * that Genre's « Tout voir » grid.
 */
@Composable
fun GenresScreen(state: GenresUiState, onOpenGenre: (Genre) -> Unit) {
    val requesters = remember { Genre.values().associateWith { FocusRequester() } }
    // Focus never waits for covers: the tiles are laid out before any cover is read.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { requesters.getValue(state.initialFocus()).requestFocus() }
    }
    // Covers follow the grid's « Tout » chip (every listed Chaîne) and each catalogue refresh
    // written while the page is on screen. They are decorative: a cache read that fails keeps
    // the covers shown so far (text tiles at worst), never crashes the page.
    val catalog = Providers.catalog
    val updates by catalog.updates.collectAsState()
    val channelIds = remember { Providers.all.map { it.channel.id } }
    LaunchedEffect(updates) {
        val covers = runCatching { withContext(Dispatchers.IO) { catalog.genreCovers(channelIds) } }
            .onFailure { if (it is CancellationException) throw it }
            .onFailure { Log.w(TAG, "Genre covers unavailable, keeping the tiles as they are", it) }
            .getOrNull()
        if (covers != null) state.covers = covers
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
                            cover = state.covers[genre],
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

private const val TAG = "GenresScreen"

/** 3 × 3: three 256 dp tiles fill the 808 dp between the content start and the safe edge. */
private const val TILES_PER_ROW = 3

/** Three rows and their gaps end above the 27 dp bottom safe edge, ring included. */
private val TILE_HEIGHT = 112.dp

/** Name inset inside the tile: 20 dp from the sides, 16 dp from the top and bottom. */
private val NAME_INSET_H = 20.dp
private val NAME_INSET_V = 16.dp

/** The cover scrim reaches 92 % ground this far above the name, after a ramp this long. */
private val SCRIM_LEAD = 8.dp
private val SCRIM_RAMP = 24.dp

/**
 * Raised Slate plate, Genre name in Rail Title; card focus (1.1 lift + concentric ring).
 * With a [cover], the key art fills the plate under a ground scrim drawn only once the image
 * has loaded, so a pending or failed cover looks exactly like the plain text tile.
 */
@Composable
private fun GenreTile(label: String, cover: String?, requester: FocusRequester, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale = focusScale(focused)
    var nameHeight by remember { mutableStateOf(0) }
    Box(
        modifier = modifier
            .height(TILE_HEIGHT)
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick)
            .scale(scale)
            .cardFocus(focused)
            .background(Rb.surfaceRaised),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (cover != null) GenreCover(cover, nameHeight = { nameHeight })
        Text(
            label,
            style = RbType.railTitle,
            color = Rb.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = NAME_INSET_H, vertical = NAME_INSET_V)
                .onSizeChanged { nameHeight = it.height },
        )
    }
}

/**
 * The cover and its ground scrim (DESIGN.md, Genres). Both read the painter state in the same
 * frame, and [GenreTileArt.scrimAlpha] decides the scrim, so the art never shows unscrimmed
 * under the name. The request carries the tile size, resolved in composition: a cover already
 * in memory is then a success before the first draw, not one frame of text tile later.
 */
@Composable
private fun GenreCover(url: String, nameHeight: () -> Int) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val context = LocalContext.current
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val sized = constraints.hasBoundedWidth && constraints.hasBoundedHeight && width > 0 && height > 0
    val request = remember(url, width, height, sized) {
        ImageRequest.Builder(context).data(url).crossfade(Rb.FOCUS_MS)
            .apply { if (sized) size(width, height) }
            .build()
    }
    val painter = rememberAsyncImagePainter(model = request, contentScale = ContentScale.Crop)
    val loaded = painter.state as? AsyncImagePainter.State.Success
    val fade by animateFloatAsState(if (loaded != null) 1f else 0f, tween(Rb.FOCUS_MS), label = "coverScrim")
    val scrim = GenreTileArt.scrimAlpha(
        loaded = loaded != null,
        fromMemory = loaded?.result?.dataSource == DataSource.MEMORY_CACHE,
        fade = fade,
    )
    val art = loaded?.painter?.intrinsicSize
    val bias = if (art == null) 0f else GenreTileArt.verticalBias(art.width, art.height)
    Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = BiasAlignment(0f, bias),
        modifier = Modifier.fillMaxSize(),
    )
    if (scrim == null) return@BoxWithConstraints
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val nameTop = size.height - NAME_INSET_V.toPx() - nameHeight()
                val stops = GenreTileArt.scrimStops(size.height, nameTop, SCRIM_LEAD.toPx(), SCRIM_RAMP.toPx())
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = stops.map { (at, alpha) -> at to Rb.ground.copy(alpha = alpha) }.toTypedArray(),
                        startY = 0f,
                        endY = size.height,
                    ),
                    alpha = scrim,
                )
            },
    )
}
