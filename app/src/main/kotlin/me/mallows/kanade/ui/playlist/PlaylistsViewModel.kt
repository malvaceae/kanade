package me.mallows.kanade.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import me.mallows.kanade.core.data.db.TrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import me.mallows.kanade.core.data.db.PlaylistSummary
import me.mallows.kanade.core.data.playlist.M3uFiles
import me.mallows.kanade.core.data.playlist.M3uImportResult
import me.mallows.kanade.core.data.playlist.PlaylistRepository

/** プレイリストの一覧・作成・曲の追加 */
@HiltViewModel
class PlaylistsViewModel @Inject constructor(
  private val repository: PlaylistRepository,
  private val m3u: M3uFiles,
) : ViewModel() {

  suspend fun importM3u(uri: android.net.Uri): M3uImportResult = m3u.import(uri)


  val playlists: StateFlow<List<PlaylistSummary>?> =
    repository.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  suspend fun create(name: String): Long = repository.create(name.trim())

  /** プレイリストの曲 (保存された順) */
  suspend fun tracks(id: Long): List<TrackEntity> = repository.items(id).first().map { it.track }
  suspend fun delete(id: Long) = repository.delete(id)

  suspend fun duplicates(playlistId: Long, trackIds: List<Long>): Set<Long> = repository.duplicates(playlistId, trackIds)

  suspend fun add(playlistId: Long, trackIds: List<Long>) = repository.add(playlistId, trackIds)
}
