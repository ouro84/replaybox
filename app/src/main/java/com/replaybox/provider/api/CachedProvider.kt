package com.replaybox.provider.api

import com.replaybox.core.data.CatalogStore
import com.replaybox.core.model.Episode
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.Genre
import com.replaybox.core.model.Season
import com.replaybox.core.model.SeasonId
import com.replaybox.core.model.Show
import com.replaybox.core.model.ShowId
import com.replaybox.core.model.Stream

/**
 * Cache-first decorator (ADR 0002). Catalog reads go through the Room store;
 * search and Flux resolution stay live — search hits the Chaîne directly and
 * manifests are signed, short-lived, never cached.
 */
class CachedProvider(
    private val inner: Provider,
    private val store: CatalogStore,
) : Provider {

    override val channel: com.replaybox.core.model.Channel get() = inner.channel

    /** The network Provider behind the cache, for forced refreshes. */
    val origin: Provider get() = inner

    override suspend fun shows(genre: Genre?): List<Show> = store.shows(inner, genre)

    override suspend fun seasons(show: ShowId): List<Season> = store.seasons(inner, show)

    override suspend fun episodes(season: SeasonId?): List<Episode> = store.episodes(inner, season)

    override suspend fun search(query: String): List<Show> = inner.search(query)

    override suspend fun stream(episode: EpisodeId): Stream = inner.stream(episode)
}
