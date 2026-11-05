package com.replaybox.core.data

import com.replaybox.core.model.Episode
import com.replaybox.core.model.Genre
import com.replaybox.core.model.Season
import com.replaybox.core.model.Show

/**
 * Storage seam of the catalog cache. Room implements it in the app
 * (RoomCatalogDao); unit tests use an in-memory fake — no Android, no
 * network, per AGENTS.md.
 */
interface CatalogDao {

    // ---- Listings (Home rows) ----

    suspend fun listingAt(channelId: String): Long?

    suspend fun putListing(channelId: String, shows: List<Show>, at: Long)

    suspend fun shows(channelId: String): List<Show>

    // ---- Program pages (Seasons + Episodes) ----

    suspend fun programAt(key: String): Long?

    suspend fun putSeasons(showId: String, seasons: List<Season>, at: Long)

    suspend fun seasons(showId: String): List<Season>

    suspend fun putEpisodes(seasonId: String, episodes: List<Episode>, at: Long)

    suspend fun episodes(seasonId: String): List<Episode>

    // ---- Progress + Favorites (device-local) ----

    suspend fun putProgress(episodeId: String, positionSeconds: Long, durationSeconds: Int?, at: Long)

    suspend fun progress(episodeId: String): ProgressEntity?

    suspend fun putFavorite(showId: String, at: Long)

    suspend fun removeFavorite(showId: String)

    suspend fun favoriteIds(): List<String>
}
