package me.mallows.kanade.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.mallows.kanade.R
import me.mallows.kanade.core.data.db.TrackEntity
import me.mallows.kanade.ui.common.LocalSnackbarHost
import me.mallows.kanade.ui.common.TrackArtwork
import me.mallows.kanade.ui.common.showMessage
import me.mallows.kanade.ui.icons.KanadeIcons

/** 曲をどのプレイリストに入れるか選ぶシート。重複があれば確認する */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
  tracks: List<TrackEntity>,
  onDismiss: () -> Unit,
  /** 追加し終えたときのメッセージを出す (シートを閉じた後も出せるよう、呼び出し元で出す) */
  onMessage: (String) -> Unit,
  viewModel: PlaylistsViewModel = hiltViewModel(),
) {
  val playlists by viewModel.playlists.collectAsStateWithLifecycle()
  val scope = rememberCoroutineScope()
  val snackbar = LocalSnackbarHost.current
  var creating by remember { mutableStateOf(false) }
  /** 重複の確認中: プレイリストIDと名前、重複している曲 */
  var confirm by remember { mutableStateOf<Triple<Long, String, Set<Long>>?>(null) }
  val ids = tracks.map { it.id }
  val res = LocalResources.current
  val addedMessage = { name: String, n: Int -> res.getString(R.string.message_added_to_playlist, n, name) }

  fun addTo(id: Long, name: String, skip: Set<Long>) {
    val toAdd = ids.filter { it !in skip }
    scope.launch {
      viewModel.add(id, toAdd)
      onMessage(addedMessage(name, toAdd.size))
      onDismiss()
    }
  }

  fun choose(id: Long, name: String) {
    scope.launch {
      val dup = viewModel.duplicates(id, ids)
      if (dup.isEmpty()) addTo(id, name, emptySet()) else confirm = Triple(id, name, dup)
    }
  }

  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
    Text(
      pluralStringResource(R.plurals.add_to_playlist_title, tracks.size, tracks.size),
      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
    LazyColumn(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
      item {
        Row(
          Modifier.fillMaxWidth().height(64.dp).clickable { creating = true }.padding(horizontal = 24.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) { Icon(KanadeIcons.Plus, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
          Text(stringResource(R.string.playlist_new), style = MaterialTheme.typography.bodyLarge)
        }
      }
      items(playlists.orEmpty(), key = { it.id }) { p ->
        Row(
          Modifier.fillMaxWidth().height(64.dp).clickable { choose(p.id, p.name) }.padding(horizontal = 24.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          TrackArtwork(uri = p.artUri ?: "", size = 44.dp, cornerRadius = 8.dp)
          Text(p.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
          Text(
            pluralStringResource(R.plurals.track_count, p.trackCount, p.trackCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }

  if (creating) {
    PlaylistNameDialog(
      title = stringResource(R.string.playlist_new),
      initial = "",
      onConfirm = { name ->
        creating = false
        scope.launch {
          val id = viewModel.create(name)
          addTo(id, name.trim(), emptySet())
        }
      },
      onDismiss = { creating = false },
    )
  }

  confirm?.let { (id, name, dup) ->
    AlertDialog(
      onDismissRequest = { confirm = null },
      title = { Text(stringResource(R.string.duplicate_title)) },
      text = { Text(stringResource(R.string.duplicate_body, dup.size, name)) },
      confirmButton = {
        TextButton(onClick = { confirm = null; addTo(id, name, dup) }) { Text(stringResource(R.string.duplicate_skip)) }
      },
      dismissButton = {
        TextButton(onClick = { confirm = null; addTo(id, name, emptySet()) }) { Text(stringResource(R.string.duplicate_add_anyway)) }
      },
    )
  }
}
