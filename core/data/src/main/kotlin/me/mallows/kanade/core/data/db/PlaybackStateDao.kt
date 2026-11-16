package me.mallows.kanade.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

/** 再生キューの1曲 (並び順つき) */
@Entity(tableName = "playback_queue")
data class QueueItemEntity(
  @PrimaryKey val position: Int,
  @ColumnInfo(name = "media_store_id") val mediaStoreId: Long,
)

/** 再生キュー以外の再生状態。1行だけ持つ */
@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
  @PrimaryKey val id: Int = 1,
  @ColumnInfo(name = "current_index") val currentIndex: Int,
  @ColumnInfo(name = "position_ms") val positionMs: Long,
  val shuffle: Boolean,
  @ColumnInfo(name = "repeat_mode") val repeatMode: Int,
)

@Dao
interface PlaybackStateDao {

  @Query("SELECT media_store_id FROM playback_queue ORDER BY position")
  suspend fun queue(): List<Long>

  @Query("SELECT * FROM playback_state WHERE id = 1")
  suspend fun state(): PlaybackStateEntity?

  @Query("DELETE FROM playback_queue")
  suspend fun clearQueue()

  @Insert
  suspend fun insertQueue(items: List<QueueItemEntity>)

  @Upsert
  suspend fun upsertState(state: PlaybackStateEntity)

  /** キューごと保存する */
  @Transaction
  suspend fun saveAll(mediaStoreIds: List<Long>, state: PlaybackStateEntity) {
    clearQueue()
    insertQueue(mediaStoreIds.mapIndexed { i, id -> QueueItemEntity(i, id) })
    upsertState(state)
  }
}
