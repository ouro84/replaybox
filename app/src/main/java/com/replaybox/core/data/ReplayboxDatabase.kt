package com.replaybox.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ShowEntity::class,
        GenreMapEntity::class,
        ListingEntity::class,
        SeasonEntity::class,
        EpisodeEntity::class,
        ProgramMetaEntity::class,
        ProgressEntity::class,
        FavoriteEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class ReplayboxDatabase : RoomDatabase() {
    abstract fun rawDao(): RoomRawDao

    companion object {
        /**
         * v1 → v2: Home backdrop art on shows, Programme context on progress,
         * listings marked stale so they refetch with their backdrops.
         * Additive only, so Progression and Favoris survive the update — a
         * destructive fallback would wipe the household's evening state.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shows ADD COLUMN backdropUrl TEXT")
                db.execSQL("ALTER TABLE progress ADD COLUMN showId TEXT")
                db.execSQL("ALTER TABLE progress ADD COLUMN showTitle TEXT")
                db.execSQL("ALTER TABLE progress ADD COLUMN episodeTitle TEXT")
                db.execSQL("ALTER TABLE progress ADD COLUMN thumbnailUrl TEXT")
                // Cached listings predate backdropUrl: drop their freshness so
                // the next startup refetches them (the shows stay served meanwhile).
                db.execSQL("DELETE FROM listings")
            }
        }

        /**
         * v2 → v3: first-seen order of each Programme, so the Home rails stop
         * reshuffling with the Chaînes' editorial order. Existing rows keep
         * their current listing order as their first-seen rank.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shows ADD COLUMN firstSeenAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE shows ADD COLUMN firstSeenRank INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE shows SET firstSeenRank = rowid")
            }
        }

        /**
         * v3 → v4: recent Épisode count of each Programme (search ranking of
         * the Index). Listings are marked stale so the next startup refetches
         * them with the count; the shows stay served meanwhile.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shows ADD COLUMN recentEpisodeCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("DELETE FROM listings")
            }
        }
    }
}
