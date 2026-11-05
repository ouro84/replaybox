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

    override suspend fun putListing(channelId: String, shows: List<Show>, at: Long) {
        listings[channelId] = at
        this.shows[channelId] = shows.toMutableList()
        genres[channelId] = shows.flatMap { it.genres }.toMutableSet()
    }

    override suspend fun shows(channelId: String): List<Show> = shows[channelId].orEmpty()

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

    override suspend fun putProgress(episodeId: String, positionSeconds: Long, durationSeconds: Int?, at: Long) {
        progress[episodeId] = ProgressEntity(episodeId, positionSeconds, durationSeconds, at)
    }

    override suspend fun progress(episodeId: String): ProgressEntity? = progress[episodeId]

    override suspend fun putFavorite(showId: String, at: Long) {
        favorites[showId] = at
    }

    override suspend fun removeFavorite(showId: String) {
        favorites.remove(showId)
    }

    override suspend fun favoriteIds(): List<String> = favorites.keys.toList()
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
        return showsResult.filter { it.title.contains(query, ignoreCase = true) }
    }

    override suspend fun stream(episode: EpisodeId): Stream {
        streamCalls += 1
        return Stream("https://cdn/test.mpd", com.replaybox.core.model.StreamType.DASH)
    }
}

fun show(id: String, title: String, vararg genres: Genre): Show =
    Show(ShowId(id), title, null, null, genres.toSet())

fun episode(id: String, seasonId: String): Episode =
    Episode(EpisodeId(id), SeasonId(seasonId), id, null, null, null, null)

fun <T> blocking(body: suspend () -> T): T = runBlocking { body() }
