package com.replaybox.core.data

import androidx.room.Database
import androidx.room.RoomDatabase

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
    version = 1,
    exportSchema = false,
)
abstract class ReplayboxDatabase : RoomDatabase() {
    abstract fun rawDao(): RoomRawDao
}
