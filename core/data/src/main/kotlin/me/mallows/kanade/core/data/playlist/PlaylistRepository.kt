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

/** 削除したプレイリスト (元に戻すための内容) */
data class DeletedPlaylist(val playlist: PlaylistEntity, val entries: List<PlaylistEntryEntity>)

/** プレイリストの作成・編集 */
@Singleton
class PlaylistRepository @Inject constructor(
  private val dao: PlaylistDao,
  private val covers: PlaylistCovers,
) {

  val playlists: Flow<List<PlaylistSummary>> = dao.observePlaylists()

  fun playlist(id: Long): Flow<PlaylistEntity?> = dao.observePlaylist(id)

  fun items(playlistId: Long): Flow<List<PlaylistItem>> = dao.observeItems(playlistId)

  suspend fun create(name: String): Long {
    val now = System.currentTimeMillis()
    return dao.insertPlaylist(PlaylistEntity(name = name, createdAt = now, updatedAt = now))
  }

  suspend fun rename(id: Long, name: String) = dao.rename(id, name, System.currentTimeMillis())

  /**
   * プレイリストを削除する。元に戻すための内容を返す。
   * カバー画像のファイルは元に戻せるよう残すので、戻さないと決まったら[discard]で消す
   */
  suspend fun delete(id: Long): DeletedPlaylist? {
    val playlist = dao.getPlaylist(id) ?: return null
    val entries = dao.entriesOf(id)
    dao.deletePlaylist(id)
    return DeletedPlaylist(playlist, entries)
  }

  /** [delete]で削除したプレイリストを、同じ曲・並び・カバーのまま戻す */
  suspend fun restore(deleted: DeletedPlaylist) = dao.restorePlaylist(deleted.playlist, deleted.entries)

  /** 戻さないと決まったプレイリストの、残しておいたカバー画像を消す */
  fun discard(deleted: DeletedPlaylist) = covers.delete(deleted.playlist.cover)

  /** 選んだ画像をカバーにする。読めない画像ならfalse */
  suspend fun setCover(id: Long, image: android.net.Uri): Boolean {
    val path = covers.save(id, image) ?: return false
    val old = dao.cover(id)
    dao.setCover(id, path)
    dao.touch(id, System.currentTimeMillis())
    covers.delete(old)
    return true
  }

  /** カバーを先頭の曲のアートワークに戻す */
  suspend fun clearCover(id: Long) {
    val old = dao.cover(id)
    dao.setCover(id, null)
    dao.touch(id, System.currentTimeMillis())
    covers.delete(old)
  }

  /** [trackIds]のうち、すでにプレイリストに入っている曲 (アーティスト・曲名・長さが同じ曲を含む。[duplicateKey]) */
  suspend fun duplicates(playlistId: Long, trackIds: List<Long>): Set<Long> {
    val existing = dao.tracksOf(playlistId).mapTo(HashSet()) { it.duplicateKey }
    if (existing.isEmpty()) return emptySet()
    return trackIds.distinct().chunked(500).flatMap { dao.tracksByIds(it) }
      .filter { it.duplicateKey in existing }
      .mapTo(HashSet()) { it.id }
  }

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
  suspend fun restore(entries: List<PlaylistEntryEntity>) {
    if (entries.isEmpty()) return
    dao.insertEntries(entries)
    entries.map { it.playlistId }.distinct().forEach { dao.touch(it, System.currentTimeMillis()) }
  }
}
