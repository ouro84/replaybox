package com.replaybox.provider.m6

import com.replaybox.core.model.Channel
import com.replaybox.core.model.Episode
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.Genre
import com.replaybox.core.model.Season
import com.replaybox.core.model.SeasonId
import com.replaybox.core.model.Show
import com.replaybox.core.model.ShowId
import com.replaybox.core.model.Stream
import com.replaybox.core.model.StreamType
import com.replaybox.provider.api.CatalogueIndex
import com.replaybox.provider.api.Provider
import com.replaybox.provider.tf1.StreamResolutionException
import com.replaybox.provider.tf1.Tf1Parsers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * M6+ provider, one instance per M6 group Chaîne ([M6Channels.GROUP]). The listing is the
 * Chaîne's slice of the shared M6 Index ([index]); search is local only (decision Q17).
 * Saisons and Épisodes come from every `vi` page of the Programme, loaded once and kept in
 * memory.
 *
 * Every M6 Flux is Widevine (drmtoday). Without [drmTokenProvider] (no account), [stream]
 * fails with the account-required error the player already maps to « compte requis ». With
 * it ([M6Auth.upfrontToken]), the Flux carries the drmtoday licence URL, the upfront token as
 * `x-dt-auth-token` and [LICENSE_RESPONSE_FIELD] for the JSON-wrapped answer. Providers lists
 * the M6 Chaînes only while an M6+ account is stored.
 */
class M6Provider(
    override val channel: Channel,
    private val http: M6Http = M6Http(),
    private val index: CatalogueIndex = m6CatalogueIndex(http),
    private val drmTokenProvider: (suspend (serviceCode: String, clipId: String) -> String)? = null,
    private val localSearch: (suspend (String) -> List<Show>)? = null,
) : Provider {

    private val videoCache = HashMap<ShowId, List<M6Parsers.M6Video>>()
    private val videoMutex = Mutex()

    override suspend fun shows(genre: Genre?): List<Show> {
        val shows = index.shows(channel.id)
        return if (genre == null) shows else shows.filter { genre in it.genres }
    }

    override suspend fun seasons(show: ShowId): List<Season> = M6Parsers.seasons(show, loadVideos(show))

    override suspend fun episodes(season: SeasonId?): List<Episode> {
        requireNotNull(season) { "M6 episodes(season=null) is not supported: the Season id carries the Programme" }
        val show = M6Parsers.showOf(season)
        val (serviceCode, _) = M6Parsers.showParts(show)
        return M6Parsers.episodes(season, serviceCode, loadVideos(show))
    }

    override suspend fun search(query: String): List<Show> = localSearch?.invoke(query) ?: emptyList()

    /** The Widevine DASH manifest URL of [episode], `hd` first. Anonymous; used by the health-check. */
    suspend fun manifestUrl(episode: EpisodeId): String {
        val (serviceCode, clipId) = M6Parsers.episodeParts(episode)
        return M6Parsers.dashManifest(http.get(M6Http.videoPath(serviceCode, clipId)).body)
    }

    override suspend fun stream(episode: EpisodeId): Stream {
        val tokens = drmTokenProvider ?: throw StreamResolutionException(
            Tf1Parsers.Delivery.Failure(null, "ACCOUNT_REQUIRED", "M6+ requires an account (Widevine)", false, true),
        )
        val (serviceCode, clipId) = M6Parsers.episodeParts(episode)
        val manifest = manifestUrl(episode)
        val token = tokens(serviceCode, clipId)
        return Stream(
            manifestUrl = manifest,
            type = StreamType.DASH,
            licenseUrl = LICENSE_URL,
            licenseHeaders = mapOf(LICENSE_TOKEN_HEADER to token),
            // drmtoday answers {"license": "<base64>"} (source: inputstream key `|JBlicense`).
            licenseResponseField = LICENSE_RESPONSE_FIELD,
        )
    }

    private suspend fun loadVideos(show: ShowId): List<M6Parsers.M6Video> {
        videoMutex.withLock { videoCache[show]?.let { return it } }
        val (serviceCode, programId) = M6Parsers.showParts(show)
        val collected = LinkedHashMap<String, M6Parsers.M6Video>()
        // `offset` is an item offset, `limit` capped at 100 (measured 2026-11-07, SPEC §Provider M6+).
        var offset = 0
        var total = Int.MAX_VALUE
        while (offset < minOf(total, MAX_VIDEOS)) {
            val response = http.get(M6Http.videosPath(serviceCode, programId, offset))
            val page = M6Parsers.videoPage(response.body, response.totalCount)
            page.episodes.forEach { collected.putIfAbsent(it.clipId, it) }
            if (page.episodes.isEmpty()) break
            // Without the count header, a short page is the last one.
            if (page.total == null && page.episodes.size < M6Http.PAGE_SIZE) break
            total = page.total ?: total
            offset += M6Http.PAGE_SIZE
        }
        val videos = collected.values.toList()
        videoMutex.withLock { videoCache[show] = videos }
        return videos
    }

    companion object {
        const val LICENSE_URL = "https://lic.drmtoday.com/license-proxy-widevine/cenc/"
        const val LICENSE_TOKEN_HEADER = "x-dt-auth-token"
        const val LICENSE_RESPONSE_FIELD = "license"
        private const val MAX_VIDEOS = 1000
    }
}
