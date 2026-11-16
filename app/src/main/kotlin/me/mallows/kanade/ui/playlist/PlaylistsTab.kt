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
import me.mallows.kanade.ui.selection.TrackActions
import me.mallows.kanade.core.data.db.PlaylistSummary
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import me.mallows.kanade.ui.common.showMessage
import me.mallows.kanade.ui.common.LocalSnackbarHost
import androidx.compose.ui.platform.LocalResources
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.mallows.kanade.R
import me.mallows.kanade.ui.common.GroupRow
import me.mallows.kanade.ui.common.ListHeader
import me.mallows.kanade.ui.icons.KanadeIcons
import me.mallows.kanade.ui.library.Loading

/** ライブラリの「プレイリスト」タブ */
@Composable
fun PlaylistsTab(
  onOpen: (id: Long, name: String) -> Unit,
  actions: TrackActions,
  bottomPadding: Dp,
  viewModel: PlaylistsViewModel = hiltViewModel(),
) {
  val playlists by viewModel.playlists.collectAsStateWithLifecycle()
  var creating by rememberSaveable { mutableStateOf(false) }
  /** 長押しでメニューを出しているプレイリスト */
  var menuFor by remember { mutableStateOf<PlaylistSummary?>(null) }
  var renaming by remember { mutableStateOf<PlaylistSummary?>(null) }
  var deleting by remember { mutableStateOf<PlaylistSummary?>(null) }
  var exporting by remember { mutableStateOf<PlaylistSummary?>(null) }
  val scope = rememberCoroutineScope()
  val snackbar = LocalSnackbarHost.current
  val res = LocalResources.current
  val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) {
      scope.launch {
        runCatching { viewModel.importM3u(uri) }
          .onSuccess { r ->
            val base = res.getQuantityString(R.plurals.message_m3u_imported, r.added, r.added, r.name)
            val missing = if (r.missing > 0) res.getString(R.string.message_m3u_missing, r.missing) else ""
            snackbar.showMessage(base + missing)
          }
          .onFailure { snackbar.showMessage(res.getString(R.string.message_m3u_failed)) }
      }
    }
  }
  val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
    val target = exporting
    exporting = null
    if (uri != null && target != null) {
      scope.launch {
        val message = runCatching { viewModel.exportM3u(target.id, uri) }
          .fold({ n -> res.getQuantityString(R.plurals.message_m3u_exported, n, n) }, { res.getString(R.string.message_m3u_failed) })
        snackbar.showMessage(message)
      }
    }
  }
  val list = playlists ?: return Loading()

  LazyColumn(contentPadding = PaddingValues(bottom = bottomPadding)) {
    item { ListHeader(pluralStringResource(R.plurals.item_count, list.size, list.size)) }
    item { IconRow(KanadeIcons.Plus, stringResource(R.string.playlist_new)) { creating = true } }
    item { IconRow(KanadeIcons.AddToPlaylist, stringResource(R.string.playlist_import_m3u)) { importLauncher.launch(M3U_TYPES) } }
    items(list, key = { it.id }) { p ->
      GroupRow(
        title = p.name,
        subtitle = "${pluralStringResource(R.plurals.track_count, p.trackCount, p.trackCount)} · ${p.durationMs / 60_000}${stringResource(R.string.minutes_suffix)}",
        artUri = p.artUri ?: "",
        onClick = { onOpen(p.id, p.name) },
        onLongClick = { menuFor = p },
      )
    }
  }

  menuFor?.let { p ->
    PlaylistMenuSheet(
      name = p.name,
      onDismiss = { menuFor = null },
      onPlay = { scope.launch { viewModel.tracks(p.id).takeIf { it.isNotEmpty() }?.let { actions.play(it, 0) } } },
      onPlayNext = {
        scope.launch {
          val tracks = viewModel.tracks(p.id)
          actions.playNext(tracks)
          snackbar.showMessage(res.getString(R.string.message_play_next, tracks.size))
        }
      },
      onAddToQueue = {
        scope.launch {
          val tracks = viewModel.tracks(p.id)
          actions.addToQueue(tracks)
          snackbar.showMessage(res.getString(R.string.message_added_to_queue, tracks.size))
        }
      },
      onRename = { renaming = p },
      onExport = { exporting = p; exportLauncher.launch("${p.name}.m3u8") },
      onDelete = { deleting = p },
    )
  }
  renaming?.let { p ->
    PlaylistNameDialog(
      title = stringResource(R.string.playlist_rename),
      initial = p.name,
      onConfirm = { name -> renaming = null; scope.launch { viewModel.rename(p.id, name) } },
      onDismiss = { renaming = null },
    )
  }
  deleting?.let { p ->
    AlertDialog(
      onDismissRequest = { deleting = null },
      title = { Text(stringResource(R.string.playlist_delete)) },
      text = { Text(stringResource(R.string.playlist_delete_confirm, p.name)) },
      confirmButton = {
        TextButton(onClick = { deleting = null; scope.launch { viewModel.delete(p.id) } }) { Text(stringResource(R.string.action_delete)) }
      },
      dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }

  if (creating) {
    PlaylistNameDialog(
      title = stringResource(R.string.playlist_new),
      initial = "",
      onConfirm = { name ->
        creating = false
        scope.launch { viewModel.create(name) }
      },
      onDismiss = { creating = false },
    )
  }
}

/** プレイリスト名の入力 */
@Composable
fun PlaylistNameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
  var name by rememberSaveable { mutableStateOf(initial) }
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
      TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_ok)) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
  )
}

/** M3U・M3U8として選べるファイルの種類 (端末によって種類の付き方が違うため広めにとる) */
private val M3U_TYPES = arrayOf(
  "audio/x-mpegurl", "audio/mpegurl", "application/x-mpegurl", "application/vnd.apple.mpegurl",
  "text/plain", "application/octet-stream",
)

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

/** プレイリストを長押ししたときのメニュー */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistMenuSheet(
  name: String,
  onDismiss: () -> Unit,
  onPlay: () -> Unit,
  onPlayNext: () -> Unit,
  onAddToQueue: () -> Unit,
  onRename: () -> Unit,
  onExport: () -> Unit,
  onDelete: () -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
      Text(
        name,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        maxLines = 1,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
      )
      val items = listOf(
        Triple(KanadeIcons.Play, R.string.action_play, onPlay),
        Triple(KanadeIcons.PlayNext, R.string.action_play_next, onPlayNext),
        Triple(KanadeIcons.AddToQueue, R.string.action_add_to_queue, onAddToQueue),
        Triple(KanadeIcons.Edit, R.string.playlist_rename, onRename),
        Triple(KanadeIcons.AddToPlaylist, R.string.playlist_export_m3u, onExport),
        Triple(KanadeIcons.Delete, R.string.playlist_delete, onDelete),
      )
      items.forEach { (icon, label, action) ->
        Row(
          Modifier.fillMaxWidth().height(56.dp).clickable { onDismiss(); action() }.padding(horizontal = 24.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 20.dp))
        }
      }
    }
  }
}
