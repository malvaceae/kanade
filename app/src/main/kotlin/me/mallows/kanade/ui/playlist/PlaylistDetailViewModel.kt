package me.mallows.kanade.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import me.mallows.kanade.core.data.db.PlaylistEntity
import me.mallows.kanade.core.data.db.PlaylistItem
import me.mallows.kanade.core.data.playlist.PlaylistRepository
import me.mallows.kanade.core.domain.reorder.Positioned
import me.mallows.kanade.core.domain.reorder.PositionedListEditor
import me.mallows.kanade.core.domain.reorder.ReorderResult
import me.mallows.kanade.ui.library.TrackSort

/** 元に戻す処理 */
typealias Undo = suspend () -> Unit

/** プレイリストの表示のソート。null は保存された順番 */
data class PlaylistSort(val sort: TrackSort?, val descending: Boolean = false)

@HiltViewModel(assistedFactory = PlaylistDetailViewModel.Factory::class)
class PlaylistDetailViewModel @AssistedInject constructor(
  @Assisted val playlistId: Long,
  private val repository: PlaylistRepository,
) : ViewModel() {

  val playlist: StateFlow<PlaylistEntity?> =
    repository.playlist(playlistId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  /** 保存された順番の並び */
  private val stored: StateFlow<List<PlaylistItem>?> =
    repository.items(playlistId).stateIn(viewModelScope, SharingStarted.Eagerly, null)

  private val _sort = MutableStateFlow(PlaylistSort(null))
  val sort: StateFlow<PlaylistSort> = _sort.asStateFlow()

  /** 画面に出す並び (ソート中はソートした表示) */
  val items: StateFlow<List<PlaylistItem>?> = combine(stored, _sort) { list, sort ->
    when {
      list == null -> null
      sort.sort == null -> list
      else -> list.sortedWith(compareBy<PlaylistItem, me.mallows.kanade.core.data.db.TrackEntity>(sort.sort.comparator) { it.track })
        .let { if (sort.descending) it.reversed() else it }
    }
  }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

  fun setSort(sort: PlaylistSort) {
    _sort.value = sort
  }

  private fun positioned(): List<Positioned> = stored.value.orEmpty().map { Positioned(it.entryId, it.position) }

  /** 並べ替えの結果を保存し、元に戻す処理を返す。並びが変わらなければnull */
  private suspend fun save(result: ReorderResult): Undo? {
    if (result.isEmpty) return null
    repository.updatePositions(playlistId, result.changes)
    val previous = result.previous
    return { repository.updatePositions(playlistId, previous) }
  }

  /** [ids]を「それ以外の曲」の中の[slot]番目に移す */
  suspend fun moveBlock(ids: Set<Long>, slot: Int): Undo? = save(PositionedListEditor.moveBlock(positioned(), ids, slot))
  suspend fun moveToTop(ids: Set<Long>): Undo? = save(PositionedListEditor.moveToTop(positioned(), ids))
  suspend fun moveToBottom(ids: Set<Long>): Undo? = save(PositionedListEditor.moveToBottom(positioned(), ids))
  suspend fun moveToNumber(ids: Set<Long>, number: Int): Undo? = save(PositionedListEditor.moveToNumber(positioned(), ids, number))
  suspend fun reverse(ids: Set<Long>): Undo? = save(PositionedListEditor.reverse(positioned(), ids))

  suspend fun swap(ids: Set<Long>): Undo? {
    val (a, b) = ids.toList().takeIf { it.size == 2 } ?: return null
    return save(PositionedListEditor.swap(positioned(), a, b))
  }

  /** 今のソート表示の並びで保存する */
  suspend fun saveSortedOrder(): Undo? {
    val order = items.value?.map { it.entryId } ?: return null
    val undo = save(PositionedListEditor.renumber(positioned(), order))
    _sort.value = PlaylistSort(null)
    return undo
  }

  suspend fun remove(ids: Set<Long>): Undo {
    val removed = repository.remove(playlistId, ids.toList())
    return { repository.restore(removed) }
  }

  suspend fun rename(name: String) = repository.rename(playlistId, name.trim())

  suspend fun delete() = repository.delete(playlistId)

  /** 保存された順番の総数 (「1 ~ N」の案内用) */
  fun count(): Int = stored.value?.size ?: 0

  @AssistedFactory
  interface Factory {
    fun create(playlistId: Long): PlaylistDetailViewModel
  }
}
