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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import coil.compose.AsyncImage
import com.replaybox.R
import com.replaybox.core.model.Episode
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.Season
import com.replaybox.core.model.SeasonId
import com.replaybox.core.model.ShowId
import kotlinx.coroutines.launch

@Composable
fun ShowScreen(showId: ShowId, title: String, onOpenEpisode: (EpisodeId, String) -> Unit) {
    val provider = Providers.resolveShow(showId.value)
    var seasons by remember { mutableStateOf<List<Season>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedSeason by remember { mutableStateOf<SeasonId?>(null) }
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var pinned by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(showId) {
        try {
            seasons = provider.seasons(showId)
            selectedSeason = seasons?.firstOrNull()?.id
            pinned = Providers.catalog.isFavorite(showId)
        } catch (e: Exception) {
            error = e.message
        }
    }
    LaunchedEffect(selectedSeason) {
        val season = selectedSeason ?: return@LaunchedEffect
        try {
            episodes = provider.episodes(season)
        } catch (e: Exception) {
            error = e.message
        }
    }

    TvLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                PinButton(pinned = pinned, onToggle = {
                    scope.launch { pinned = Providers.catalog.toggleFavorite(showId) }
                })
            }
            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        }
        val seasonList = seasons.orEmpty()
        if (seasonList.size > 1) {
            item {
                Text(
                    text = stringResource(R.string.seasons_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                TvLazyRow(
                    contentPadding = PaddingValues(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(seasonList) { season ->
                        SeasonChip(
                            season = season,
                            selected = season.id == selectedSeason,
                            onSelect = { selectedSeason = season.id },
                        )
                    }
                }
            }
        }
        item {
            Text(
                text = stringResource(R.string.episodes_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
        if (episodes.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.loading),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        } else {
            items(episodes) { episode ->
                EpisodeRow(episode = episode, onOpenEpisode = onOpenEpisode)
            }
        }
    }
}

@Composable
private fun PinButton(pinned: Boolean, onToggle: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text = stringResource(if (pinned) R.string.pin_remove else R.string.pin_add),
        style = MaterialTheme.typography.bodyMedium,
        color = when {
            pinned -> MaterialTheme.colorScheme.primary
            focused -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        },
        modifier = Modifier
            .clickable { onToggle() }
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = 1.dp,
                color = if (pinned || focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SeasonChip(season: Season, selected: Boolean, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text = season.title,
        style = MaterialTheme.typography.bodyMedium,
        color = when {
            selected -> MaterialTheme.colorScheme.primary
            focused -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        },
        modifier = Modifier
            .clickable { onSelect() }
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun EpisodeRow(episode: Episode, onOpenEpisode: (EpisodeId, String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .clickable { onOpenEpisode(episode.id, episode.title) }
            .focusable()
            .onFocusChanged { focused = it.isFocused },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(180.dp)
                .aspectRatio(16f / 9f),
        ) {
            AsyncImage(
                model = episode.thumbnailUrl,
                contentDescription = episode.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column {
            Text(episode.title, style = MaterialTheme.typography.bodyLarge)
            episode.broadcastDate?.let {
                Text(it, style = MaterialTheme.typography.labelSmall)
            }
            episode.durationSeconds?.let { seconds ->
                Text(
                    text = "${seconds / 60} min",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
