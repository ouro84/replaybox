package com.replaybox.provider.francetv

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
import kotlinx.serialization.json.JsonObject

/**
 * France TV provider (Chaîne France 2..5, franceinfo). Anonymous catalogue
 * over yatta; Flux resolved live at k7 + hdfauth signing — no account, no DRM.
 * One instance per Chaîne shares one HTTP layer; seasons/episodes/stream do
 * not depend on the listing Chaîne (SPEC: program_path prefixes are navigation
 * slugs, not channels).
 */
class FtvProvider(
    private val listingChannel: String,
    override val channel: Channel,
    private val http: FtvHttp = FtvHttp(),
) : Provider {

    private val programCache = HashMap<String, JsonObject>()
    private val programMutex = Mutex()

    override suspend fun shows(genre: Genre?): List<Show> {
        val shows = FtvParsers.showsFromChannel(http.channel(listingChannel))
        // Canonical Genre mapping of sub_categories lands with the Genre filter
        // lot; until then France TV shows carry no genres.
        return if (genre == null) shows else shows.filter { genre in it.genres }
    }

    override suspend fun seasons(show: ShowId): List<Season> {
        val detail = programDetail(FtvParsers.programPath(show))
        return FtvParsers.seasonsFromProgram(detail, show)
    }

    override suspend fun episodes(season: SeasonId?): List<Episode> {
        val path = season?.let(FtvParsers::programPathOfSeason)
            ?: throw IllegalArgumentException("France TV episodes(season=null) requires a season id carrying the program path")
        val detail = programDetail(path)
        return FtvParsers.episodesFromProgram(detail, season)
    }

    /**
     * SPEC §Recherche: France TV has no discovered search route — search is
     * served from the local cache (Room lot) by normalized title. Until the
     * cache exists, this stays empty.
     */
    override suspend fun search(query: String): List<Show> = emptyList()

    override suspend fun stream(episode: EpisodeId): Stream {
        val siId = FtvParsers.siIdOf(episode)
        val flux = FtvParsers.streamFromK7(http.k7(siId))
        val hdfauth = http.sign(flux.manifestUrl, flux.signBase)
        val signed = FtvParsers.signedUrl(hdfauth) ?: flux.manifestUrl
        return Stream(
            manifestUrl = signed,
            type = flux.type,
            subtitleUrl = flux.subtitleUrl,
            subtitleLanguage = flux.subtitleLanguage,
        )
    }

    private suspend fun programDetail(path: String): JsonObject {
        programMutex.withLock { programCache[path]?.let { return it } }
        val detail = http.program(path)
        programMutex.withLock { programCache[path] = detail }
        return detail
    }
}
