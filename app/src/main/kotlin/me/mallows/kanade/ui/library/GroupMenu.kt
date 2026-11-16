package me.mallows.kanade.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.mallows.kanade.R
import me.mallows.kanade.core.data.library.GroupKind
import me.mallows.kanade.playback.PlaySource
import me.mallows.kanade.ui.common.LocalShowMessage
import me.mallows.kanade.ui.icons.KanadeIcons
import me.mallows.kanade.ui.selection.TrackActions

/** 長押しで操作するアルバム・アーティスト・フォルダー・ジャンル */
data class GroupTarget(val kind: GroupKind, val key: String?, val title: String)

/** アルバムなどを長押ししたときのメニュー。中の全曲をまとめて再生・追加する */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupMenuSheet(target: GroupTarget, viewModel: LibraryViewModel, actions: TrackActions, onDismiss: () -> Unit) {
  val scope = rememberCoroutineScope()
  val showMessage = LocalShowMessage.current
  val res = LocalResources.current
  val source = PlaySource("group:${target.kind.name}:${target.key}", target.title)
  // メニューを閉じた後も処理が続くよう、閉じる前に曲を読んでから実行する
  fun withTracks(block: (List<me.mallows.kanade.core.data.db.TrackEntity>) -> Unit) {
    scope.launch {
      val tracks = viewModel.groupTracks(target.kind, target.key)
      if (tracks.isNotEmpty()) block(tracks)
      onDismiss()
    }
  }
  val items = listOf(
    Triple(KanadeIcons.Play, R.string.action_play) { withTracks { actions.play(it, 0, source) } },
    Triple(KanadeIcons.Shuffle, R.string.action_shuffle_play) { withTracks { actions.shufflePlay(it, source) } },
    Triple(KanadeIcons.PlayNext, R.string.action_play_next) {
      withTracks { actions.playNext(it); showMessage(res.getString(R.string.message_play_next, it.size)) }
    },
    Triple(KanadeIcons.AddToQueue, R.string.action_add_to_queue) {
      withTracks { actions.addToQueue(it); showMessage(res.getString(R.string.message_added_to_queue, it.size)) }
    },
    Triple(KanadeIcons.AddToPlaylist, R.string.action_add_to_playlist_long) { withTracks { actions.addToPlaylist(it) } },
  )
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
    Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
      Text(
        target.title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
      )
      items.forEach { (icon, label, action) ->
        Row(
          Modifier.fillMaxWidth().height(56.dp).clickable(onClick = action).padding(horizontal = 24.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 20.dp))
        }
      }
    }
  }
}
