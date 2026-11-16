package me.mallows.kanade.ui.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.mallows.kanade.R
import me.mallows.kanade.core.data.db.PlaylistItem
import me.mallows.kanade.ui.common.LocalBottomBarActive
import me.mallows.kanade.ui.common.LocalSnackbarHost
import me.mallows.kanade.ui.common.TrackRow
import me.mallows.kanade.ui.common.showMessage
import me.mallows.kanade.ui.common.showUndo
import me.mallows.kanade.ui.icons.KanadeIcons
import me.mallows.kanade.ui.library.Loading
import me.mallows.kanade.ui.library.SortButton
import me.mallows.kanade.ui.library.TrackSort
import me.mallows.kanade.ui.selection.BarAction
import me.mallows.kanade.ui.selection.SelectionBottomBar
import me.mallows.kanade.ui.selection.SelectionTopBar
import me.mallows.kanade.ui.selection.TrackActions
import me.mallows.kanade.ui.selection.dragToSelect
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * プレイリストの中身と並べ替え。
 * - ⋮⋮ をドラッグで移動。選択中の曲を掴むと、選んだ曲がまとめて1つのかたまりになって動く
 * - 長押しで選択を始め、なぞって範囲選択
 * - 選択中は「移動・入れ替え・逆順・次に再生・削除」
 * - ソートは表示だけ。「この並びで保存」で保存された順番を書き換える。ソート中は移動できない
 */
