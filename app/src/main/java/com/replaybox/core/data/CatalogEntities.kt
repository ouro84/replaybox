package com.replaybox.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.replaybox.core.model.Episode
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.SeasonId
import java.time.Instant

/**
 * Room cache of the Chaînes' catalogues (ADR 0002, SPEC §Cache). TTL 12 h,
 * refreshed at startup with the Home rows first. Progress and Favorites are
 * device-local and never synchronized (glossary).
 */
@Entity(tableName = "shows")
data class ShowEntity(
    @PrimaryKey val id: String,
    val channelId: String,
    val title: String,
    val posterUrl: String?,
    val synopsis: String?,
    val backdropUrl: String? = null,
    /**
     * When this Programme first appeared in its Chaîne listing, and its rank
     * in that listing (schema v3). Listings are re-ordered editorially on
     * every fetch; keeping the first-seen order holds the Home rails still
     * from one evening to the next, newest arrivals first.
     */
    @ColumnInfo(defaultValue = "0") val firstSeenAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val firstSeenRank: Int = 0,
    /** Show.recentEpisodeCount (schema v4), the search tie-breaker. */
    @ColumnInfo(defaultValue = "0") val recentEpisodeCount: Int = 0,
    /**
     * Show.recencyAt (schema v7, epoch ms), the « Tout voir » order: the latest value any
     * refresh gave (GenreGrid.mergeRecency), so a sign seen once is not lost when the next
     * listing omits it.
     */
    val recencyAt: Long? = null,
)

@Entity(tableName = "genre_map", primaryKeys = ["showId", "genre"])
data class GenreMapEntity(
    val showId: String,
    val genre: String,
)

/** Freshness marker of a Chaîne listing (TTL anchor for the Home rows). */
@Entity(tableName = "listings")
data class ListingEntity(
    @PrimaryKey val channelId: String,
    val fetchedAt: Long,
)

@Entity(tableName = "seasons")
data class SeasonEntity(
    @PrimaryKey val id: String,
    val showId: String,
    val number: Int?,
    val title: String,
)

@Entity(tableName = "episodes")
data class EpisodeEntity(
    @PrimaryKey val id: String,
    val seasonId: String?,
    val title: String,
    val synopsis: String?,
    val broadcastDate: String?,
    val durationSeconds: Int?,
    val thumbnailUrl: String?,
    /** Episode.broadcastAt / publishedAt / availableUntil in epoch millis (schema v5). */
    val broadcastAt: Long? = null,
    val publishedAt: Long? = null,
    val availableUntil: Long? = null,
    /** Episode.number (schema v6). */
    val number: Int? = null,
)

fun Episode.toEntity(): EpisodeEntity = EpisodeEntity(
    id = id.value,
    seasonId = seasonId?.value,
    title = title,
    synopsis = synopsis,
    broadcastDate = broadcastDate,
    durationSeconds = durationSeconds,
    thumbnailUrl = thumbnailUrl,
    broadcastAt = broadcastAt?.toEpochMilli(),
    publishedAt = publishedAt?.toEpochMilli(),
    availableUntil = availableUntil?.toEpochMilli(),
    number = number,
)

fun EpisodeEntity.toEpisode(): Episode = Episode(
    id = EpisodeId(id),
    seasonId = seasonId?.let(::SeasonId),
    title = title,
    synopsis = synopsis,
    broadcastDate = broadcastDate,
    durationSeconds = durationSeconds,
    thumbnailUrl = thumbnailUrl,
    broadcastAt = broadcastAt?.let(Instant::ofEpochMilli),
    publishedAt = publishedAt?.let(Instant::ofEpochMilli),
    availableUntil = availableUntil?.let(Instant::ofEpochMilli),
    number = number,
)

/**
 * Freshness marker per cached key — a ShowId for Seasons, a SeasonId for
 * Episodes (Seasons and Episodes resolve together per program page).
 */
@Entity(tableName = "programs")
data class ProgramMetaEntity(
    @PrimaryKey val key: String,
    val fetchedAt: Long,
)

/**
 * Progression (glossary): device-local playhead, never leaves the box. The
 * Programme context (schema v2) feeds the Home "Reprendre" rail; rows saved
 * before v2 have none and stay out of that rail.
 */
@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val episodeId: String,
    val positionSeconds: Long,
    val durationSeconds: Int?,
    val updatedAt: Long,
    val showId: String? = null,
    val showTitle: String? = null,
    val episodeTitle: String? = null,
    val thumbnailUrl: String? = null,
)

/**
 * « Retirer d'À suivre » (schema v6): the Épisode the viewer took off the Home rail for this
 * Programme. The Programme comes back once its candidate Épisode is another one (UpNextPolicy).
 */
@Entity(tableName = "up_next_dismissals")
data class UpNextDismissalEntity(
    @PrimaryKey val showId: String,
    val episodeId: String,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val showId: String,
    val addedAt: Long,
)
