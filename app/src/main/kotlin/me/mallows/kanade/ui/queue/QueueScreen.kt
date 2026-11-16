package me.mallows.kanade.ui.queue

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.mallows.kanade.R
import me.mallows.kanade.playback.QueueItem
import me.mallows.kanade.ui.common.LocalBottomBarActive
import me.mallows.kanade.ui.common.LocalSnackbarHost
import me.mallows.kanade.ui.common.SelectionCheck
import me.mallows.kanade.ui.common.TrackArtwork
import me.mallows.kanade.ui.common.audioUri
import me.mallows.kanade.ui.common.showUndo
import me.mallows.kanade.ui.icons.KanadeIcons
import me.mallows.kanade.ui.playlist.Undo
import me.mallows.kanade.ui.selection.BarAction
import me.mallows.kanade.ui.selection.SelectionBottomBar
import me.mallows.kanade.ui.selection.SelectionTopBar
import me.mallows.kanade.ui.selection.dragToSelect
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** キューの1行と、今の並びでの元の位置 */
private data class Entry(val origin: Int, val item: QueueItem)

/**
 * 再生キュー。今の曲の後ろに並ぶ曲を、「次に再生」で入れた曲とその後の曲に分けて見せる。
 * プレイリストと同じく、ドラッグ (まとめて移動を含む)・なぞって選択・先頭へ／末尾へ・削除・元に戻すができる。
 * シャッフル中は並べ替えできない。
 */
