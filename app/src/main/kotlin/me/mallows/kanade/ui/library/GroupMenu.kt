package me.mallows.kanade.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import me.mallows.kanade.R
import me.mallows.kanade.core.data.db.TrackEntity
import me.mallows.kanade.core.data.library.GroupKind
import me.mallows.kanade.playback.PlaySource
import me.mallows.kanade.ui.common.LocalBottomBarActive
import me.mallows.kanade.ui.common.LocalShowMessage
import me.mallows.kanade.ui.icons.KanadeIcons
import me.mallows.kanade.ui.selection.BarAction
import me.mallows.kanade.ui.selection.SelectionBottomBar
import me.mallows.kanade.ui.selection.SelectionTopBar
import me.mallows.kanade.ui.selection.TrackActions

/** アルバム・アーティスト・フォルダ・ジャンルの1つ */
data class GroupTarget(val kind: GroupKind, val key: String?, val title: String)

/**
 * アルバムなどの複数選択の上下のバー。選んだものの中の全曲を、一覧の順にまとめて再生・追加する。
 * [selected]は一覧の順に並べて渡す
 */
@Composable
fun BoxScope.GroupSelectionBars(
  selected: List<GroupTarget>,
  allSelected: Boolean,
  onSelectAll: () -> Unit,
  onClear: () -> Unit,
  /** 再生元として出す名前 (「アルバム」など) */
  sourceTitle: String,
  load: suspend (GroupTarget) -> List<TrackEntity>,
  actions: TrackActions,
) {
  val barActive = LocalBottomBarActive.current
  val selecting = selected.isNotEmpty()
  DisposableEffect(selecting) {
    barActive.value = selecting
    onDispose { barActive.value = false }
  }
  BackHandler(enabled = selecting) { onClear() }
  if (!selecting) return

  val scope = rememberCoroutineScope()
  val showMessage = LocalShowMessage.current
  val res = LocalResources.current
  val source = PlaySource("selection:groups:" + selected.joinToString(",") { "${it.kind}:${it.key}" }, sourceTitle)
  // 選択を解いた後も処理が続くよう、先に曲を読んでから実行する
  fun withTracks(block: (List<TrackEntity>) -> Unit) {
    val targets = selected
    onClear()
    scope.launch {
      val tracks = targets.flatMap { load(it) }.distinctBy { it.id }
      if (tracks.isNotEmpty()) block(tracks)
    }
  }
  SelectionTopBar(
    count = selected.size,
    allSelected = allSelected,
    onSelectAll = onSelectAll,
    onClose = onClear,
    text = stringResource(R.string.selection_count_items, selected.size),
    modifier = Modifier.align(Alignment.TopCenter).background(MaterialTheme.colorScheme.background),
  )
  SelectionBottomBar(
    modifier = Modifier.align(Alignment.BottomCenter),
    actions = listOfNotNull(
      BarAction(KanadeIcons.Play, stringResource(R.string.action_play)) { withTracks { actions.play(it, 0, source) } },
      BarAction(KanadeIcons.Shuffle, stringResource(R.string.action_shuffle)) { withTracks { actions.shufflePlay(it, source) } },
      BarAction(KanadeIcons.PlayNext, stringResource(R.string.action_play_next)) {
        withTracks { actions.playNext(it); showMessage(res.getString(R.string.message_play_next, it.size)) }
      },
      BarAction(KanadeIcons.AddToQueue, stringResource(R.string.action_add_to_queue)) {
        withTracks { actions.addToQueue(it); showMessage(res.getString(R.string.message_added_to_queue, it.size)) }
      },
      if (actions.canAddToPlaylist) {
        BarAction(KanadeIcons.AddToPlaylist, stringResource(R.string.action_add_to_playlist)) { withTracks { actions.addToPlaylist(it) } }
      } else null,
    ),
  )
}
