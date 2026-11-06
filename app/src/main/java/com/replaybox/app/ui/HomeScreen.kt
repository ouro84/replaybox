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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import coil.compose.AsyncImage
import com.replaybox.R
import com.replaybox.app.ReplayboxApplication
import com.replaybox.app.update.UpdateInstaller
import com.replaybox.app.update.UpdateState
import com.replaybox.core.data.CatalogStore
import com.replaybox.core.model.Genre
import com.replaybox.core.model.Show
import com.replaybox.core.model.ShowId
import com.replaybox.provider.api.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    onOpenShow: (ShowId, String) -> Unit,
    onOpenLogin: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    var selectedGenre by remember { mutableStateOf<Genre?>(null) }
    val updates by Providers.catalog.updates.collectAsState()
    val updateState by ReplayboxApplication.updater.state.collectAsState()

    // ADR 0002: background refresh at startup, Home listings first. Rows
    // compose instantly from the cache; this only re-ticks them with fresher
    // data (catalog.updates) once the Chaînes answered.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            Providers.catalog.refreshListings(Providers.all)
        }
    }

    TvLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        val update = updateState
        if (update is UpdateState.Ready) {
            item {
                UpdateBanner(update)
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_title),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onOpenSearch, modifier = Modifier.padding(end = 16.dp)) {
                        Text(stringResource(R.string.search_button))
                    }
                    AccountButton(onOpenLogin = onOpenLogin)
                }
            }
        }
        item {
            GenreFilterRow(selected = selectedGenre, onSelect = { selectedGenre = it })
        }
        if (selectedGenre == null) {
            item {
                FavoritesRow(updates = updates, onOpenShow = onOpenShow)
            }
        }
        items(Providers.all) { provider ->
            ProviderRow(provider = provider, genre = selectedGenre, updates = updates, onOpenShow = onOpenShow)
        }
    }
}

/**
 * Discreet in-app update notice (TV launchers do not surface system
 * notifications). The installer only starts on the user's press.
 */
@Composable
private fun UpdateBanner(update: UpdateState.Ready) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.update_ready, update.version.toString()),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(end = 16.dp),
        )
        Button(onClick = { UpdateInstaller.install(context, update.apk) }) {
            Text(stringResource(R.string.update_install))
        }
    }
}

@Composable
private fun GenreFilterRow(selected: Genre?, onSelect: (Genre?) -> Unit) {
    TvLazyRow(
        contentPadding = PaddingValues(horizontal = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            GenreChip(
                label = stringResource(R.string.all_genres),
                selected = selected == null,
                onSelect = { onSelect(null) },
            )
        }
        items(Genre.values().toList()) { genre ->
            GenreChip(
                label = genre.genreLabel(),
                selected = selected == genre,
                onSelect = { onSelect(genre) },
            )
        }
    }
}

@Composable
private fun GenreChip(label: String, selected: Boolean, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text = label,
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
            .border(
                width = 1.dp,
                color = when {
                    selected -> MaterialTheme.colorScheme.primary
                    focused -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.surface
                },
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun AccountButton(onOpenLogin: () -> Unit) {
    val connected = remember { ReplayboxApplication.tf1Auth.hasCredentials() }
    Button(onClick = onOpenLogin) {
        Text(
            text = if (connected) {
                stringResource(R.string.account_connected)
            } else {
                stringResource(R.string.account_button)
            },
        )
    }
}

@Composable
private fun FavoritesRow(updates: Int, onOpenShow: (ShowId, String) -> Unit) {
    var favorites by remember { mutableStateOf<List<Show>>(emptyList()) }
    LaunchedEffect(updates) {
        favorites = Providers.catalog.favoriteShows(CatalogStore.FTV_CHANNEL_IDS + "tf1")
    }
    if (favorites.isEmpty()) return
    Column {
        Text(
            text = stringResource(R.string.favorites_row),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        TvLazyRow(
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(favorites) { show ->
                ShowCard(show = show, onOpenShow = onOpenShow)
            }
        }
    }
}

@Composable
private fun ProviderRow(
    provider: Provider,
    genre: Genre?,
    updates: Int,
    onOpenShow: (ShowId, String) -> Unit,
) {
    var state by remember(provider.channel.id) { mutableStateOf<RowState>(RowState.Loading) }
    LaunchedEffect(provider.channel.id, genre, updates) {
        state = try {
            val shows = provider.shows(genre)
            val asOf = Providers.catalog.listingAt(provider.channel.id)
            val stale = asOf != null && System.currentTimeMillis() - asOf > CatalogStore.TTL_MS
            RowState.Loaded(shows, if (stale) asOf else null)
        } catch (error: Exception) {
            RowState.Failed(error.message ?: "error")
        }
    }
    Column {
        Row(
            modifier = Modifier.padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = provider.channel.name,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.width(12.dp))
            when (val current = state) {
                is RowState.Loading -> Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodySmall)
                is RowState.Failed -> Text(current.message, style = MaterialTheme.typography.bodySmall)
                is RowState.Loaded -> if (current.shows.isEmpty()) {
                    Text(stringResource(R.string.empty_shows), style = MaterialTheme.typography.bodySmall)
                } else if (current.staleAsOf != null) {
                    Text(
                        stringResource(R.string.stale_data, staleDate(current.staleAsOf)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        when (val current = state) {
            is RowState.Loaded -> TvLazyRow(
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(current.shows) { show ->
                    ShowCard(show = show, onOpenShow = onOpenShow)
                }
            }
            else -> Unit
        }
    }
}

private sealed interface RowState {
    object Loading : RowState
    data class Loaded(val shows: List<Show>, val staleAsOf: Long? = null) : RowState
    data class Failed(val message: String) : RowState
}

/** ADR 0002: stale cache is served with its date — "Données du JJ/MM". */
private fun staleDate(asOf: Long): String =
    DateTimeFormatter.ofPattern("dd/MM")
        .withZone(ZoneId.of("Europe/Paris"))
        .format(Instant.ofEpochMilli(asOf))

@Composable
private fun ShowCard(show: Show, onOpenShow: (ShowId, String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val genreLabel = show.genres.firstOrNull()?.genreLabel().orEmpty()
    Column(
        modifier = Modifier
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f),
        ) {
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
