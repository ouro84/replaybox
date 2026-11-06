package com.replaybox.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

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

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val showId: String,
    val addedAt: Long,
)
