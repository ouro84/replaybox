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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import coil.compose.AsyncImage
import com.replaybox.R
import com.replaybox.app.ReplayboxApplication
import com.replaybox.core.model.Genre
import com.replaybox.core.model.Show
import com.replaybox.core.model.ShowId
import com.replaybox.provider.api.Provider

@Composable
fun HomeScreen(onOpenShow: (ShowId, String) -> Unit, onOpenLogin: () -> Unit) {
    TvLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
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
                AccountButton(onOpenLogin = onOpenLogin)
            }
        }
        items(Providers.all) { provider ->
            ProviderRow(provider = provider, onOpenShow = onOpenShow)
        }
    }
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
private fun ProviderRow(provider: Provider, onOpenShow: (ShowId, String) -> Unit) {
    var state by remember(provider.channel.id) { mutableStateOf<RowState>(RowState.Loading) }
    LaunchedEffect(provider.channel.id) {
        state = try {
            RowState.Loaded(provider.shows(null))
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
    data class Loaded(val shows: List<Show>) : RowState
    data class Failed(val message: String) : RowState
}

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
