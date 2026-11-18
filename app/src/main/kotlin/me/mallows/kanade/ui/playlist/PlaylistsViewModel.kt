package me.mallows.kanade.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import me.mallows.kanade.core.data.db.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import me.mallows.kanade.core.data.settings.SettingsRepository
import me.mallows.kanade.core.data.settings.SortSetting
import me.mallows.kanade.ui.library.PlaylistListSort
import me.mallows.kanade.ui.library.SortCategory
import me.mallows.kanade.ui.library.sortedWith
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import me.mallows.kanade.core.data.db.PlaylistSummary
import me.mallows.kanade.core.data.playlist.PlaylistRepository

/** プレイリストの一覧・作成・曲の追加 */
@HiltViewModel
class PlaylistsViewModel @Inject constructor(
  private val repository: PlaylistRepository,
  private val settings: SettingsRepository,
) : ViewModel() {

  /** 一覧の並べ方 (nullは既定の名前順) */
  val sort: StateFlow<SortSetting?> =
    settings.sort(SortCategory.PLAYLISTS).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  /** null は読み込み前 */
  val playlists: StateFlow<List<PlaylistSummary>?> =
    combine(repository.playlists, settings.sort(SortCategory.PLAYLISTS)) { list, sort ->
      list.sortedWith(sort, PlaylistListSort.entries.toTypedArray(), PlaylistListSort.NAME)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  fun setSort(setting: SortSetting) {
    viewModelScope.launch { settings.setSort(SortCategory.PLAYLISTS, setting) }
  }

  suspend fun create(name: String): Long = repository.create(name.trim())

  /** プレイリストの曲 (保存された順) */
  suspend fun tracks(id: Long): List<TrackEntity> = repository.items(id).first().map { it.track }
  suspend fun delete(id: Long) = repository.delete(id)

  suspend fun duplicates(playlistId: Long, trackIds: List<Long>): Set<Long> = repository.duplicates(playlistId, trackIds)

  suspend fun add(playlistId: Long, trackIds: List<Long>) = repository.add(playlistId, trackIds)
}
