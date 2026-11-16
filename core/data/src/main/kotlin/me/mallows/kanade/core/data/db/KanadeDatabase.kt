package me.mallows.kanade.core.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
  entities = [
    TrackEntity::class,
    QueueItemEntity::class,
    PlaybackStateEntity::class,
  ],
  version = 3,
  exportSchema = true,
  autoMigrations = [
    AutoMigration(from = 1, to = 2),
    AutoMigration(from = 2, to = 3),
  ],
)
abstract class KanadeDatabase : RoomDatabase() {
  abstract fun trackDao(): TrackDao
  abstract fun playbackStateDao(): PlaybackStateDao
}
