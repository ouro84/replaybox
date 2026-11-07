package com.replaybox.core.data

import com.replaybox.core.model.Episode
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.EpisodeProgress
import com.replaybox.core.model.Genre
import com.replaybox.core.model.Genres
import com.replaybox.core.model.ChannelShow
import com.replaybox.core.model.Progress
import com.replaybox.core.model.ProgressContext
import com.replaybox.core.model.ResumeItem
import com.replaybox.core.model.Season
import com.replaybox.core.model.Show
import com.replaybox.core.model.ShowId
import com.replaybox.core.model.SeasonId
import com.replaybox.core.model.Titles
import com.replaybox.core.model.UpNextItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.replaybox.provider.api.CachedProvider
import com.replaybox.provider.api.Provider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

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
                forget(channel)
                tick()
                return filter(fetched.map(Genres::canonical), genre)
            } catch (error: Exception) {
                val cached = listing(channel)
                if (cached.isEmpty()) throw error
                return filter(cached, genre)
            }
        }
        return filter(listing(channel), genre)
    }

    suspend fun seasons(provider: Provider, show: ShowId): List<Season> {
        val fresh = dao.programAt(show.value)?.let { clock() - it < TTL_MS } == true
        if (fresh) return dao.seasons(show.value)
        return try {
            val fetched = provider.network().seasons(show)
            dao.putSeasons(show.value, fetched, clock())
            tick()
            fetched
        } catch (error: Exception) {
            val cached = dao.seasons(show.value)
            if (cached.isEmpty()) throw error
            cached
        }
    }

    /**
     * Épisodes of a Saison, cache-first. Épisodes whose known end of availability
     * is past are left out (ROADMAP §M4): a 12 h cache would otherwise list them.
     * Listed by ascending Épisode number: TF1 returns a batch upload in no
     * particular order. The sort is stable and unnumbered Épisodes go last, so
     * a Saison without numbers (news, magazines) keeps the broadcaster's order.
     * Without a Saison the list spans several, so it is left as the Provider gave it.
     */
    suspend fun episodes(provider: Provider, season: SeasonId?): List<Episode> {
        val episodes = loadEpisodes(provider, season).withoutExpired()
        return if (season == null) episodes else episodes.sortedWith(compareBy(nullsLast()) { it.number })
    }

    private suspend fun loadEpisodes(provider: Provider, season: SeasonId?): List<Episode> {
        if (season == null) return provider.network().episodes(null)
        val fresh = dao.programAt(season.value)?.let { clock() - it < TTL_MS } == true
        if (fresh) return dao.episodes(season.value)
        return try {
            val fetched = provider.network().episodes(season)
            dao.putEpisodes(season.value, fetched, clock())
            tick()
            fetched
        } catch (error: Exception) {
            val cached = dao.episodes(season.value)
            if (cached.isEmpty()) throw error
            cached
        }
    }

    /** The network side of a cache-first Provider: this store is the cache. */
    private fun Provider.network(): Provider = (this as? CachedProvider)?.origin ?: this

    private fun now(): Instant = Instant.ofEpochMilli(clock())

    private fun List<Episode>.withoutExpired(): List<Episode> {
        val now = now()
        return filterNot { FreshnessPolicy.isExpired(it, now) }
    }

    /**
     * Forced background refresh of the Home listings (startup priority,
     * ADR 0002). A failing Chaîne is skipped — the evening never blocks on
     * the network; its stale date surfaces in the Home banner instead.
     * Returns how many Chaînes failed, so an empty Home can tell « offline »
     * from « nothing listed » (decision Round 8 Q4). Santé: each Chaîne's
     * `origin.shows` goes through its HealthTrackingProvider, which records
     * the outcome per Chaîne; LocalHealthStore reports the group KO while any
     * Chaîne failed its latest pass.
     */
    suspend fun refreshListings(providers: List<Provider>): Int {
        var failed = 0
        for (provider in providers) {
            try {
                // A cache-first Provider would serve the fresh cache: go to its network side.
                val source = (provider as? CachedProvider)?.origin ?: provider
                val fetched = source.shows(null)
                dao.putListing(provider.channel.id, fetched, clock())
                forget(provider.channel.id)
                tick()
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                // Degraded mode: keep serving the cache.
                failed += 1
            }
        }
        return failed
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
            for (show in listing(channelId)) {
                if (Titles.normalize(show.title).contains(wanted)) {
                    matches.putIfAbsent(show.id.value, show)
                }
            }
        }
        return SearchRanking.rank(query, matches.values.toList())
    }

    private fun tick() {
        _updates.value = _updates.value + 1
    }

    /**
     * Per-Chaîne memo of the cached listing: the TF1 Index holds ~2900
     * Programmes and every Home / search read would otherwise reload them
     * from Room. An entry is valid only for the listing date it was read
     * at, and every write through this store drops it. Genres go through
     * [Genres.canonical] on read, so a listing stored before a Genre rule
     * changed follows the new rule without waiting for a crawl.
     */
    private val listingMemo = HashMap<String, Pair<Long, List<Show>>>()
    private val listingMutex = Mutex()

    private suspend fun listing(channelId: String): List<Show> {
        val at = dao.listingAt(channelId)
        listingMutex.withLock {
            listingMemo[channelId]?.takeIf { at != null && it.first == at }?.let { return it.second }
        }
        val shows = dao.shows(channelId).map(Genres::canonical)
        if (at != null) listingMutex.withLock { listingMemo[channelId] = at to shows }
        return shows
    }

    private suspend fun forget(channelId: String) {
        listingMutex.withLock { listingMemo.remove(channelId) }
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
     * The end of availability comes from the episodes cache (no network):
     * an expired Épisode leaves the rail, its Progression stays stored, and an
     * Épisode missing from that cache carries no end and never expires here.
     */
    suspend fun continueWatching(channelIds: List<String>, limit: Int = RESUME_LIMIT): List<ResumeItem> {
        val rows = dao.recentProgress(RESUME_SCAN)
        val listed = listedByIds(rows.mapNotNull { it.showId }, channelIds)
        val ends = availabilityEnds(rows.map { it.episodeId })
        val now = now()
        val seenShows = HashSet<String>()
        val items = ArrayList<ResumeItem>()
        for (row in rows) {
            val showId = row.showId ?: continue
            if (showId in seenShows) continue
            val progress = Progress(EpisodeId(row.episodeId), row.positionSeconds, row.updatedAt)
            // A finished or expired row does not claim its Programme: an older
            // unfinished Épisode still resumes (same rule as the Programme page hero).
            if (ResumePolicy.resumeAt(progress, row.durationSeconds) == null) continue
            val availableUntil = ends[row.episodeId]
            if (availableUntil?.isBefore(now) == true) continue
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
                availableUntil = availableUntil,
            )
            if (items.size >= limit) break
        }
        return items
    }

    /** Known ends of availability of these Épisodes, from the episodes cache only. */
    private suspend fun availabilityEnds(episodeIds: List<String>): Map<String, Instant> {
        if (episodeIds.isEmpty()) return emptyMap()
        return dao.episodesById(episodeIds)
            .mapNotNull { episode -> episode.availableUntil?.let { episode.id.value to it } }
            .toMap()
    }

    /**
     * « Retirer d'À suivre ». An Épisode in progress forgets its playhead; either way the
     * Épisode is recorded as dismissed for its Programme, so a pinned Programme does not offer
     * that same Épisode again (UpNextPolicy). A newer candidate Épisode shows again.
     */
    suspend fun removeFromUpNext(item: UpNextItem) {
        if (item is UpNextItem.InProgress) dao.deleteProgress(item.episodeId.value)
        dao.putDismissal(item.showId.value, item.episodeId.value)
        tick()
    }

    /**
     * The Home « À suivre » rail (ROADMAP §M4): the Épisodes in progress ([continueWatching])
     * plus, for each pinned Programme listed on [channelIds] without one, the new Épisode it
     * offers (UpNextPolicy), from the cache only: [refreshPinned] keeps it current. Expired
     * and dismissed Épisodes are left out; newest event first, one card per Programme.
     */
    suspend fun upNext(channelIds: List<String>, limit: Int = RESUME_LIMIT): List<UpNextItem> {
        val inProgress = continueWatching(channelIds, limit).map { UpNextItem.InProgress(it) }
        val claimed = inProgress.mapTo(HashSet()) { it.showId }
        val now = now()
        val fresh = favoriteChannelShows(channelIds).filter { it.show.id !in claimed }.mapNotNull { entry ->
            val seasons = dao.seasons(entry.show.id.value)
            if (seasons.isEmpty()) return@mapNotNull null
            val episodesBySeason = seasons.associate { it.id to dao.episodes(it.id.value) }
            val watched = watchedAmong(episodesBySeason.values.flatten())
            val offered = UpNextPolicy.candidate(seasons, episodesBySeason, watched, now) ?: return@mapNotNull null
            UpNextItem.NewEpisode(
                showId = entry.show.id,
                showTitle = entry.show.title,
                episode = offered.episode,
                seasonNumber = offered.season?.number,
                backdropUrl = entry.show.backdropUrl ?: entry.show.posterUrl,
                channelId = entry.channelId,
                sortAtEpochMs = UpNextPolicy.eventAt(offered.episode)?.toEpochMilli() ?: 0L,
            )
        }
        return UpNextPolicy.merge(inProgress, fresh, dao.dismissals(), limit)
    }

    /**
     * Background refresh of the pinned Programmes listed on [channelIds] (ROADMAP §M4), when
     * Home opens: each one whose Saisons cache is older than [TTL_MS] (or absent) refetches
     * its Saisons and every Saison's Épisodes from the network side of [providerFor] (one
     * crawl per Programme, ProgrammeMemo), [parallelism] Programmes at a time, one store tick
     * per Programme. A failure keeps the cache silently. Returns how many were refreshed.
     */
    suspend fun refreshPinned(
        channelIds: List<String>,
        providerFor: (ShowId) -> Provider,
        parallelism: Int = PINNED_PARALLELISM,
    ): Int {
        val stale = favoriteChannelShows(channelIds).map { it.show.id }
            .filter { id -> dao.programAt(id.value)?.let { clock() - it < TTL_MS } != true }
        if (stale.isEmpty()) return 0
        val gate = Semaphore(parallelism)
        return coroutineScope {
            stale.map { id -> async { gate.withPermit { refreshProgramme(providerFor(id).network(), id) } } }
                .awaitAll()
                .count { it }
        }
    }

    private suspend fun refreshProgramme(provider: Provider, showId: ShowId): Boolean = try {
        val seasons = provider.seasons(showId)
        val episodes = seasons.map { it to provider.episodes(it.id) }
        val at = clock()
        dao.putSeasons(showId.value, seasons, at)
        for ((season, listed) in episodes) dao.putEpisodes(season.id.value, listed, at)
        tick()
        true
    } catch (error: kotlinx.coroutines.CancellationException) {
        throw error
    } catch (_: Exception) {
        false
    }

    /**
     * Épisode suivant of [current] in Programme [showId] (NextEpisodePolicy): the next one in
     * its numbered Saison, else the first of the following numbered Saison. Saisons and
     * Épisodes are read cache-first, the following Saison only when needed, so the player
     * calls this while the Épisode plays and the panel never waits on the network. Null for
     * an unnumbered Saison, after the last Saison, or when nothing can be loaded.
     */
    suspend fun nextEpisode(provider: Provider, showId: ShowId, current: EpisodeId): SeasonEpisode? {
        val seasons = loadOrNull { seasons(provider, showId) } ?: return null
        val numbered = NextEpisodePolicy.numberedSeasons(seasons)
        if (numbered.isEmpty()) return null
        val cachedSeason = dao.episodesById(listOf(current.value)).firstOrNull()?.seasonId
        if (cachedSeason != null && seasons.any { it.id == cachedSeason } && numbered.none { it.id == cachedSeason }) {
            return null // An Épisode of an unnumbered Saison: no « next ».
        }
        var index = numbered.indexOfFirst { it.id == cachedSeason }
        var queue: List<Episode> = emptyList()
        if (index >= 0) {
            queue = NextEpisodePolicy.playOrder(loadOrNull { episodes(provider, numbered[index].id) }.orEmpty())
        } else {
            // Not cached yet: look for it Saison by Saison.
            for ((i, season) in numbered.withIndex()) {
                val listed = loadOrNull { episodes(provider, season.id) }.orEmpty()
                if (listed.any { it.id == current }) {
                    index = i
                    queue = NextEpisodePolicy.playOrder(listed)
                    break
                }
            }
        }
        if (index < 0 || queue.none { it.id == current }) return null
        NextEpisodePolicy.next(queue, current)?.let { return SeasonEpisode(it, numbered[index]) }
        for (season in numbered.drop(index + 1)) {
            val first = NextEpisodePolicy.playOrder(loadOrNull { episodes(provider, season.id) }.orEmpty()).firstOrNull()
            if (first != null) return SeasonEpisode(first, season)
        }
        return null
    }

    /**
     * What the Programme page needs beyond the selected Saison (ROADMAP §M4): the hero target
     * (ProgrammeHeroPolicy) and the Saisons whose Épisodes are all watched. With a viewing
     * history every numbered Saison is read (cache-first; one crawl per Programme), without
     * one only the first numbered Saison, or the first unnumbered one of a magazine.
     */
    suspend fun programmeView(
        provider: Provider,
        showId: ShowId,
        seasons: List<Season>,
        channelIds: List<String>,
    ): ProgrammeView {
        val resume = resumeFor(showId, channelIds)
        val history = dao.progressOfShow(showId.value).isNotEmpty()
        val numbered = NextEpisodePolicy.numberedSeasons(seasons)
        val toLoad = when {
            numbered.isEmpty() -> seasons.take(1)
            history -> numbered
            else -> numbered.take(1)
        }
        val episodesBySeason = LinkedHashMap<SeasonId, List<Episode>>()
        for (season in toLoad) loadOrNull { episodes(provider, season.id) }?.let { episodesBySeason[season.id] = it }
        val watched = watchedAmong(episodesBySeason.values.flatten())
        var target = ProgrammeHeroPolicy.target(resume, seasons, episodesBySeason, watched)
        if (target is HeroTarget.Resume && target.season == null) {
            // In a Saison not read here: the episodes cache still knows which one.
            val seasonId = dao.episodesById(listOf(target.item.episodeId.value)).firstOrNull()?.seasonId
            target = target.copy(season = seasons.firstOrNull { it.id == seasonId })
        }
        return ProgrammeView(target, ProgrammeHeroPolicy.watchedSeasons(seasons, episodesBySeason, watched))
    }

    private suspend fun watchedAmong(episodes: List<Episode>): Set<EpisodeId> =
        episodeProgress(episodes.map { it.id }).filterValues { it.watched }.keys

    private suspend fun <T> loadOrNull(load: suspend () -> T): T? = try {
        load()
    } catch (error: kotlinx.coroutines.CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    /**
     * « Marquer comme vu »: a Progression at 100 % (position = duration), so the
     * Épisode leaves Reprendre and reads « ✓ Vu ». An unknown [durationSeconds]
     * falls back to the saved row, then the cached Épisode. With no duration at
     * all, the row stores duration = position (at least
     * ResumePolicy.MIN_POSITION_SECONDS), which [episodeProgress] and
     * ResumePolicy already read as finished: no extra column. A null [context]
     * keeps the Programme context an earlier save recorded.
     */
    suspend fun markWatched(episodeId: EpisodeId, durationSeconds: Int?, context: ProgressContext? = null) {
        val previous = dao.progress(episodeId.value)
        val duration = (durationSeconds ?: previous?.durationSeconds ?: cachedDuration(episodeId))
            ?.takeIf { it > 0 }
        val position = duration?.toLong()
            ?: maxOf(previous?.positionSeconds ?: 0L, ResumePolicy.MIN_POSITION_SECONDS)
        dao.putProgress(
            ProgressEntity(
                episodeId = episodeId.value,
                positionSeconds = position,
                durationSeconds = duration ?: position.toInt(),
                updatedAt = clock(),
                showId = context?.showId?.value ?: previous?.showId,
                showTitle = context?.showTitle ?: previous?.showTitle,
                episodeTitle = context?.episodeTitle ?: previous?.episodeTitle,
                thumbnailUrl = context?.thumbnailUrl ?: previous?.thumbnailUrl,
            ),
        )
        tick()
    }

    /** « Marquer comme non vu »: forgets the Progression of this Épisode. */
    suspend fun markUnwatched(episodeId: EpisodeId) {
        dao.deleteProgress(episodeId.value)
        tick()
    }

    private suspend fun cachedDuration(episodeId: EpisodeId): Int? =
        dao.episodesById(listOf(episodeId.value)).firstOrNull()?.durationSeconds

    /**
     * Home Genre rails, cache only: every Genre in glossary order with its
     * Programmes across [channelIds], interleaved Chaîne by Chaîne (the
     * listings carry no broadcast date to sort on) and de-duplicated on the
     * normalized title. Empty Genres are dropped; each rail is capped at
     * [perRail] cards (the TF1 Index alone lists ~2900 Programmes) and says
     * whether the cap cut it ([GenreRail.hasMore]: the « Tout voir » card).
     */
    suspend fun genreRails(
        channelIds: List<String>,
        perRail: Int = GENRE_RAIL_LIMIT,
    ): List<GenreRail> {
        val listings = channelIds.map { channelId -> listing(channelId).map { ChannelShow(it, channelId) } }
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
            if (merged.isEmpty()) null else GenreRail(genre, merged.take(perRail), hasMore = merged.size > perRail)
        }
    }

    /**
     * The « Tout voir » grid of [genre], cache only (no crawl, ADR 0002): every Programme of
     * the Genre on [channelIds], arranged by [GenreGrid] (recency, then title). France TV
     * shows what its cached listings hold.
     */
    suspend fun genreGrid(genre: Genre, channelIds: List<String>): List<ChannelShow> {
        val all = channelIds.flatMap { channelId -> listing(channelId).map { ChannelShow(it, channelId) } }
        return GenreGrid.arrange(all, genre, channelIds = null, now = clock())
    }

    /**
     * Search with an empty query and an active filter: every Programme of [genre] (null: every
     * Genre) on [channelIds], cache only, in the « Tout voir » order (GenreGrid: recency, then
     * title), the same title on two Chaînes kept once.
     */
    suspend fun browse(channelIds: List<String>, genre: Genre?): List<ChannelShow> {
        val all = channelIds.flatMap { channelId -> listing(channelId).map { ChannelShow(it, channelId) } }
        return GenreGrid.arrange(all, genre, channelIds = null, now = clock())
    }

    /** Favorites with the Chaîne they are listed on, most recently pinned first. */
    suspend fun favoriteChannelShows(channelIds: List<String>): List<ChannelShow> {
        val ids = dao.favoriteIds()
        if (ids.isEmpty()) return emptyList()
        val byId = listedByIds(ids, channelIds)
        return ids.mapNotNull { byId[it] }
    }

    /**
     * Listed rows of a few Programmes, on the first of [channelIds] that lists each (the
     * order the full listings used to give), with canonical Genres.
     */
    private suspend fun listedByIds(ids: Collection<String>, channelIds: List<String>): Map<String, ChannelShow> {
        if (ids.isEmpty()) return emptyMap()
        val rank = channelIds.withIndex().associate { it.value to it.index }
        // associateBy keeps the last value of a key: sort the preferred Chaîne last.
        return dao.listedShows(ids.toSet())
            .filter { it.channelId in rank }
            .sortedByDescending { rank.getValue(it.channelId) }
            .associateBy({ it.show.id.value }) { ChannelShow(Genres.canonical(it.show), it.channelId) }
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

    /**
     * Search captions: the first Chaîne (in [channelIds] order) listing each
     * Programme. Each listing is read once; unlisted Programmes are absent.
     */
    suspend fun channelsOf(showIds: Collection<ShowId>, channelIds: List<String>): Map<ShowId, String> {
        val wanted = showIds.toHashSet()
        val found = LinkedHashMap<ShowId, String>()
        for (channelId in channelIds) {
            if (found.size == wanted.size) break
            for (show in listing(channelId)) {
                if (show.id in wanted) found.putIfAbsent(show.id, channelId)
            }
        }
        return found
    }

    /** The Programme as cached in the listings of [channelIds], with its Chaîne; null when unlisted. */
    suspend fun listedShow(showId: ShowId, channelIds: List<String>): ChannelShow? {
        for (channelId in channelIds) {
            listing(channelId).firstOrNull { it.id == showId }?.let { return ChannelShow(it, channelId) }
        }
        return null
    }

    /**
     * The Reprendre target of this Programme: its newest unfinished Épisode,
     * or null. Unlike the Home rail, a newer finished Épisode does not hide an
     * older unfinished one: on its own page every Progression counts. An
     * expired Épisode (episodes cache) is skipped, as on the Home rail.
     */
    suspend fun resumeFor(showId: ShowId, channelIds: List<String>): ResumeItem? {
        // Queried per Programme: a long history of other Programmes never hides it.
        val rows = dao.progressOfShow(showId.value)
        val ends = availabilityEnds(rows.map { it.episodeId })
        val now = now()
        val row = rows.firstOrNull {
            ResumePolicy.resumeAt(Progress(EpisodeId(it.episodeId), it.positionSeconds, it.updatedAt), it.durationSeconds) != null &&
                ends[it.episodeId]?.isBefore(now) != true
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
            availableUntil = ends[row.episodeId],
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
            for (show in listing(channelId)) {
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

        /** Cap of each Home Genre rail. */
        const val GENRE_RAIL_LIMIT = 40

        /** Progress rows scanned to fill the rail (several per Programme). */
        private const val RESUME_SCAN = 200

        /** Pinned Programmes refreshed at once when Home opens. */
        const val PINNED_PARALLELISM = 3

        /** Channels carrying France TV listings (SPEC §Provider France TV). */
        val FTV_CHANNEL_IDS = listOf("france-2", "france-3", "france-4", "france-5", "franceinfo")
    }
}

/** One Home Genre rail; [hasMore] when the cap left Programmes out (« Tout voir » card). */
data class GenreRail(val genre: Genre, val shows: List<ChannelShow>, val hasMore: Boolean)

/** The Programme page hero target and the Saisons to check (all their Épisodes watched). */
data class ProgrammeView(val target: HeroTarget?, val watchedSeasons: Set<SeasonId>)