@Composable
fun QueueScreen(onBack: () -> Unit, viewModel: QueueViewModel = hiltViewModel()) {
  val queue by viewModel.queue.collectAsStateWithLifecycle()
  val scope = rememberCoroutineScope()
  val snackbar = LocalSnackbarHost.current
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  val barActive = LocalBottomBarActive.current
  val undoLabel = stringResource(R.string.action_undo)
  val locked = queue.shuffle

  val entries = remember(queue) { queue.upcoming.mapIndexed { i, item -> Entry(i, item) } }
  var local by remember(entries) { mutableStateOf(entries) }
  var selected by remember(entries) { mutableStateOf(setOf<Long>()) }
  val selecting = selected.isNotEmpty()
  var moving by remember { mutableStateOf(emptySet<Int>()) }
  var grabbed by remember { mutableStateOf<Int?>(null) }

  DisposableEffect(selecting) {
    barActive.value = selecting
    onDispose { barActive.value = false }
  }
  BackHandler(enabled = selecting) { selected = emptySet() }

  fun perform(message: String, op: suspend () -> Undo?) {
    selected = emptySet()
    scope.launch {
      val undo = op() ?: return@launch
      snackbar.showUndo(message, undoLabel) { scope.launch { undo() } }
    }
  }

  val listState = rememberLazyListState()
  val reorderState = rememberReorderableLazyListState(listState) { from, to ->
    val fromKey = (from.key as? Long)?.toInt() ?: return@rememberReorderableLazyListState
    val toKey = (to.key as? Long)?.toInt() ?: return@rememberReorderableLazyListState
    val fi = local.indexOfFirst { it.origin == fromKey }
    val ti = local.indexOfFirst { it.origin == toKey }
    if (fi < 0 || ti < 0) return@rememberReorderableLazyListState
    local = local.toMutableList().apply { add(ti, removeAt(fi)) }
    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
  }

  fun startDrag(entry: Entry) {
    val sel = selected.map { it.toInt() }.toSet()
    val block = if (entry.origin in sel && sel.size > 1) sel else setOf(entry.origin)
    moving = block
    grabbed = entry.origin
    if (block.size > 1) local = local.filter { it.origin == entry.origin || it.origin !in block }
  }

  fun endDrag() {
    val rep = grabbed ?: return
    val block = moving
    val index = local.indexOfFirst { it.origin == rep }
    val slot = local.subList(0, index.coerceAtLeast(0)).count { it.origin !in block }
    moving = emptySet()
    grabbed = null
    selected = emptySet()
    val message = context.resources.getQuantityString(R.plurals.message_moved, block.size, block.size)
    scope.launch {
      val undo = viewModel.moveBlock(block, slot)
      if (undo == null) local = entries else snackbar.showUndo(message, undoLabel) { scope.launch { undo() } }
    }
  }

  // 先頭から続く「次に再生」で入れた曲の数
  val queuedLead = local.takeWhile { it.item.queued }.size

  Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
      if (selecting) {
        SelectionTopBar(
          count = selected.size,
          allSelected = selected.size == local.size,
          onSelectAll = { selected = if (selected.size == local.size) emptySet() else local.map { it.origin.toLong() }.toSet() },
          onClose = { selected = emptySet() },
        )
      } else {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onBack) { Icon(KanadeIcons.ChevronDown, contentDescription = stringResource(R.string.action_close)) }
          Text(stringResource(R.string.queue_title), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        }
      }
      LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 96.dp),
        modifier = Modifier.weight(1f).dragToSelect(
          state = listState,
          keys = local.map { it.origin.toLong() },
          currentSelection = { selected },
          onStart = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
          onChange = { selected = it },
          excludeEnd = 56.dp,
        ),
      ) {
        queue.current?.let { current ->
          item(key = "now_label") { SectionLabel(stringResource(R.string.queue_now_playing)) }
          item(key = "now") { QueueRow(current, selectionMode = false, selected = false, onClick = {}, handle = null, highlight = true) }
        }
        if (locked && local.isNotEmpty()) {
          item(key = "locked") {
            Text(
              stringResource(R.string.queue_shuffle_locked),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
          }
        }
        local.forEachIndexed { index, entry ->
          // 見出しは並べ替えの対象にしない別の項目にする
          if (index == 0 && queuedLead > 0) {
            item(key = "label_next") { SectionLabel(pluralStringResource(R.plurals.queue_next_section, queuedLead, queuedLead)) }
          }
          if (index == queuedLead) {
            item(key = "label_after") { SectionLabel(stringResource(R.string.queue_after_section)) }
          }
          item(key = entry.origin.toLong()) {
          ReorderableItem(reorderState, key = entry.origin.toLong()) { dragging ->
            val isSelected = entry.origin.toLong() in selected
            Surface(
              shadowElevation = if (dragging) 8.dp else 0.dp,
              shape = RoundedCornerShape(if (dragging) 12.dp else 0.dp),
              color = if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.background,
            ) {
              QueueRow(
                item = entry.item,
                selectionMode = selecting,
                selected = isSelected && !dragging,
                onClick = {
                  if (selecting) {
                    val k = entry.origin.toLong()
                    selected = if (isSelected) selected - k else selected + k
                  } else {
                    viewModel.playAt(entry.origin)
                  }
                },
                badge = if (dragging && moving.size > 1) moving.size else null,
                handle = if (locked) null else {
                  {
                    IconButton(
                      onClick = {},
                      modifier = Modifier.draggableHandle(onDragStarted = { startDrag(entry) }, onDragStopped = { endDrag() }),
                    ) { Icon(KanadeIcons.Grip, contentDescription = stringResource(R.string.action_drag), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                  }
                },
              )
            }
          }
          }
        }
        if (local.isEmpty()) {
          item(key = "empty") {
            Text(
              stringResource(R.string.queue_empty),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(24.dp),
            )
          }
        }
      }
    }

    if (selecting) {
      val sel = selected.map { it.toInt() }.toSet()
      val n = sel.size
      SelectionBottomBar(
        modifier = Modifier.align(Alignment.BottomCenter),
        actions = listOf(
          BarAction(KanadeIcons.ToTop, stringResource(R.string.queue_play_next), enabled = !locked) {
            perform(context.resources.getQuantityString(R.plurals.message_moved_top, n, n)) { viewModel.moveToTop(sel) }
          },
          BarAction(KanadeIcons.ToBottom, stringResource(R.string.move_bottom), enabled = !locked) {
            perform(context.resources.getQuantityString(R.plurals.message_moved_bottom, n, n)) { viewModel.moveToBottom(sel) }
          },
          BarAction(KanadeIcons.Delete, stringResource(R.string.action_delete)) {
            perform(context.resources.getQuantityString(R.plurals.message_removed_queue, n, n)) { viewModel.remove(sel) }
          },
        ),
      )
    }
  }
}

@Composable
private fun SectionLabel(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
  )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueRow(
  item: QueueItem,
  selectionMode: Boolean,
  selected: Boolean,
  onClick: () -> Unit,
  handle: (@Composable () -> Unit)?,
  highlight: Boolean = false,
  badge: Int? = null,
) {
  val scheme = MaterialTheme.colorScheme
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(64.dp)
      .background(if (selected) scheme.primaryContainer else Color.Transparent)
      .combinedClickable(onClick = onClick)
      .padding(start = 12.dp, end = if (handle != null) 4.dp else 16.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (selectionMode) Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) { SelectionCheck(selected) }
    TrackArtwork(uri = audioUri(item.mediaStoreId), size = 44.dp)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        item.title,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
        color = if (highlight) scheme.primary else scheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        item.artist ?: stringResource(R.string.unknown_artist),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
        color = scheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    if (badge != null) {
      Surface(shape = CircleShape, color = scheme.primary) {
        Text(
          pluralStringResource(R.plurals.track_count, badge, badge),
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
          color = scheme.onPrimary,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
      }
    }
    handle?.invoke()
  }
}
