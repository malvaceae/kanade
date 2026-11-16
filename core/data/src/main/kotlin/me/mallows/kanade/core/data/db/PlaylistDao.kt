package me.mallows.kanade.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "playlist")
data class PlaylistEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  @ColumnInfo(name = "created_at") val createdAt: Long,
  @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/**
 * プレイリストの1曲。同じ曲を何度でも入れられるよう、曲とは別のIDを持つ。
 * 並び順は小数で持ち、移動では動かした曲の値だけを書き換える。
 */
@Entity(
  tableName = "playlist_entry",
  foreignKeys = [
    ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlist_id"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = TrackEntity::class, parentColumns = ["id"], childColumns = ["track_id"], onDelete = ForeignKey.CASCADE),
  ],
  indices = [Index(value = ["playlist_id", "position"]), Index("track_id")],
)
data class PlaylistEntryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  @ColumnInfo(name = "playlist_id") val playlistId: Long,
  @ColumnInfo(name = "track_id") val trackId: Long,
  val position: Double,
)

/** 並び順だけの更新用 */
data class EntryPosition(val id: Long, val position: Double)

/** プレイリストの一覧の1行 */
data class PlaylistSummary(
  val id: Long,
  val name: String,
  val trackCount: Int,
  val durationMs: Long,
  val artUri: String?,
)

/** プレイリストの中の1曲 (曲の情報つき) */
data class PlaylistItem(
  @ColumnInfo(name = "entry_id") val entryId: Long,
  val position: Double,
  @Embedded val track: TrackEntity,
)

@Dao
interface PlaylistDao {

  @Query(
    """
    SELECT p.id AS id, p.name AS name, COUNT(t.id) AS trackCount,
      COALESCE(SUM(t.duration_ms), 0) AS durationMs, MIN(t.uri) AS artUri
    FROM playlist p
    LEFT JOIN playlist_entry e ON e.playlist_id = p.id
    LEFT JOIN track t ON t.id = e.track_id
    GROUP BY p.id ORDER BY p.name COLLATE NOCASE
    """
  )
  fun observePlaylists(): Flow<List<PlaylistSummary>>

  @Query("SELECT * FROM playlist WHERE id = :id")
  fun observePlaylist(id: Long): Flow<PlaylistEntity?>

  @Query(
    """
    SELECT e.id AS entry_id, e.position AS position, t.*
    FROM playlist_entry e JOIN track t ON t.id = e.track_id
    WHERE e.playlist_id = :playlistId ORDER BY e.position
    """
  )
  fun observeItems(playlistId: Long): Flow<List<PlaylistItem>>

  @Insert
  suspend fun insertPlaylist(playlist: PlaylistEntity): Long

  @Query("UPDATE playlist SET name = :name, updated_at = :now WHERE id = :id")
  suspend fun rename(id: Long, name: String, now: Long)

  @Query("UPDATE playlist SET updated_at = :now WHERE id = :id")
  suspend fun touch(id: Long, now: Long)

  @Query("DELETE FROM playlist_entry WHERE playlist_id = :id")
  suspend fun deleteEntriesOf(id: Long)

  @Query("DELETE FROM playlist WHERE id = :id")
  suspend fun deletePlaylistRow(id: Long)

  @Transaction
  suspend fun deletePlaylist(id: Long) {
    deleteEntriesOf(id)
    deletePlaylistRow(id)
  }

  @Query("SELECT MAX(position) FROM playlist_entry WHERE playlist_id = :playlistId")
  suspend fun maxPosition(playlistId: Long): Double?

  @Query("SELECT MIN(position) FROM playlist_entry WHERE playlist_id = :playlistId")
  suspend fun minPosition(playlistId: Long): Double?

  @Query("SELECT track_id FROM playlist_entry WHERE playlist_id = :playlistId AND track_id IN (:trackIds)")
  suspend fun existingTrackIds(playlistId: Long, trackIds: List<Long>): List<Long>

  @Query("SELECT * FROM playlist_entry WHERE id IN (:ids)")
  suspend fun entriesByIds(ids: List<Long>): List<PlaylistEntryEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertEntries(entries: List<PlaylistEntryEntity>)

  @Update(entity = PlaylistEntryEntity::class)
  suspend fun updatePositions(positions: List<EntryPosition>)

  @Query("DELETE FROM playlist_entry WHERE id IN (:ids)")
  suspend fun deleteEntries(ids: List<Long>)

  /** 端末から消えた曲をプレイリストから外す */
  @Query("DELETE FROM playlist_entry WHERE track_id NOT IN (SELECT id FROM track)")
  suspend fun deleteOrphans()
}
