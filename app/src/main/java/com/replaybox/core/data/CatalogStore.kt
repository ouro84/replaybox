package com.replaybox.core.data

import com.replaybox.core.model.Episode
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.EpisodeProgress
import com.replaybox.core.model.Genre
import com.replaybox.core.model.ChannelShow
import com.replaybox.core.model.Progress
import com.replaybox.core.model.ProgressContext
import com.replaybox.core.model.ResumeItem
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

    // ---- Progress (device-local, never synchronized — glossary) ----

    /**
     * Saves the playhead. A null [context] keeps the Programme context an
     * earlier save recorded, so a context-less caller never orphans the row
     * from the "Reprendre" rail.
     */
    suspend fun saveProgress(
        episodeId: EpisodeId,
        positionSeconds: Long,
        durationSeconds: Int?,
        context: ProgressContext? = null,
    ) {
        if (positionSeconds <= 0) return
        val previous = if (context == null) dao.progress(episodeId.value) else null
        dao.putProgress(
            ProgressEntity(
                episodeId = episodeId.value,
                positionSeconds = positionSeconds,
                durationSeconds = durationSeconds,
                updatedAt = clock(),
                showId = context?.showId?.value ?: previous?.showId,
                showTitle = context?.showTitle ?: previous?.showTitle,
                episodeTitle = context?.episodeTitle ?: previous?.episodeTitle,
                thumbnailUrl = context?.thumbnailUrl ?: previous?.thumbnailUrl,
            ),
        )
        tick()
    }

    /**
     * The Home "Reprendre" rail: one card per Programme (its newest
     * unfinished Épisode), most recent first, finished Épisodes and rows without a
     * Programme context left out (ResumePolicy). Art and Chaîne come from the
     * cached listings of [channelIds] when the Programme is still listed.
     */
    suspend fun continueWatching(channelIds: List<String>, limit: Int = RESUME_LIMIT): List<ResumeItem> {
        val listed = HashMap<String, ChannelShow>()
        for (channelId in channelIds) {
            for (show in dao.shows(channelId)) listed.putIfAbsent(show.id.value, ChannelShow(show, channelId))
        }
        val seenShows = HashSet<String>()
        val items = ArrayList<ResumeItem>()
        for (row in dao.recentProgress(RESUME_SCAN)) {
            val showId = row.showId ?: continue
            if (showId in seenShows) continue
            val progress = Progress(EpisodeId(row.episodeId), row.positionSeconds, row.updatedAt)
            // A finished row does not claim its Programme: an older unfinished
            // Épisode still resumes (same rule as the Programme page hero).
            if (ResumePolicy.resumeAt(progress, row.durationSeconds) == null) continue
            seenShows.add(showId)
            val listing = listed[showId]
            items += ResumeItem(
                episodeId = EpisodeId(row.episodeId),
                showId = ShowId(showId),
                showTitle = row.showTitle ?: listing?.show?.title.orEmpty(),
                episodeTitle = row.episodeTitle.orEmpty(),
                thumbnailUrl = row.thumbnailUrl ?: listing?.show?.backdropUrl,
                backdropUrl = listing?.show?.backdropUrl ?: row.thumbnailUrl,
                channelId = listing?.channelId,
                positionSeconds = row.positionSeconds,
                durationSeconds = row.durationSeconds,
                updatedAtEpochMs = row.updatedAt,
            )
            if (items.size >= limit) break
        }
        return items
    }

    /** "Retirer de Reprendre": forgets the playhead of this Épisode. */
    suspend fun removeFromContinueWatching(episodeId: EpisodeId) {
        dao.deleteProgress(episodeId.value)
        tick()
    }

    /**
     * Home Genre rails, cache only: every Genre in glossary order with its
     * Programmes across [channelIds], interleaved Chaîne by Chaîne (the
     * listings carry no broadcast date to sort on) and de-duplicated on the
     * normalized title. Empty Genres are dropped.
     */
    suspend fun genreRails(channelIds: List<String>): List<Pair<Genre, List<ChannelShow>>> {
        val listings = channelIds.map { channelId -> dao.shows(channelId).map { ChannelShow(it, channelId) } }
        return Genre.values().mapNotNull { genre ->
            val perChannel = listings.map { listing -> listing.filter { genre in it.show.genres } }
            val merged = ArrayList<ChannelShow>()
            val seen = HashSet<String>()
            val longest = perChannel.maxOfOrNull { it.size } ?: 0
            for (index in 0 until longest) {
                for (listing in perChannel) {
                    val candidate = listing.getOrNull(index) ?: continue
                    if (seen.add(Titles.normalize(candidate.show.title))) merged += candidate
                }
            }
            if (merged.isEmpty()) null else genre to merged
        }
    }

    /** Favorites with the Chaîne they are listed on, most recently pinned first. */
    suspend fun favoriteChannelShows(channelIds: List<String>): List<ChannelShow> {
        val ids = dao.favoriteIds()
        if (ids.isEmpty()) return emptyList()
        val byId = HashMap<String, ChannelShow>()
        for (channelId in channelIds) {
            for (show in dao.shows(channelId)) byId.putIfAbsent(show.id.value, ChannelShow(show, channelId))
        }
        return ids.mapNotNull { byId[it] }
    }

    /**
     * Programme page: Progression of each listed Épisode. Positions under
     * ResumePolicy.MIN_POSITION_SECONDS count as not started and are absent.
     */
    suspend fun episodeProgress(ids: List<EpisodeId>): Map<EpisodeId, EpisodeProgress> {
        if (ids.isEmpty()) return emptyMap()
        return dao.progressFor(ids.map { it.value })
            .filter { it.positionSeconds >= ResumePolicy.MIN_POSITION_SECONDS }
            .associate { row ->
                val duration = row.durationSeconds
                val watched = duration != null && duration > 0 &&
                    row.positionSeconds >= duration * ResumePolicy.FINISHED_RATIO
                EpisodeId(row.episodeId) to EpisodeProgress(row.positionSeconds, duration, watched)
            }
    }

    /** The Programme as cached in the listings of [channelIds], with its Chaîne; null when unlisted. */
    suspend fun listedShow(showId: ShowId, channelIds: List<String>): ChannelShow? {
        for (channelId in channelIds) {
            dao.shows(channelId).firstOrNull { it.id == showId }?.let { return ChannelShow(it, channelId) }
        }
        return null
    }

    /**
     * The Reprendre target of this Programme: its newest unfinished Épisode,
     * or null. Unlike the Home rail, a newer finished Épisode does not hide an
     * older unfinished one: on its own page every Progression counts.
     */
    suspend fun resumeFor(showId: ShowId, channelIds: List<String>): ResumeItem? {
        val row = dao.recentProgress(RESUME_SCAN).firstOrNull {
            it.showId == showId.value &&
                ResumePolicy.resumeAt(Progress(EpisodeId(it.episodeId), it.positionSeconds, it.updatedAt), it.durationSeconds) != null
        } ?: return null
        val listing = listedShow(showId, channelIds)
        return ResumeItem(
            episodeId = EpisodeId(row.episodeId),
            showId = showId,
            showTitle = row.showTitle ?: listing?.show?.title.orEmpty(),
            episodeTitle = row.episodeTitle.orEmpty(),
            thumbnailUrl = row.thumbnailUrl ?: listing?.show?.backdropUrl,
            backdropUrl = listing?.show?.backdropUrl ?: row.thumbnailUrl,
            channelId = listing?.channelId,
            positionSeconds = row.positionSeconds,
            durationSeconds = row.durationSeconds,
            updatedAtEpochMs = row.updatedAt,
        )
    }

    suspend fun progressFor(episodeId: EpisodeId): Progress? {
        val saved = dao.progress(episodeId.value) ?: return null
        return Progress(episodeId, saved.positionSeconds, saved.updatedAt)
    }

    /** Resume position for this Épisode, or null to start from zero (SPEC §Player). */
    suspend fun resumeAtFor(episodeId: EpisodeId): Long? {
        val saved = dao.progress(episodeId.value) ?: return null
        return ResumePolicy.resumeAt(
            Progress(episodeId, saved.positionSeconds, saved.updatedAt),
            saved.durationSeconds,
        )
    }

    /**
     * Favorites rendered on the Home row, most recent first. Only shows
     * still present in the cached listings are returned.
     */
    suspend fun favoriteShows(channelIds: List<String>): List<Show> {
        val ids = dao.favoriteIds()
        if (ids.isEmpty()) return emptyList()
        val wanted = ids.toSet()
        val byId = HashMap<String, Show>()
        for (channelId in channelIds) {
            for (show in dao.shows(channelId)) {
                if (show.id.value in wanted) byId[show.id.value] = show
            }
        }
        return ids.mapNotNull { byId[it] }
    }

    suspend fun isFavorite(showId: ShowId): Boolean =
        dao.favoriteIds().contains(showId.value)

    /** Returns the new state: true when the show is now pinned. */
    suspend fun toggleFavorite(showId: ShowId): Boolean {
        val pinned = !isFavorite(showId)
        if (pinned) dao.putFavorite(showId.value, clock()) else dao.removeFavorite(showId.value)
        tick()
        return pinned
    }

    private fun filter(shows: List<Show>, genre: Genre?): List<Show> =
        if (genre == null) shows else shows.filter { genre in it.genres }

    companion object {
        const val TTL_MS: Long = 12 * 60 * 60 * 1000

        /** Cap of the Home "Reprendre" rail. */
        const val RESUME_LIMIT = 20

        /** Progress rows scanned to fill the rail (several per Programme). */
        private const val RESUME_SCAN = 200

        /** Channels carrying France TV listings (SPEC §Provider France TV). */
        val FTV_CHANNEL_IDS = listOf("france-2", "france-3", "france-4", "france-5", "franceinfo")
    }
}
