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
        UpNextDismissalEntity::class,
    ],
    version = 7,
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

        /**
         * v4 → v5: freshness instants of each Épisode (broadcast, publication, end of
         * availability; epoch millis, nullable). The Seasons/Épisodes freshness markers are
         * cleared so cached Épisodes refetch with them; the rows stay served meanwhile.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE episodes ADD COLUMN broadcastAt INTEGER")
                db.execSQL("ALTER TABLE episodes ADD COLUMN publishedAt INTEGER")
                db.execSQL("ALTER TABLE episodes ADD COLUMN availableUntil INTEGER")
                db.execSQL("DELETE FROM programs")
            }
        }

        /**
         * v5 → v6: Épisode number (play order of a numbered Saison) and the « À suivre »
         * dismissals. The Seasons/Épisodes freshness markers are cleared so cached Épisodes
         * refetch with their number; the rows stay served meanwhile.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE episodes ADD COLUMN number INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `up_next_dismissals` " +
                        "(`showId` TEXT NOT NULL, `episodeId` TEXT NOT NULL, PRIMARY KEY(`showId`))",
                )
                db.execSQL("DELETE FROM programs")
            }
        }

        /**
         * v6 → v7: recency of each Programme (Show.recencyAt, the « Tout voir » order).
         * Listings are marked stale so the next startup refetches them with it; the shows
         * stay served meanwhile and keep their first-seen order.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shows ADD COLUMN recencyAt INTEGER")
                db.execSQL("DELETE FROM listings")
            }
        }
    }
}
