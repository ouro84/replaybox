package com.replaybox.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.withTransaction
import com.replaybox.core.model.Episode
import com.replaybox.core.model.Genre
import com.replaybox.core.model.Season
import com.replaybox.core.model.Show

/** Generated Room layer — wrapped by [RoomCatalogDao]. */
@Dao
interface RoomRawDao {

    @Query("SELECT fetchedAt FROM listings WHERE channelId = :channelId")
    suspend fun listingAt(channelId: String): Long?

    @Query("DELETE FROM shows WHERE channelId = :channelId")
    suspend fun clearShows(channelId: String)

    @Query("DELETE FROM genre_map WHERE showId IN (SELECT id FROM shows WHERE channelId = :channelId)")
    suspend fun clearGenres(channelId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShows(shows: List<ShowEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGenres(genres: List<GenreMapEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertListing(listing: ListingEntity)

    @Query(
        "SELECT * FROM shows WHERE channelId = :channelId ORDER BY rowid",
    )
    suspend fun shows(channelId: String): List<ShowEntity>

    @Query(
        "SELECT showId, genre FROM genre_map " +
            "WHERE showId IN (SELECT id FROM shows WHERE channelId = :channelId)",
    )
    suspend fun genres(channelId: String): List<GenreMapEntity>

    @Query("SELECT fetchedAt FROM programs WHERE `key` = :key")
    suspend fun programAt(key: String): Long?

    @Query("DELETE FROM seasons WHERE showId = :showId")
    suspend fun clearSeasons(showId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeasons(seasons: List<SeasonEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgramMeta(meta: ProgramMetaEntity)

    @Query("SELECT * FROM seasons WHERE showId = :showId ORDER BY rowid")
    suspend fun seasons(showId: String): List<SeasonEntity>

    @Query("DELETE FROM episodes WHERE seasonId = :seasonId")
    suspend fun clearEpisodes(seasonId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<EpisodeEntity>)

    @Query("SELECT * FROM episodes WHERE seasonId = :seasonId ORDER BY rowid")
    suspend fun episodes(seasonId: String): List<EpisodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: ProgressEntity)

    @Query("SELECT * FROM progress WHERE episodeId = :episodeId")
    suspend fun progress(episodeId: String): ProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE showId = :showId")
    suspend fun deleteFavorite(showId: String)

    @Query("SELECT showId FROM favorites ORDER BY addedAt DESC")
    suspend fun favoriteIds(): List<String>
}

/** Room implementation of the [CatalogDao] seam. */
class RoomCatalogDao(
    private val db: ReplayboxDatabase,
    private val raw: RoomRawDao,
) : CatalogDao {

    override suspend fun putListing(channelId: String, shows: List<Show>, at: Long) = db.withTransaction {
        raw.clearShows(channelId)
        raw.clearGenres(channelId)
        raw.insertShows(
            shows.map {
                ShowEntity(it.id.value, channelId, it.title, it.posterUrl, it.synopsis)
            },
        )
        raw.insertGenres(
            shows.flatMap { show ->
                show.genres.map { genre -> GenreMapEntity(show.id.value, genre.name) }
            },
        )
        raw.insertListing(ListingEntity(channelId, at))
    }

    override suspend fun listingAt(channelId: String): Long? = raw.listingAt(channelId)

    override suspend fun shows(channelId: String): List<Show> {
        val entities = raw.shows(channelId)
        if (entities.isEmpty()) return emptyList()
        val genres = raw.genres(channelId).groupBy({ it.showId }) { it.genre }
        return entities.map {
            Show(
                id = com.replaybox.core.model.ShowId(it.id),
                title = it.title,
                posterUrl = it.posterUrl,
                synopsis = it.synopsis,
                genres = genres[it.id].orEmpty().mapNotNull { g -> runCatching { Genre.valueOf(g) }.getOrNull() }.toSet(),
            )
        }
    }

    override suspend fun programAt(key: String): Long? = raw.programAt(key)

    override suspend fun putSeasons(showId: String, seasons: List<Season>, at: Long) = db.withTransaction {
        raw.clearSeasons(showId)
        raw.insertSeasons(
            seasons.map {
                SeasonEntity(it.id.value, it.showId.value, it.number, it.title)
            },
        )
        raw.insertProgramMeta(ProgramMetaEntity(showId, at))
    }

    override suspend fun seasons(showId: String): List<Season> =
        raw.seasons(showId).map {
            Season(
                id = com.replaybox.core.model.SeasonId(it.id),
                showId = com.replaybox.core.model.ShowId(it.showId),
                number = it.number,
                title = it.title,
            )
        }

    override suspend fun putEpisodes(seasonId: String, episodes: List<Episode>, at: Long) = db.withTransaction {
        raw.clearEpisodes(seasonId)
        raw.insertEpisodes(
            episodes.map {
                EpisodeEntity(
                    it.id.value, it.seasonId?.value, it.title, it.synopsis,
                    it.broadcastDate, it.durationSeconds, it.thumbnailUrl,
                )
            },
        )
        raw.insertProgramMeta(ProgramMetaEntity(seasonId, at))
    }

    override suspend fun episodes(seasonId: String): List<Episode> =
        raw.episodes(seasonId).map {
            Episode(
                id = com.replaybox.core.model.EpisodeId(it.id),
                seasonId = it.seasonId?.let { s -> com.replaybox.core.model.SeasonId(s) },
                title = it.title,
                synopsis = it.synopsis,
                broadcastDate = it.broadcastDate,
                durationSeconds = it.durationSeconds,
                thumbnailUrl = it.thumbnailUrl,
            )
        }

    override suspend fun putProgress(episodeId: String, positionSeconds: Long, durationSeconds: Int?, at: Long) {
        raw.insertProgress(ProgressEntity(episodeId, positionSeconds, durationSeconds, at))
    }

    override suspend fun progress(episodeId: String): ProgressEntity? = raw.progress(episodeId)

    override suspend fun putFavorite(showId: String, at: Long) {
        raw.insertFavorite(FavoriteEntity(showId, at))
    }

    override suspend fun removeFavorite(showId: String) = raw.deleteFavorite(showId)

    override suspend fun favoriteIds(): List<String> = raw.favoriteIds()
}
