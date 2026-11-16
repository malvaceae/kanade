package me.mallows.kanade.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

  @Query("SELECT * FROM track ORDER BY title COLLATE NOCASE")
  fun observeAll(): Flow<List<TrackEntity>>

  @Query("SELECT * FROM track WHERE media_store_id IN (:mediaStoreIds)")
  suspend fun getByMediaStoreIds(mediaStoreIds: List<Long>): List<TrackEntity>

  @Upsert
  suspend fun upsert(tracks: List<TrackEntity>)

  @Query("DELETE FROM track WHERE media_store_id NOT IN (:mediaStoreIds)")
  suspend fun deleteMissing(mediaStoreIds: List<Long>)
}
