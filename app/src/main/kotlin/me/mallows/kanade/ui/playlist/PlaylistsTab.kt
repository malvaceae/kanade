package me.mallows.kanade.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import me.mallows.kanade.ui.selection.dragToSelect
import me.mallows.kanade.ui.library.GroupSelectionBars
import me.mallows.kanade.ui.selection.BarAction
import me.mallows.kanade.ui.selection.navigationBarHeight
import androidx.compose.foundation.layout.fillMaxSize
import me.mallows.kanade.playback.PlaySource
import me.mallows.kanade.ui.selection.TrackActions
import me.mallows.kanade.core.data.db.PlaylistSummary
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.mallows.kanade.R
import me.mallows.kanade.ui.common.GroupRow
import me.mallows.kanade.ui.common.ListHeader
import me.mallows.kanade.ui.icons.KanadeIcons
import me.mallows.kanade.ui.library.Loading
import me.mallows.kanade.ui.common.LocalShowMessage
import me.mallows.kanade.ui.common.LocalShowUndo
import androidx.compose.ui.platform.LocalResources
import me.mallows.kanade.ui.library.PlaylistListSort
import me.mallows.kanade.ui.library.SortButton
import me.mallows.kanade.ui.library.SortSheet

/** ライブラリの「プレイリスト」タブ */
@Composable
fun PlaylistsTab(
  onOpen: (id: Long, name: String) -> Unit,
  actions: TrackActions,
  bottomPadding: Dp,
  viewModel: PlaylistsViewModel = hiltViewModel(),
) {
  val playlists by viewModel.playlists.collectAsStateWithLifecycle()
  val sort by viewModel.sort.collectAsStateWithLifecycle()
  var showSort by rememberSaveable { mutableStateOf(false) }
  var creating by rememberSaveable { mutableStateOf(false) }
  // 長押しで選択を始める。選択中は押すたびに選択を切り替える
  var selectedIds by rememberSaveable { mutableStateOf(setOf<Long>()) }
  val showMessage = LocalShowMessage.current
  val showUndo = LocalShowUndo.current
  val res = LocalResources.current
  val scope = rememberCoroutineScope()
  val list = playlists ?: return Loading()

  val selecting = selectedIds.isNotEmpty()
  Box(Modifier.fillMaxSize()) {
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    LazyColumn(
      state = listState,
      contentPadding = PaddingValues(bottom = if (selecting) 88.dp + navigationBarHeight() else bottomPadding),
      // 長押ししたまま指を動かすと、通ったプレイリストまでをまとめて選ぶ (曲の一覧と同じ)
      modifier = Modifier.dragToSelect(
        state = listState,
        keys = list.map { it.id },
        currentSelection = { selectedIds },
        onStart = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
        onChange = { selectedIds = it },
      ),
    ) {
      item {
        ListHeader(pluralStringResource(R.plurals.item_count, list.size, list.size)) {
          SortButton(sort != null) { showSort = true }
        }
      }
      item { IconRow(KanadeIcons.Plus, stringResource(R.string.playlist_new)) { creating = true } }
      items(list, key = { it.id }) { p ->
        val toggle = { selectedIds = if (p.id in selectedIds) selectedIds - p.id else selectedIds + p.id }
        GroupRow(
          title = p.name,
          subtitle = "${pluralStringResource(R.plurals.track_count, p.trackCount, p.trackCount)} · ${p.durationMs / 60_000}${stringResource(R.string.minutes_suffix)}",
          artUri = p.artUri ?: "",
          onClick = { if (selecting) toggle() else onOpen(p.id, p.name) },
          selected = if (selecting) p.id in selectedIds else null,
        )
      }
    }
    val chosen = list.filter { it.id in selectedIds }
    GroupSelectionBars(
      selected = chosen,
      allSelected = selectedIds.size == list.size,
      onSelectAll = { selectedIds = if (selectedIds.size == list.size) emptySet() else list.map { it.id }.toSet() },
      onClear = { selectedIds = emptySet() },
      sourceTitle = if (chosen.size == 1) chosen.first().name else stringResource(R.string.tab_playlists),
      keyOf = { "playlist:${it.id}" },
      load = { viewModel.tracks(it.id) },
      actions = actions,
      distinct = false,
      canAddToPlaylist = false,
      extra = listOf(
        BarAction(KanadeIcons.Delete, stringResource(R.string.action_delete)) {
          // 確かめずに削除し、「元に戻す」を出す (曲を外すときと同じ)
          val targets = chosen
          selectedIds = emptySet()
          scope.launch {
            val deleted = targets.mapNotNull { viewModel.delete(it.id) }
            if (deleted.isEmpty()) return@launch
            val message = if (deleted.size == 1) res.getString(R.string.message_playlist_deleted, deleted.first().playlist.name)
              else res.getQuantityString(R.plurals.message_playlists_deleted, deleted.size, deleted.size)
            showUndo(message, { deleted.forEach { viewModel.restore(it) } }, { deleted.forEach { viewModel.discard(it) } })
          }
        },
      ),
    )
  }

  if (showSort) {
    SortSheet(PlaylistListSort.entries.toTypedArray(), sort, PlaylistListSort.NAME,
      onSelect = { viewModel.setSort(it) }, onDismiss = { showSort = false })
  }
  if (creating) {
    PlaylistNameDialog(
      title = stringResource(R.string.playlist_new),
      initial = "",
      onConfirm = { name ->
        creating = false
        scope.launch {
          viewModel.create(name)
          showMessage(res.getString(R.string.message_playlist_created, name.trim()))
        }
      },
      onDismiss = { creating = false },
    )
  }
}

/** プレイリスト名の入力 */
@Composable
fun PlaylistNameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
  // 名前を変えるときは、カーソルを今の名前の末尾に置く (続けて書き足せるように)
  var name by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
  val focus = remember { FocusRequester() }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          singleLine = true,
          label = { Text(stringResource(R.string.playlist_name)) },
          modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
      }
      androidx.compose.runtime.LaunchedEffect(Unit) { focus.requestFocus() }
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(name.text) }, enabled = name.text.isNotBlank()) { Text(stringResource(R.string.action_ok)) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
  )
}

/** アイコンつきの1行 (新しいプレイリスト・自動のリスト) */
@Composable
private fun IconRow(icon: ImageVector, title: String, onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(72.dp)
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Box(
      Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer),
      contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
    Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
  }
}
