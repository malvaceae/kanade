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
  bottomPadding: Dp,
  viewModel: PlaylistsViewModel = hiltViewModel(),
) {
  val playlists by viewModel.playlists.collectAsStateWithLifecycle()
  var creating by rememberSaveable { mutableStateOf(false) }
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
      )
    }
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
