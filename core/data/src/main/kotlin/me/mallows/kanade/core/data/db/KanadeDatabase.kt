package me.mallows.kanade.core.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
  entities = [TrackEntity::class],
  version = 2,
  exportSchema = true,
  autoMigrations = [
    AutoMigration(from = 1, to = 2),
  ],
)
abstract class KanadeDatabase : RoomDatabase() {
  abstract fun trackDao(): TrackDao
}
