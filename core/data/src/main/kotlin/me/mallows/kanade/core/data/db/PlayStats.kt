package me.mallows.kanade.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
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

  /**
   * 1回再生したことを記録する。
   * 「INSERT ... ON CONFLICT DO UPDATE」(upsert) はSQLite 3.24以降 (Android 11以降) でしか動かないので、
   * 更新を試し、なければ (初めての再生なら) 足す、の2段にして、1つの処理 (トランザクション) にまとめる
   */
  @Transaction
  suspend fun recordPlay(mediaStoreId: Long, now: Long) {
    if (incrementPlay(mediaStoreId, now) == 0) insertFirstPlay(PlayStatsEntity(mediaStoreId, 1, now))
  }

  @Query("UPDATE play_stats SET play_count = play_count + 1, last_played_at = :now WHERE media_store_id = :mediaStoreId")
  suspend fun incrementPlay(mediaStoreId: Long, now: Long): Int

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertFirstPlay(stats: PlayStatsEntity)

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
