package com.replaybox.provider.api

import com.replaybox.core.model.Channel
import com.replaybox.core.model.Episode
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.Genre
import com.replaybox.core.model.Season
import com.replaybox.core.model.SeasonId
import com.replaybox.core.model.Show
import com.replaybox.core.model.ShowId
import com.replaybox.core.model.Stream

interface Provider {
    val channel: Channel

    suspend fun shows(genre: Genre?): List<Show>

    suspend fun seasons(show: ShowId): List<Season>

    suspend fun episodes(season: SeasonId?): List<Episode>

    suspend fun search(query: String): List<Show>

    suspend fun stream(episode: EpisodeId): Stream
}
