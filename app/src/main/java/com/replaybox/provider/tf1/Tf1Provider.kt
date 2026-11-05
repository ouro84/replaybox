package com.replaybox.provider.tf1

import com.replaybox.core.model.Channel
import com.replaybox.core.model.Episode
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.Genre
import com.replaybox.core.model.Season
import com.replaybox.core.model.SeasonId
import com.replaybox.core.model.Show
import com.replaybox.core.model.ShowId
import com.replaybox.core.model.Stream
import com.replaybox.provider.api.Provider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * TF1+ provider. Catalogue over graphql persisted queries; Flux resolution follows
 * the wat.tv chain of yt-dlp (mediainfocombo, anonymous, no DRM).
 *
 * Measured 2026-11-05: TF1 requires a signed-in account for most replay fiction;
 * only a subset (e.g. "Les 12 coups de midi") still delivers anonymously.
 * Account-gated content surfaces as StreamResolutionException(accountOrDrmRequired).
 */
class Tf1Provider(private val http: Tf1Http = Tf1Http()) : Provider {

    override val channel: Channel = Channel("tf1", "TF1+")

    private val replayCache = HashMap<ShowId, List<Tf1Parsers.ReplayVideo>>()
    private val replayMutex = Mutex()

    override suspend fun shows(genre: Genre?): List<Show> {
        val root = http.graphql(Tf1QueryIds.PROGRAMS, http.programsVariables(channel.id, offset = 0, limit = 40))
        val shows = Tf1Parsers.showsFromPrograms(root)
        return if (genre == null) shows else shows.filter { genre in it.genres }
    }

    override suspend fun seasons(show: ShowId): List<Season> {
        val videos = loadReplayVideos(show)
        return Tf1Parsers.seasonsFromVideos(show, videos)
    }

    override suspend fun episodes(season: SeasonId?): List<Episode> {
        if (season == null) {
            throw IllegalArgumentException("TF1 episodes(season=null) requires a program slug in the season id")
        }
        val showId = ShowId(season.value.substringBefore(Tf1Parsers.SEPARATOR))
        val videos = loadReplayVideos(showId)
        return Tf1Parsers.episodesFromVideos(season, showId, videos)
    }

    override suspend fun search(query: String): List<Show> {
        val root = http.graphql(Tf1QueryIds.SEARCH_PROGRAMS, http.searchProgramsVariables(query))
        return Tf1Parsers.showsFromSearch(root)
    }

    override suspend fun stream(episode: EpisodeId): Stream {
        val showSlug = episode.value.substringBefore(Tf1Parsers.SEPARATOR)
        val videoSlug = episode.value.substringAfter(Tf1Parsers.SEPARATOR)
        val root = http.graphql(
            Tf1QueryIds.VIDEO_BY_SLUG,
            http.videoBySlugVariables(showSlug, videoSlug),
        )
        val detail = Tf1Parsers.videoDetail(root)
            ?: throw StreamResolutionException(
                Tf1Parsers.Delivery.Failure(null, null, "Episode not found", false, false),
            )
        if (detail.type != null && detail.type != Tf1Parsers.REPLAY_TYPE) {
            throw StreamResolutionException(
                Tf1Parsers.Delivery.Failure(null, null, "Not a REPLAY episode", false, false),
            )
        }
        val mediaId = detail.streamId
            ?: throw StreamResolutionException(
                Tf1Parsers.Delivery.Failure(null, null, "No internal media id", false, false),
            )
        return Tf1Parsers.streamFrom(Tf1Parsers.delivery(http.mediainfo(mediaId)))
    }

    private suspend fun loadReplayVideos(show: ShowId): List<Tf1Parsers.ReplayVideo> {
        replayMutex.withLock {
            replayCache[show]?.let { return it }
        }
        val collected = LinkedHashMap<String, Tf1Parsers.ReplayVideo>()
        var offset = 0
        var total = Int.MAX_VALUE
        while (offset < total && offset < MAX_VIDEOS) {
            val page = Tf1Parsers.replayPage(
                http.graphql(Tf1QueryIds.PROGRAM_BY_SLUG, http.programVideosVariables(show.value, offset, Tf1Http.PAGE_LIMIT)),
            )
            total = page.total
            page.videos.forEach { collected.putIfAbsent(it.slug, it) }
            if (page.videos.isEmpty()) break
            offset += page.videos.size
        }
        val videos = collected.values.toList()
        replayMutex.withLock {
            replayCache[show] = videos
        }
        return videos
    }

    private companion object {
        const val MAX_VIDEOS = 1000
    }
}
