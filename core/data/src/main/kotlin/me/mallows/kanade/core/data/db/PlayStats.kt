package me.mallows.kanade.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** 曲ごとの再生回数と、最後に再生した日時 */
@Entity(tableName = "play_stats")
data class PlayStatsEntity(
  @PrimaryKey @ColumnInfo(name = "media_store_id") val mediaStoreId: Long,
  @ColumnInfo(name = "play_count") val playCount: Int,
  /** エポックミリ秒 */
  @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long,
)

@Dao
interface PlayStatsDao {

  /** 1回再生したことを記録する */
  @Query(
    """
    INSERT INTO play_stats (media_store_id, play_count, last_played_at) VALUES (:mediaStoreId, 1, :now)
    ON CONFLICT(media_store_id) DO UPDATE SET play_count = play_count + 1, last_played_at = :now
    """
  )
  suspend fun recordPlay(mediaStoreId: Long, now: Long)

  @Query(
    """
    SELECT track.* FROM track JOIN play_stats ON play_stats.media_store_id = track.media_store_id
    WHERE track.excluded = 0
    ORDER BY play_stats.last_played_at DESC LIMIT :limit
    """
  )
  fun observeRecentlyPlayed(limit: Int): Flow<List<TrackEntity>>

  @Query(
    """
    SELECT track.* FROM track JOIN play_stats ON play_stats.media_store_id = track.media_store_id
    WHERE track.excluded = 0
    ORDER BY play_stats.play_count DESC, play_stats.last_played_at DESC LIMIT :limit
    """
  )
  fun observeMostPlayed(limit: Int): Flow<List<TrackEntity>>
}
