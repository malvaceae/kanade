package me.mallows.kanade.core.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
  entities = [
    TrackEntity::class,
    QueueItemEntity::class,
    PlaybackStateEntity::class,
    PlaylistEntity::class,
    PlaylistEntryEntity::class,
    EqPresetEntity::class,
    PlayStatsEntity::class,
  ],
  version = 7,
  exportSchema = true,
  autoMigrations = [
    AutoMigration(from = 1, to = 2),
    AutoMigration(from = 2, to = 3),
    AutoMigration(from = 3, to = 4),
    AutoMigration(from = 4, to = 5),
    AutoMigration(from = 5, to = 6),
    AutoMigration(from = 6, to = 7),
  ],
)
abstract class KanadeDatabase : RoomDatabase() {
  abstract fun trackDao(): TrackDao
  abstract fun playbackStateDao(): PlaybackStateDao
  abstract fun playlistDao(): PlaylistDao
  abstract fun eqPresetDao(): EqPresetDao
  abstract fun playStatsDao(): PlayStatsDao
}
