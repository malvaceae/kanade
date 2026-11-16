package me.mallows.kanade.core.data.playlist

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import me.mallows.kanade.core.data.db.EntryPosition
import me.mallows.kanade.core.data.db.PlaylistDao
import me.mallows.kanade.core.data.db.PlaylistEntity
import me.mallows.kanade.core.data.db.PlaylistEntryEntity
import me.mallows.kanade.core.data.db.PlaylistItem
import me.mallows.kanade.core.data.db.PlaylistSummary

/** プレイリストの作成・編集 */
@Singleton
class PlaylistRepository @Inject constructor(
  private val dao: PlaylistDao,
) {

  val playlists: Flow<List<PlaylistSummary>> = dao.observePlaylists()

  fun playlist(id: Long): Flow<PlaylistEntity?> = dao.observePlaylist(id)

  fun items(playlistId: Long): Flow<List<PlaylistItem>> = dao.observeItems(playlistId)

  suspend fun create(name: String): Long {
    val now = System.currentTimeMillis()
    return dao.insertPlaylist(PlaylistEntity(name = name, createdAt = now, updatedAt = now))
  }

  suspend fun rename(id: Long, name: String) = dao.rename(id, name, System.currentTimeMillis())

  suspend fun delete(id: Long) = dao.deletePlaylist(id)

  /** [trackIds]のうち、すでにプレイリストに入っている曲 */
  suspend fun duplicates(playlistId: Long, trackIds: List<Long>): Set<Long> =
    dao.existingTrackIds(playlistId, trackIds.distinct()).toSet()

  /** 曲を末尾 (または先頭) に追加する */
  suspend fun add(playlistId: Long, trackIds: List<Long>, atTop: Boolean = false) {
    if (trackIds.isEmpty()) return
    val entries = if (atTop) {
      val min = dao.minPosition(playlistId) ?: 1.0
      trackIds.mapIndexed { i, id -> PlaylistEntryEntity(playlistId = playlistId, trackId = id, position = min - trackIds.size + i) }
    } else {
      val max = dao.maxPosition(playlistId) ?: 0.0
      trackIds.mapIndexed { i, id -> PlaylistEntryEntity(playlistId = playlistId, trackId = id, position = max + 1 + i) }
    }
    dao.insertEntries(entries)
    dao.touch(playlistId, System.currentTimeMillis())
  }

  /** 並び順を書き換える (変わった曲の分だけ) */
  suspend fun updatePositions(playlistId: Long, positions: Map<Long, Double>) {
    if (positions.isEmpty()) return
    dao.updatePositions(positions.map { (id, pos) -> EntryPosition(id, pos) })
    dao.touch(playlistId, System.currentTimeMillis())
  }

  /** 曲を外す。元に戻すための内容を返す */
  suspend fun remove(playlistId: Long, entryIds: List<Long>): List<PlaylistEntryEntity> {
    val removed = dao.entriesByIds(entryIds)
    dao.deleteEntries(entryIds)
    dao.touch(playlistId, System.currentTimeMillis())
    return removed
  }

  /** [remove]で外した曲を元に戻す */
  suspend fun restore(entries: List<PlaylistEntryEntity>) = dao.insertEntries(entries)
}
