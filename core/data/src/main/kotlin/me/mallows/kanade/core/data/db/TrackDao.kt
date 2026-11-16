package me.mallows.kanade.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** 曲のIDとMediaStoreのIDの組 */
data class TrackIdPair(val id: Long, val mediaStoreId: Long)

@Dao
interface TrackDao {

  @Query("SELECT * FROM track ORDER BY title COLLATE NOCASE")
  fun observeAll(): Flow<List<TrackEntity>>

  @Query("SELECT * FROM track WHERE media_store_id IN (:mediaStoreIds)")
  suspend fun getByMediaStoreIds(mediaStoreIds: List<Long>): List<TrackEntity>

  @Query("SELECT id, media_store_id AS mediaStoreId FROM track")
  suspend fun idPairs(): List<TrackIdPair>

  @Upsert
  suspend fun upsert(tracks: List<TrackEntity>)

  @Query("DELETE FROM track WHERE media_store_id NOT IN (:mediaStoreIds)")
  suspend fun deleteMissing(mediaStoreIds: List<Long>)

  /**
   * MediaStoreから読み込んだ曲の一覧でデータベースを置き換える。
   * 登録済みの曲はIDを引き継ぐ (プレイリストなどからの参照を保つため)。
   */
  @Transaction
  suspend fun sync(incoming: List<TrackEntity>) {
    val ids = idPairs().associate { it.mediaStoreId to it.id }
    upsert(incoming.map { it.copy(id = ids[it.mediaStoreId] ?: 0) })
    deleteMissing(incoming.map { it.mediaStoreId })
  }
}
