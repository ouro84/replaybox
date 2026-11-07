package com.replaybox.core.data

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
import kotlinx.coroutines.runBlocking

/**
 * In-memory CatalogDao for JVM tests of the cache-first logic — no Android,
 * no network (AGENTS.md).
 */
class FakeCatalogDao : CatalogDao {

    val listings = HashMap<String, Long>()
    val shows = LinkedHashMap<String, MutableList<Show>>()
    val genres = LinkedHashMap<String, MutableSet<Genre>>()
    val programs = HashMap<String, Long>()
    val seasons = LinkedHashMap<String, MutableList<Season>>()
    val episodes = LinkedHashMap<String, MutableList<Episode>>()
    val progress = HashMap<String, ProgressEntity>()
    val favorites = LinkedHashMap<String, Long>()

    override suspend fun listingAt(channelId: String): Long? = listings[channelId]

    /** id -> (firstSeenAt, firstSeenRank), mirroring RoomCatalogDao. */
    private val firstSeen = HashMap<String, Pair<Long, Int>>()

    override suspend fun putListing(channelId: String, shows: List<Show>, at: Long) {
        listings[channelId] = at
        shows.forEachIndexed { index, show -> firstSeen.putIfAbsent(show.id.value, at to index) }
        // Recency survives a refresh only while the Programme stays listed, as in RoomCatalogDao.
        val stored = this.shows[channelId].orEmpty().associate { it.id.value to it.recencyAt }
        this.shows[channelId] = shows.map {
            it.copy(recencyAt = GenreGrid.mergeRecency(stored[it.id.value], it.recencyAt))
        }.toMutableList()
        genres[channelId] = shows.flatMap { it.genres }.toMutableSet()
    }

    override suspend fun shows(channelId: String): List<Show> =
        shows[channelId].orEmpty().map { it.copy(firstSeenAt = firstSeen[it.id.value]?.first ?: 0) }.sortedWith(
            compareByDescending<Show> { (firstSeen[it.id.value]?.first ?: 0) / 86_400_000 }
                .thenBy { firstSeen[it.id.value]?.first ?: 0 }
                .thenBy { firstSeen[it.id.value]?.second ?: 0 },
        )

    override suspend fun programAt(key: String): Long? = programs[key]

    override suspend fun putSeasons(showId: String, seasons: List<Season>, at: Long) {
        this.seasons[showId] = seasons.toMutableList()
        programs[showId] = at
    }

    override suspend fun seasons(showId: String): List<Season> = seasons[showId].orEmpty()

    override suspend fun putEpisodes(seasonId: String, episodes: List<Episode>, at: Long) {
        this.episodes[seasonId] = episodes.toMutableList()
        programs[seasonId] = at
    }

    override suspend fun episodes(seasonId: String): List<Episode> = episodes[seasonId].orEmpty()

    override suspend fun episodesById(episodeIds: List<String>): List<Episode> {
        val wanted = episodeIds.toSet()
        return episodes.values.flatten().filter { it.id.value in wanted }
    }

    override suspend fun putProgress(progress: ProgressEntity) {
        this.progress[progress.episodeId] = progress
    }

    override suspend fun progress(episodeId: String): ProgressEntity? = progress[episodeId]

    override suspend fun recentProgress(limit: Int): List<ProgressEntity> =
        progress.values.sortedByDescending { it.updatedAt }.take(limit)

    override suspend fun deleteProgress(episodeId: String) {
        progress.remove(episodeId)
    }

    override suspend fun progressOfShow(showId: String): List<ProgressEntity> =
        progress.values.filter { it.showId == showId }.sortedByDescending { it.updatedAt }

    override suspend fun progressFor(episodeIds: List<String>): List<ProgressEntity> =
        episodeIds.mapNotNull { progress[it] }

    override suspend fun putFavorite(showId: String, at: Long) {
        favorites[showId] = at
    }

    override suspend fun removeFavorite(showId: String) {
        favorites.remove(showId)
    }

    override suspend fun favoriteIds(): List<String> = favorites.keys.toList()

    val dismissed = LinkedHashMap<String, String>()

    override suspend fun putDismissal(showId: String, episodeId: String) {
        dismissed[showId] = episodeId
    }

    override suspend fun dismissals(): Map<String, String> = HashMap(dismissed)
}

/** Deterministic Provider double recording its calls. */
class FakeProvider(
    override val channel: Channel = Channel("test", "Test"),
    private val showsResult: List<Show> = emptyList(),
    private val showsError: Exception? = null,
    private val seasonsResult: List<Season> = emptyList(),
    private val seasonsError: Exception? = null,
    private val episodesResult: List<Episode> = emptyList(),
    private val episodesError: Exception? = null,
    private val searchError: Exception? = null,
    private val streamError: Exception? = null,
) : Provider {

    var showsCalls = 0
    var seasonsCalls = 0
    var episodesCalls = 0
    var streamCalls = 0
    var searchCalls = 0

    override suspend fun shows(genre: Genre?): List<Show> {
        showsCalls += 1
        showsError?.let { throw it }
        return if (genre == null) showsResult else showsResult.filter { genre in it.genres }
    }

    override suspend fun seasons(show: ShowId): List<Season> {
        seasonsCalls += 1
        seasonsError?.let { throw it }
        return seasonsResult
    }

    override suspend fun episodes(season: SeasonId?): List<Episode> {
        episodesCalls += 1
        episodesError?.let { throw it }
        return episodesResult
    }

    override suspend fun search(query: String): List<Show> {
        searchCalls += 1
        searchError?.let { throw it }
        return showsResult.filter { it.title.contains(query, ignoreCase = true) }
    }

    override suspend fun stream(episode: EpisodeId): Stream {
        streamCalls += 1
        streamError?.let { throw it }
        return Stream("https://cdn/test.mpd", com.replaybox.core.model.StreamType.DASH)
    }
}

fun show(id: String, title: String, vararg genres: Genre): Show =
    Show(ShowId(id), title, null, null, genres.toSet())

fun episode(id: String, seasonId: String): Episode =
    Episode(EpisodeId(id), SeasonId(seasonId), id, null, null, null, null)

fun <T> blocking(body: suspend () -> T): T = runBlocking { body() }