@Composable
fun PlaylistDetailScreen(
  playlistId: Long,
  playingMediaStoreId: Long?,
  actions: TrackActions,
  onBack: () -> Unit,
  bottomPadding: Dp,
  viewModel: PlaylistDetailViewModel = hiltViewModel<PlaylistDetailViewModel, PlaylistDetailViewModel.Factory>(
    key = "playlist-$playlistId",
    creationCallback = { it.create(playlistId) },
  ),
) {
  val playlist by viewModel.playlist.collectAsStateWithLifecycle()
  val items by viewModel.items.collectAsStateWithLifecycle()
  val sort by viewModel.sort.collectAsStateWithLifecycle()
  val sorted = sort.sort != null

  val scope = rememberCoroutineScope()
  val snackbar = LocalSnackbarHost.current
  val res = LocalResources.current
  val haptic = LocalHapticFeedback.current
  val barActive = LocalBottomBarActive.current
  val undoLabel = stringResource(R.string.action_undo)

  var selected by rememberSaveable { mutableStateOf(setOf<Long>()) }
  val selecting = selected.isNotEmpty()
  var sheet by rememberSaveable { mutableStateOf<String?>(null) }
  var menuOpen by remember { mutableStateOf(false) }
  val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
    if (uri != null) {
      scope.launch {
        val message = runCatching { viewModel.exportM3u(uri) }
          .fold({ n -> res.getQuantityString(R.plurals.message_m3u_exported, n, n) }, { res.getString(R.string.message_m3u_failed) })
        snackbar.showMessage(message)
      }
    }
  }

  DisposableEffect(selecting) {
    barActive.value = selecting
    onDispose { barActive.value = false }
  }
  BackHandler(enabled = selecting) { selected = emptySet() }

  /** 操作を実行し、並びが変わったら「元に戻す」を出す */
  fun perform(message: String, op: suspend () -> Undo?) {
    scope.launch {
      val undo = op() ?: return@launch
      snackbar.showUndo(message, undoLabel) { scope.launch { undo() } }
    }
  }

  val all = items ?: return Loading()
  // ドラッグ中の並び。保存された結果が届いたら置き換わる
  var local by remember(all) { mutableStateOf(all) }
  val ids = remember(all) { all.map { it.entryId }.toSet() }
  if (!ids.containsAll(selected)) selected = selected intersect ids

  // ドラッグ中: 動かしている曲 (かたまり) と、掴んでいる代表の曲
  var moving by remember { mutableStateOf(emptySet<Long>()) }
  var grabbed by remember { mutableStateOf<Long?>(null) }

  val listState = rememberLazyListState()
  val reorderState = rememberReorderableLazyListState(listState) { from, to ->
    val fromKey = from.key as? Long ?: return@rememberReorderableLazyListState
    val toKey = to.key as? Long ?: return@rememberReorderableLazyListState
    val fi = local.indexOfFirst { it.entryId == fromKey }
    val ti = local.indexOfFirst { it.entryId == toKey }
    if (fi < 0 || ti < 0) return@rememberReorderableLazyListState
    local = local.toMutableList().apply { add(ti, removeAt(fi)) }
    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
  }

  fun startDrag(item: PlaylistItem) {
    val block = if (item.entryId in selected && selected.size > 1) selected else setOf(item.entryId)
    moving = block
    grabbed = item.entryId
    if (block.size > 1) {
      // 選んだ曲を掴んだ曲の位置にまとめて畳む
      local = local.filter { it.entryId == item.entryId || it.entryId !in block }
    }
    haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
  }

  fun endDrag() {
    val rep = grabbed ?: return
    val block = moving
    val index = local.indexOfFirst { it.entryId == rep }
    val slot = local.subList(0, index.coerceAtLeast(0)).count { it.entryId !in block }
    moving = emptySet()
    grabbed = null
    val message = res.getQuantityString(R.plurals.message_moved, block.size, block.size)
    scope.launch {
      val undo = viewModel.moveBlock(block, slot)
      if (undo == null) {
        local = all
      } else {
        snackbar.showUndo(message, undoLabel) { scope.launch { undo() } }
      }
    }
  }

  Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
      // 上部バー
      if (selecting) {
        SelectionTopBar(
          count = selected.size,
          allSelected = selected.size == all.size,
          onSelectAll = { selected = if (selected.size == all.size) emptySet() else ids },
          onClose = { selected = emptySet() },
        )
      } else {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onBack) { Icon(KanadeIcons.Back, contentDescription = stringResource(R.string.action_back)) }
          Spacer(Modifier.weight(1f))
          SortButton(sorted) { sheet = "sort" }
          Box {
            IconButton(onClick = { menuOpen = true }) { Icon(KanadeIcons.More, contentDescription = stringResource(R.string.action_more)) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
              DropdownMenuItem(text = { Text(stringResource(R.string.playlist_rename)) }, onClick = { menuOpen = false; sheet = "rename" })
              DropdownMenuItem(
                text = { Text(stringResource(R.string.playlist_export_m3u)) },
                onClick = { menuOpen = false; exportLauncher.launch("${playlist?.name ?: "playlist"}.m3u8") },
              )
              DropdownMenuItem(text = { Text(stringResource(R.string.playlist_delete)) }, onClick = { menuOpen = false; sheet = "delete" })
            }
          }
        }
      }

      LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = if (selecting) 88.dp else bottomPadding),
        modifier = Modifier
          .weight(1f)
          .dragToSelect(
            state = listState,
            keys = local.map { it.entryId },
            currentSelection = { selected },
            onStart = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
            onChange = { selected = it },
            excludeEnd = 56.dp,
          ),
      ) {
        if (!selecting) {
          item(key = "header") {
            PlaylistHeader(
              name = playlist?.name.orEmpty(),
              items = all,
              sortLabel = sort.sort?.let { stringResource(it.label) },
              onPlay = { actions.play(all.map { it.track }, 0) },
              onShuffle = { actions.play(all.map { it.track }.shuffled(), 0) },
            )
          }
        }
        if (all.isEmpty()) {
          item(key = "empty") {
            Text(
              stringResource(R.string.playlist_empty),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(24.dp),
            )
          }
        }
        itemsIndexed(local, key = { _, it -> it.entryId }) { index, item ->
          ReorderableItem(reorderState, key = item.entryId) { dragging ->
            val isSelected = item.entryId in selected
            Surface(
              shadowElevation = if (dragging) 8.dp else 0.dp,
              shape = if (dragging) RoundedCornerShape(12.dp) else RoundedCornerShape(0.dp),
              color = if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.background,
            ) {
              TrackRow(
                track = item.track,
                number = index + 1,
                playing = item.track.mediaStoreId == playingMediaStoreId,
                selectionMode = selecting,
                selected = isSelected && !dragging,
                onClick = {
                  if (selecting) {
                    selected = if (isSelected) selected - item.entryId else selected + item.entryId
                  } else {
                    actions.play(local.map { it.track }, index)
                  }
                },
                onLongClick = null,
                trailing = {
                  if (dragging && moving.size > 1) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                      Text(
                        pluralStringResource(R.plurals.track_count, moving.size, moving.size),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                      )
                    }
                  }
                  if (!sorted) {
                    IconButton(
                      onClick = {},
                      modifier = Modifier.draggableHandle(
                        onDragStarted = { startDrag(item) },
                        onDragStopped = { endDrag() },
                      ),
                    ) {
                      Icon(KanadeIcons.Grip, contentDescription = stringResource(R.string.action_drag), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                  }
                },
              )
            }
          }
        }
      }
    }

    if (selecting) {
      val n = selected.size
      SelectionBottomBar(
        modifier = Modifier.align(Alignment.BottomCenter),
        actions = listOf(
          BarAction(KanadeIcons.Move, stringResource(R.string.action_move), enabled = !sorted) { sheet = "move" },
          BarAction(KanadeIcons.Swap, stringResource(R.string.action_swap), enabled = !sorted && n == 2) {
            perform(res.getString(R.string.message_swapped)) { viewModel.swap(selected) }
          },
          BarAction(KanadeIcons.Reverse, stringResource(R.string.action_reverse), enabled = !sorted && n >= 2) {
            perform(res.getQuantityString(R.plurals.message_reversed, n, n)) { viewModel.reverse(selected) }
          },
          BarAction(KanadeIcons.PlayNext, stringResource(R.string.action_play_next)) {
            actions.playNext(all.filter { it.entryId in selected }.map { it.track })
            selected = emptySet()
            scope.launch { snackbar.showMessage(res.getString(R.string.message_play_next, n)) }
          },
          BarAction(KanadeIcons.Delete, stringResource(R.string.action_delete)) {
            val target = selected
            selected = emptySet()
            perform(res.getQuantityString(R.plurals.message_removed, n, n)) { viewModel.remove(target) }
          },
        ),
      )
    }
  }

  when (sheet) {
    "move" -> MoveSheet(
      count = selected.size,
      max = viewModel.count() - selected.size + 1,
      onTop = {
        sheet = null
        val n = selected.size
        perform(res.getQuantityString(R.plurals.message_moved_top, n, n)) { viewModel.moveToTop(selected) }
      },
      onBottom = {
        sheet = null
        val n = selected.size
        perform(res.getQuantityString(R.plurals.message_moved_bottom, n, n)) { viewModel.moveToBottom(selected) }
      },
      onNumber = { number ->
        sheet = null
        val n = selected.size
        perform(res.getQuantityString(R.plurals.message_moved_number, n, n, number)) { viewModel.moveToNumber(selected, number) }
      },
      onDismiss = { sheet = null },
    )
    "sort" -> PlaylistSortSheet(
      current = sort,
      onSelect = { viewModel.setSort(it) },
      onSave = {
        sheet = null
        perform(res.getString(R.string.message_sort_saved)) { viewModel.saveSortedOrder() }
      },
      onDismiss = { sheet = null },
    )
    "rename" -> PlaylistNameDialog(
      title = stringResource(R.string.playlist_rename),
      initial = playlist?.name.orEmpty(),
      onConfirm = { name -> sheet = null; scope.launch { viewModel.rename(name) } },
      onDismiss = { sheet = null },
    )
    "delete" -> AlertDialog(
      onDismissRequest = { sheet = null },
      title = { Text(stringResource(R.string.playlist_delete)) },
      text = { Text(stringResource(R.string.playlist_delete_confirm, playlist?.name.orEmpty())) },
      confirmButton = {
        TextButton(onClick = {
          sheet = null
          scope.launch {
            viewModel.delete()
            onBack()
          }
        }) { Text(stringResource(R.string.action_delete)) }
      },
      dismissButton = { TextButton(onClick = { sheet = null }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }
}

@Composable
private fun PlaylistHeader(
  name: String,
  items: List<PlaylistItem>,
  sortLabel: String?,
  onPlay: () -> Unit,
  onShuffle: () -> Unit,
) {
  Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(name, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Row(Modifier.weight(1f)) {
        Text(
          "${pluralStringResource(R.plurals.track_count, items.size, items.size)} · ${items.sumOf { it.track.durationMs } / 60_000}${stringResource(R.string.minutes_suffix)}",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (sortLabel != null) {
          Text(" · $sortLabel", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
      }
      FilledTonalIconButton(onClick = onShuffle, shape = CircleShape, enabled = items.isNotEmpty()) {
        Icon(KanadeIcons.Shuffle, contentDescription = stringResource(R.string.action_shuffle_play), modifier = Modifier.size(20.dp))
      }
      Button(onClick = onPlay, enabled = items.isNotEmpty(), modifier = Modifier.padding(start = 8.dp)) {
        Icon(KanadeIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.action_play), modifier = Modifier.padding(start = 6.dp))
      }
    }
  }
}

/** 選んだ曲の移動先を選ぶシート */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveSheet(
  count: Int,
  max: Int,
  onTop: () -> Unit,
  onBottom: () -> Unit,
  onNumber: (Int) -> Unit,
  onDismiss: () -> Unit,
) {
  var number by rememberSaveable { mutableStateOf("") }
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
      Text(
        pluralStringResource(R.plurals.move_title, count, count),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
      )
      SheetRow(KanadeIcons.ToTop, stringResource(R.string.move_top), onTop)
      SheetRow(KanadeIcons.ToBottom, stringResource(R.string.move_bottom), onBottom)
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedTextField(
          value = number,
          onValueChange = { v -> number = v.filter(Char::isDigit).take(6) },
          label = { Text(stringResource(R.string.move_to_number)) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.width(160.dp),
        )
        Button(onClick = { number.toIntOrNull()?.let(onNumber) }, enabled = number.toIntOrNull() != null) {
          Text(stringResource(R.string.action_move))
        }
      }
      Text(
        stringResource(R.string.move_hint, max),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp),
      )
    }
  }
}

