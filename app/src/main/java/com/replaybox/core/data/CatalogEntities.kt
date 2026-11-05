package com.replaybox.core.data

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

/** Progression (glossary): device-local playhead, never leaves the box. */
@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val episodeId: String,
    val positionSeconds: Long,
    val durationSeconds: Int?,
    val updatedAt: Long,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val showId: String,
    val addedAt: Long,
)
