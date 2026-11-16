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

  @Query("SELECT * FROM track ORDER BY date_added DESC, title COLLATE NOCASE LIMIT :limit")
  fun observeRecentlyAdded(limit: Int): Flow<List<TrackEntity>>

  @Query("SELECT * FROM track WHERE media_store_id IN (:mediaStoreIds)")
  suspend fun getByMediaStoreIds(mediaStoreIds: List<Long>): List<TrackEntity>

  @Query("SELECT * FROM track WHERE id IN (:ids)")
  suspend fun getByIds(ids: List<Long>): List<TrackEntity>

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

  // --- アルバム ---

  @Query(
    """
    SELECT album_id AS albumId, MAX(album) AS album,
      COALESCE(MAX(album_artist), MAX(artist)) AS artist,
      COUNT(*) AS trackCount, SUM(duration_ms) AS durationMs, MAX(year) AS year,
      MIN(uri) AS artUri, MAX(date_added) AS dateAdded
    FROM track GROUP BY album_id
    """
  )
  fun observeAlbums(): Flow<List<AlbumSummary>>

  @Query("SELECT * FROM track WHERE album_id IS :albumId ORDER BY disc_no, track_no, title COLLATE NOCASE")
  fun observeAlbumTracks(albumId: Long?): Flow<List<TrackEntity>>

  // --- アーティスト (アルバムアーティストを優先) ---

  @Query(
    """
    SELECT COALESCE(album_artist, artist) AS name, COUNT(*) AS trackCount,
      COUNT(DISTINCT album_id) AS albumCount, SUM(duration_ms) AS durationMs, MIN(uri) AS artUri
    FROM track GROUP BY name
    """
  )
  fun observeArtists(): Flow<List<GroupSummary>>

  @Query(
    """
    SELECT * FROM track WHERE COALESCE(album_artist, artist) IS :name
    ORDER BY album COLLATE NOCASE, disc_no, track_no, title COLLATE NOCASE
    """
  )
  fun observeArtistTracks(name: String?): Flow<List<TrackEntity>>

  // --- フォルダー ---

  @Query(
    """
    SELECT relative_path AS name, COUNT(*) AS trackCount,
      COUNT(DISTINCT album_id) AS albumCount, SUM(duration_ms) AS durationMs, MIN(uri) AS artUri
    FROM track GROUP BY relative_path
    """
  )
  fun observeFolders(): Flow<List<GroupSummary>>

  @Query("SELECT * FROM track WHERE relative_path IS :path ORDER BY disc_no, track_no, title COLLATE NOCASE")
  fun observeFolderTracks(path: String?): Flow<List<TrackEntity>>

  // --- ジャンル ---

  @Query(
    """
    SELECT genre AS name, COUNT(*) AS trackCount,
      COUNT(DISTINCT album_id) AS albumCount, SUM(duration_ms) AS durationMs, MIN(uri) AS artUri
    FROM track GROUP BY genre
    """
  )
  fun observeGenres(): Flow<List<GroupSummary>>

  @Query("SELECT * FROM track WHERE genre IS :genre ORDER BY title COLLATE NOCASE")
  fun observeGenreTracks(genre: String?): Flow<List<TrackEntity>>

  // --- 検索 ---

  @Query(
    """
    SELECT * FROM track
    WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%'
      OR album_artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%'
    ORDER BY title COLLATE NOCASE LIMIT 500
    """
  )
  fun search(query: String): Flow<List<TrackEntity>>
}