@Composable
private fun SheetRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().height(56.dp).clickable(onClick = onClick).padding(horizontal = 24.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Icon(icon, contentDescription = null)
    Text(label, style = MaterialTheme.typography.bodyLarge)
  }
}

/** プレイリストのソート。表示だけを変え、「この並びで保存」で確定する */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistSortSheet(
  current: PlaylistSort,
  onSelect: (PlaylistSort) -> Unit,
  onSave: () -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
      Text(
        stringResource(R.string.sort_title_sheet),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
      )
      val options: List<TrackSort?> = listOf(null) + TrackSort.entries
      options.forEach { option ->
        Row(
          Modifier.fillMaxWidth().height(52.dp).clickable { onSelect(current.copy(sort = option)) }.padding(horizontal = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          RadioButton(selected = current.sort == option, onClick = { onSelect(current.copy(sort = option)) })
          Text(
            if (option == null) stringResource(R.string.sort_saved_order) else stringResource(option.label),
            style = MaterialTheme.typography.bodyLarge,
          )
        }
      }
      if (current.sort != null) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(selected = !current.descending, onClick = { onSelect(current.copy(descending = false)) }, label = { Text(stringResource(R.string.sort_ascending)) })
          FilterChip(selected = current.descending, onClick = { onSelect(current.copy(descending = true)) }, label = { Text(stringResource(R.string.sort_descending)) })
        }
        Row(
          Modifier.fillMaxWidth().height(64.dp).clickable(onClick = onSave).padding(horizontal = 24.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Icon(KanadeIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Column {
            Text(stringResource(R.string.sort_save_this_order), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.sort_save_this_order_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }
  }
}
