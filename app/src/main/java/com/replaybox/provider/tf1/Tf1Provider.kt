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
 * the wat.tv chain of yt-dlp (mediainfocombo, anonymous, no DRM). When a TF1
 * account is configured (tokenProvider), the Flux carries a Bearer token and
 * DRM-protected fictions resolve with their Widevine license.
 */
class Tf1Provider(
    private val http: Tf1Http = Tf1Http(),
    private val tokenProvider: (suspend (forceRefresh: Boolean) -> String?)? = null,
    // Injected so the provider also runs on a plain JVM (nightly health-check, CI).
    private val log: (String) -> Unit = { message -> android.util.Log.w(TAG, message) },
) : Provider {

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

        var lastFailure: Tf1Parsers.Delivery.Failure? = null
        for (attempt in 0 until 2) {
            val token = tokenProvider?.invoke(attempt > 0)
            try {
                val delivery = Tf1Parsers.delivery(http.mediainfo(mediaId, token))
                when (delivery) {
                    is Tf1Parsers.Delivery.Ok -> return Tf1Parsers.streamFrom(delivery)
                    is Tf1Parsers.Delivery.Failure -> {
                        lastFailure = delivery
                        log(
                            "delivery refused mediaId=$mediaId attempt=$attempt code=${delivery.httpDeliveryCode}" +
                                " error=${delivery.errorCode} desc=${delivery.description} geo=${delivery.geoBlocked}" +
                                " accountOrDrm=${delivery.accountOrDrm} withToken=${token != null}",
                        )
                        if (tokenProvider == null) throw StreamResolutionException(delivery)
                    }
                }
            } catch (error: Tf1HttpException) {
                log("mediainfo HTTP ${error.message}")
                if (tokenProvider == null || attempt > 0) throw error
            }
        }
        throw StreamResolutionException(
            lastFailure ?: Tf1Parsers.Delivery.Failure(null, null, "TF1 delivery failed", false, false),
        )
    }

    private suspend fun loadReplayVideos(show: ShowId): List<Tf1Parsers.ReplayVideo> {
        replayMutex.withLock {
            replayCache[show]?.let { return it }
        }
        val collected = LinkedHashMap<String, Tf1Parsers.ReplayVideo>()
        // TF1 reads `offset` as a page index (items offset*limit until (offset+1)*limit),
        // not as an item index — measured 2026-11-06, see SPEC §TF1.
        var pageIndex = 0
        var total = Int.MAX_VALUE
        while (pageIndex * Tf1Http.PAGE_LIMIT < minOf(total, MAX_VIDEOS)) {
            val page = Tf1Parsers.replayPage(
                http.graphql(
                    Tf1QueryIds.PROGRAM_BY_SLUG,
                    http.programVideosVariables(show.value, pageIndex, Tf1Http.PAGE_LIMIT),
                ),
            )
            total = page.total
            page.videos.forEach { collected.putIfAbsent(it.slug, it) }
            if (page.videos.isEmpty()) break
            pageIndex++
        }
        val videos = collected.values.toList()
        replayMutex.withLock {
            replayCache[show] = videos
        }
        return videos
    }

    private companion object {
        const val MAX_VIDEOS = 1000
        const val TAG = "Tf1Provider"
    }
}
