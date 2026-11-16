package me.mallows.kanade.ui.queue

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import me.mallows.kanade.core.domain.reorder.IndexReorder
import me.mallows.kanade.playback.PlayerConnection
import me.mallows.kanade.playback.QueueState
import me.mallows.kanade.ui.playlist.Undo

/** 再生キューの表示と並べ替え。位置は「今の曲の後ろ」での0始まり */
@HiltViewModel
class QueueViewModel @Inject constructor(
  private val connection: PlayerConnection,
) : ViewModel() {

  val queue: StateFlow<QueueState> = connection.queue

  fun playAt(position: Int) {
    val item = queue.value.upcoming.getOrNull(position) ?: return
    connection.playAt(item.index)
  }

  private suspend fun apply(order: List<Int>): Undo? {
    if (IndexReorder.isIdentity(order)) return null
    val inverse = connection.reorderUpcoming(order) ?: return null
    return { connection.reorderUpcoming(inverse) }
  }

  private val size get() = queue.value.upcoming.size

  suspend fun moveBlock(selected: Set<Int>, slot: Int): Undo? = apply(IndexReorder.moveBlock(size, selected, slot))
  suspend fun moveToTop(selected: Set<Int>): Undo? = apply(IndexReorder.moveToTop(size, selected))
  suspend fun moveToBottom(selected: Set<Int>): Undo? = apply(IndexReorder.moveToBottom(size, selected))

  suspend fun remove(selected: Set<Int>): Undo {
    val removed = connection.removeUpcoming(selected)
    return { connection.restoreUpcoming(removed) }
  }
}
