package com.replaybox.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.withTransaction
import com.replaybox.core.model.ChannelShow
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
        "SELECT * FROM shows WHERE channelId = :channelId " +
            "ORDER BY firstSeenAt / 86400000 DESC, firstSeenAt ASC, firstSeenRank ASC",
    )
    suspend fun shows(channelId: String): List<ShowEntity>

    @Query(
        "SELECT showId, genre FROM genre_map " +
            "WHERE showId IN (SELECT id FROM shows WHERE channelId = :channelId)",
    )
    suspend fun genres(channelId: String): List<GenreMapEntity>

    @Query("SELECT * FROM shows WHERE id IN (:ids)")
    suspend fun showsByIds(ids: List<String>): List<ShowEntity>

    @Query("SELECT showId, genre FROM genre_map WHERE showId IN (:ids)")
    suspend fun genresByIds(ids: List<String>): List<GenreMapEntity>

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

    @Query("SELECT * FROM episodes WHERE id IN (:episodeIds)")
    suspend fun episodesById(episodeIds: List<String>): List<EpisodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: ProgressEntity)

    @Query("SELECT * FROM progress WHERE episodeId = :episodeId")
    suspend fun progress(episodeId: String): ProgressEntity?

    @Query("SELECT * FROM progress ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun recentProgress(limit: Int): List<ProgressEntity>

    @Query("DELETE FROM progress WHERE episodeId = :episodeId")
    suspend fun deleteProgress(episodeId: String)

    @Query("SELECT * FROM progress WHERE showId = :showId ORDER BY updatedAt DESC")
    suspend fun progressOfShow(showId: String): List<ProgressEntity>

    @Query("SELECT * FROM progress WHERE episodeId IN (:episodeIds)")
    suspend fun progressFor(episodeIds: List<String>): List<ProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE showId = :showId")
    suspend fun deleteFavorite(showId: String)

    @Query("SELECT showId FROM favorites ORDER BY addedAt DESC")
    suspend fun favoriteIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDismissal(dismissal: UpNextDismissalEntity)

    @Query("SELECT * FROM up_next_dismissals")
    suspend fun dismissals(): List<UpNextDismissalEntity>
}

/** Room implementation of the [CatalogDao] seam. */
class RoomCatalogDao(
    private val db: ReplayboxDatabase,
    private val raw: RoomRawDao,
) : CatalogDao {

    override suspend fun putListing(channelId: String, shows: List<Show>, at: Long) = db.withTransaction {
        val seen = raw.shows(channelId).associateBy { it.id }
        // Genres first: clearGenres finds its rows through the shows table, so after
        // clearShows it matched nothing and a Genre a Programme lost was never removed.
        raw.clearGenres(channelId)
        raw.clearShows(channelId)
        raw.insertShows(
            shows.mapIndexed { index, show ->
                val known = seen[show.id.value]
                ShowEntity(
                    show.id.value, channelId, show.title, show.posterUrl, show.synopsis, show.backdropUrl,
                    firstSeenAt = known?.firstSeenAt ?: at,
                    firstSeenRank = known?.firstSeenRank ?: index,
                    recentEpisodeCount = show.recentEpisodeCount,
                    recencyAt = GenreGrid.mergeRecency(known?.recencyAt, show.recencyAt),
                )
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
        return entities.map { it.toShow(genres[it.id].orEmpty()) }
    }

    /**
     * A few Programmes by id (« À suivre », Favoris): two indexed queries instead of
     * reading every listing, which took over a second on the Bbox at launch.
     */
    override suspend fun listedShows(ids: Collection<String>): List<ChannelShow> {
        if (ids.isEmpty()) return emptyList()
        val wanted = ids.toList()
        val entities = wanted.chunked(500).flatMap { raw.showsByIds(it) }
        val genres = wanted.chunked(500).flatMap { raw.genresByIds(it) }.groupBy({ it.showId }) { it.genre }
        return entities.map { ChannelShow(it.toShow(genres[it.id].orEmpty()), it.channelId) }
    }

    private fun ShowEntity.toShow(genres: List<String>) = Show(
        id = com.replaybox.core.model.ShowId(id),
        title = title,
        posterUrl = posterUrl,
        synopsis = synopsis,
        backdropUrl = backdropUrl,
        recentEpisodeCount = recentEpisodeCount,
        firstSeenAt = firstSeenAt,
        recencyAt = recencyAt,
        genres = genres.mapNotNull { g -> runCatching { Genre.valueOf(g) }.getOrNull() }.toSet(),
    )

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
        raw.insertEpisodes(episodes.map { it.toEntity() })
        raw.insertProgramMeta(ProgramMetaEntity(seasonId, at))
    }

    override suspend fun episodes(seasonId: String): List<Episode> =
        raw.episodes(seasonId).map { it.toEpisode() }

    override suspend fun episodesById(episodeIds: List<String>): List<Episode> =
        // SQLite caps bound parameters (999 on old versions): query in chunks.
        episodeIds.chunked(500).flatMap { ids -> raw.episodesById(ids).map { it.toEpisode() } }

    override suspend fun putProgress(progress: ProgressEntity) = raw.insertProgress(progress)

    override suspend fun progress(episodeId: String): ProgressEntity? = raw.progress(episodeId)

    override suspend fun recentProgress(limit: Int): List<ProgressEntity> = raw.recentProgress(limit)

    override suspend fun deleteProgress(episodeId: String) = raw.deleteProgress(episodeId)

    override suspend fun progressOfShow(showId: String): List<ProgressEntity> = raw.progressOfShow(showId)

    override suspend fun progressFor(episodeIds: List<String>): List<ProgressEntity> =
        // SQLite caps bound parameters (999 on old versions): query in chunks.
        episodeIds.chunked(500).flatMap { raw.progressFor(it) }

    override suspend fun putFavorite(showId: String, at: Long) {
        raw.insertFavorite(FavoriteEntity(showId, at))
    }

    override suspend fun removeFavorite(showId: String) = raw.deleteFavorite(showId)

    override suspend fun favoriteIds(): List<String> = raw.favoriteIds()

    override suspend fun putDismissal(showId: String, episodeId: String) =
        raw.insertDismissal(UpNextDismissalEntity(showId, episodeId))

    override suspend fun dismissals(): Map<String, String> =
        raw.dismissals().associate { it.showId to it.episodeId }
}
