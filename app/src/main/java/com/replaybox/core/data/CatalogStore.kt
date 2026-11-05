package com.replaybox.core.data

import com.replaybox.core.model.Episode
import com.replaybox.core.model.Genre
import com.replaybox.core.model.Season
import com.replaybox.core.model.Show
import com.replaybox.core.model.ShowId
import com.replaybox.core.model.SeasonId
import com.replaybox.core.model.Titles
import com.replaybox.provider.api.Provider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Cache-first catalog (ADR 0002): listings and program pages are served from
 * Room with a 12 h TTL and refreshed in the background at startup, Home rows
 * first. When a Provider is down, the stale cache is served and the row is
 * marked with its date instead of failing the evening. Flux resolution is
 * never cached — signed manifests stay live.
 */
class CatalogStore(
    private val dao: CatalogDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    private val _updates = MutableStateFlow(0)

    /** Ticks after every successful store write so the UI can re-compose. */
    val updates: StateFlow<Int> get() = _updates

    suspend fun shows(provider: Provider, genre: Genre?): List<Show> {
        val channel = provider.channel.id
        val fresh = dao.listingAt(channel)?.let { clock() - it < TTL_MS } == true
        if (!fresh) {
            try {
                val fetched = provider.shows(null)
                dao.putListing(channel, fetched, clock())
                tick()
                return filter(fetched, genre)
            } catch (error: Exception) {
                val cached = dao.shows(channel)
                if (cached.isEmpty()) throw error
                return filter(cached, genre)
            }
        }
        return filter(dao.shows(channel), genre)
    }

    suspend fun seasons(provider: Provider, show: ShowId): List<Season> {
        val fresh = dao.programAt(show.value)?.let { clock() - it < TTL_MS } == true
        if (fresh) return dao.seasons(show.value)
        return try {
            val fetched = provider.seasons(show)
            dao.putSeasons(show.value, fetched, clock())
            tick()
            fetched
        } catch (error: Exception) {
            val cached = dao.seasons(show.value)
            if (cached.isEmpty()) throw error
            cached
        }
    }

    suspend fun episodes(provider: Provider, season: SeasonId?): List<Episode> {
        if (season == null) return provider.episodes(null)
        val fresh = dao.programAt(season.value)?.let { clock() - it < TTL_MS } == true
        if (fresh) return dao.episodes(season.value)
        return try {
            val fetched = provider.episodes(season)
            dao.putEpisodes(season.value, fetched, clock())
            tick()
            fetched
        } catch (error: Exception) {
            val cached = dao.episodes(season.value)
            if (cached.isEmpty()) throw error
            cached
        }
    }

    /**
     * Forced background refresh of the Home listings (startup priority,
     * ADR 0002). A failing Chaîne is skipped — the evening never blocks on
     * the network; its stale date surfaces in the Home banner instead.
     */
    suspend fun refreshListings(providers: List<Provider>) {
        for (provider in providers) {
            try {
                val fetched = provider.shows(null)
                dao.putListing(provider.channel.id, fetched, clock())
                tick()
            } catch (_: Exception) {
                // Degraded mode: keep serving the cache.
            }
        }
    }

    /** Freshness of a Chaîne listing, for the dated degraded-mode banner. */
    suspend fun listingAt(channelId: String): Long? = dao.listingAt(channelId)

    /**
     * Local search over the cached listings of the given Chaînes (SPEC
     * §Recherche: France TV has no discovered search route — its search is a
     * cache filter by normalized title). Cache-only: what was never listed
     * cannot be found.
     */
    suspend fun searchShows(channelIds: List<String>, query: String): List<Show> {
        val wanted = Titles.normalize(query)
        if (wanted.isEmpty()) return emptyList()
        val matches = LinkedHashMap<String, Show>()
        for (channelId in channelIds) {
            for (show in dao.shows(channelId)) {
                if (Titles.normalize(show.title).contains(wanted)) {
                    matches.putIfAbsent(show.id.value, show)
                }
            }
        }
        return matches.values.toList()
    }

    private fun tick() {
        _updates.value = _updates.value + 1
    }

    private fun filter(shows: List<Show>, genre: Genre?): List<Show> =
        if (genre == null) shows else shows.filter { genre in it.genres }

    companion object {
        const val TTL_MS: Long = 12 * 60 * 60 * 1000
    }
}
