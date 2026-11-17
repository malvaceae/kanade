package me.mallows.kanade.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.mallows.kanade.core.domain.folder.classify

/** スキャンの結果。取り込んだ曲・対象外にした曲・消した曲の数 */
data class SyncResult(val included: Int, val excluded: Int, val deleted: Int)

/** 1回の問い合わせに渡すIDの数の上限 (SQLiteの変数の上限より小さく) */
private const val CHUNK = 500

/** 曲のIDとMediaStoreのIDの組 */
data class TrackIdPair(val id: Long, val mediaStoreId: Long)

@Dao
interface TrackDao {

  @Query("SELECT * FROM track WHERE excluded = 0 ORDER BY title COLLATE NOCASE")
  fun observeAll(): Flow<List<TrackEntity>>

  @Query("SELECT * FROM track WHERE excluded = 0 ORDER BY date_added DESC, title COLLATE NOCASE LIMIT :limit")
  fun observeRecentlyAdded(limit: Int): Flow<List<TrackEntity>>

  @Query("SELECT * FROM track WHERE media_store_id IN (:mediaStoreIds)")
  suspend fun getByMediaStoreIds(mediaStoreIds: List<Long>): List<TrackEntity>

  @Query("SELECT * FROM track WHERE id IN (:ids)")
  suspend fun getByIds(ids: List<Long>): List<TrackEntity>

  @Query("SELECT id, media_store_id AS mediaStoreId FROM track")
  suspend fun idPairs(): List<TrackIdPair>

  @Upsert
  suspend fun upsert(tracks: List<TrackEntity>)

  @Query("UPDATE track SET excluded = 1 WHERE media_store_id IN (:mediaStoreIds)")
  suspend fun markExcluded(mediaStoreIds: List<Long>)

  @Query("DELETE FROM track WHERE media_store_id IN (:mediaStoreIds)")
  suspend fun deleteByMediaStoreIds(mediaStoreIds: List<Long>)

  @Query("SELECT media_store_id FROM track WHERE excluded = 1")
  fun observeExcludedIds(): Flow<List<Long>>

  /**
   * スキャンの結果をデータベースに反映する。
   * 対象フォルダの曲 ([included]) は登録・更新し、対象外の印を外す。登録済みの曲はIDを引き継ぐ (プレイリストなどからの参照を保つため)。
   * 登録済みで[included]にない曲は、端末にあれば ([existing]) 対象外の印を付けて残し、なければ消す (プレイリストからも消える)。
   */
  @Transaction
  suspend fun sync(included: List<TrackEntity>, existing: Set<Long>): SyncResult {
    val ids = idPairs().associate { it.mediaStoreId to it.id }
    upsert(included.map { it.copy(id = ids[it.mediaStoreId] ?: 0, excluded = false) })
    val plan = classify(stored = ids.keys, included = included.mapTo(HashSet()) { it.mediaStoreId }, existing = existing)
    plan.excluded.chunked(CHUNK).forEach { markExcluded(it) }
    plan.deleted.chunked(CHUNK).forEach { deleteByMediaStoreIds(it) }
    return SyncResult(included = included.size, excluded = plan.excluded.size, deleted = plan.deleted.size)
  }

  // --- アルバム ---

  @Query(
    """
    SELECT album_id AS albumId, MAX(album) AS album,
      COALESCE(MAX(album_artist), MAX(artist)) AS artist,
      COUNT(*) AS trackCount, SUM(duration_ms) AS durationMs, MAX(year) AS year,
      MIN(uri) AS artUri, MAX(date_added) AS dateAdded
    FROM track WHERE excluded = 0 GROUP BY album_id
    """
  )
  fun observeAlbums(): Flow<List<AlbumSummary>>

  @Query("SELECT * FROM track WHERE excluded = 0 AND album_id IS :albumId ORDER BY disc_no, track_no, title COLLATE NOCASE")
  fun observeAlbumTracks(albumId: Long?): Flow<List<TrackEntity>>

  // --- アーティスト (アルバムアーティストを優先) ---

  @Query(
    """
    SELECT COALESCE(album_artist, artist) AS name, COUNT(*) AS trackCount,
      COUNT(DISTINCT album_id) AS albumCount, SUM(duration_ms) AS durationMs, MIN(uri) AS artUri
    FROM track WHERE excluded = 0 GROUP BY name
    """
  )
  fun observeArtists(): Flow<List<GroupSummary>>

  @Query(
    """
    SELECT * FROM track WHERE excluded = 0 AND COALESCE(album_artist, artist) IS :name
    ORDER BY album COLLATE NOCASE, disc_no, track_no, title COLLATE NOCASE
    """
  )
  fun observeArtistTracks(name: String?): Flow<List<TrackEntity>>

  // --- フォルダ ---

  @Query(
    """
    SELECT COALESCE(volume, '') || ':' || COALESCE(relative_path, '') AS name, COUNT(*) AS trackCount,
      COUNT(DISTINCT album_id) AS albumCount, SUM(duration_ms) AS durationMs, MIN(uri) AS artUri
    FROM track WHERE excluded = 0 GROUP BY volume, relative_path
    """
  )
  fun observeFolders(): Flow<List<GroupSummary>>

  @Query(
    """
    SELECT * FROM track WHERE excluded = 0 AND COALESCE(volume, '') = :volume AND COALESCE(relative_path, '') = :path
    ORDER BY disc_no, track_no, title COLLATE NOCASE
    """
  )
  fun observeFolderTracks(volume: String, path: String): Flow<List<TrackEntity>>

  // --- ジャンル ---

  @Query(
    """
    SELECT genre AS name, COUNT(*) AS trackCount,
      COUNT(DISTINCT album_id) AS albumCount, SUM(duration_ms) AS durationMs, MIN(uri) AS artUri
    FROM track WHERE excluded = 0 GROUP BY genre
    """
  )
  fun observeGenres(): Flow<List<GroupSummary>>

  @Query("SELECT * FROM track WHERE excluded = 0 AND genre IS :genre ORDER BY title COLLATE NOCASE")
  fun observeGenreTracks(genre: String?): Flow<List<TrackEntity>>

  // --- 検索 ---

  @Query(
    """
    SELECT * FROM track
    WHERE excluded = 0 AND (title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%'
      OR album_artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%')
    ORDER BY title COLLATE NOCASE LIMIT 500
    """
  )
  fun search(query: String): Flow<List<TrackEntity>>
}
