package me.mallows.kanade.ui.selection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.mallows.kanade.R
import me.mallows.kanade.core.data.db.TrackEntity
import me.mallows.kanade.ui.common.LocalBottomBarActive
import me.mallows.kanade.ui.common.LocalSnackbarHost
import me.mallows.kanade.ui.common.TrackRow
import me.mallows.kanade.ui.common.showMessage
import me.mallows.kanade.ui.icons.KanadeIcons

/** 曲の一覧に対する操作 */
interface TrackActions {
  /** [tracks]を再生順に並べ、[index]の曲から再生する */
  fun play(tracks: List<TrackEntity>, index: Int)
  fun playNext(tracks: List<TrackEntity>)
  fun addToQueue(tracks: List<TrackEntity>)
  fun addToPlaylist(tracks: List<TrackEntity>)
  /** プレイリストへの追加ができるか */
  val canAddToPlaylist: Boolean get() = true
}

/**
 * 長押しで選択モードに入り、そのまま指でなぞると範囲選択できる曲の一覧。
 * 選択中は下部に「再生・次に再生・キューに追加・プレイリストに追加」のバーを出す。
 */
@Composable
fun SelectableTrackList(
  tracks: List<TrackEntity>,
  playingMediaStoreId: Long?,
  actions: TrackActions,
  bottomPadding: Dp,
  modifier: Modifier = Modifier,
  numbered: Boolean = true,
  header: (@Composable () -> Unit)? = null,
) {
  var selected by rememberSaveable { mutableStateOf(setOf<Long>()) }
  val selecting = selected.isNotEmpty()
  val listState = rememberLazyListState()
  val haptic = LocalHapticFeedback.current
  val snackbar = LocalSnackbarHost.current
  val scope = rememberCoroutineScope()
  val barActive = LocalBottomBarActive.current

  // 一覧が変わって消えた曲は選択から外す
  val ids = remember(tracks) { tracks.map { it.id }.toSet() }
  if (!ids.containsAll(selected)) selected = selected intersect ids

  DisposableEffect(selecting) {
    barActive.value = selecting
    onDispose { barActive.value = false }
  }
  BackHandler(enabled = selecting) { selected = emptySet() }

  val selectedTracks = { tracks.filter { it.id in selected } }
  val clear = { selected = emptySet() }

  Box(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
      if (selecting) {
        SelectionTopBar(
          count = selected.size,
          allSelected = selected.size == tracks.size,
          onSelectAll = { selected = if (selected.size == tracks.size) emptySet() else ids },
          onClose = clear,
        )
      }
      LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = if (selecting) 88.dp else bottomPadding),
        modifier = Modifier
          .weight(1f)
          .dragToSelect(
            state = listState,
            tracks = tracks,
            currentSelection = { selected },
            onStart = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
            onChange = { selected = it },
          ),
      ) {
        if (header != null && !selecting) item(key = "header") { header() }
        itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
          val isSelected = track.id in selected
          TrackRow(
            track = track,
            number = if (numbered) index + 1 else null,
            playing = track.mediaStoreId == playingMediaStoreId,
            selectionMode = selecting,
            selected = isSelected,
            onClick = {
              if (selecting) {
                selected = if (isSelected) selected - track.id else selected + track.id
              } else {
                actions.play(tracks, index)
              }
            },
            onLongClick = null,
          )
        }
      }
    }
    if (selecting) {
      val n = selected.size
      val playNextDone = stringResource(R.string.message_play_next, n)
      val queueDone = stringResource(R.string.message_added_to_queue, n)
      SelectionBottomBar(
        modifier = Modifier.align(Alignment.BottomCenter),
        actions = listOfNotNull(
          BarAction(KanadeIcons.Play, stringResource(R.string.action_play)) {
            actions.play(selectedTracks(), 0); clear()
          },
          BarAction(KanadeIcons.PlayNext, stringResource(R.string.action_play_next)) {
            actions.playNext(selectedTracks()); clear()
            scope.launch { snackbar.showMessage(playNextDone) }
          },
          BarAction(KanadeIcons.AddToQueue, stringResource(R.string.action_add_to_queue)) {
            actions.addToQueue(selectedTracks()); clear()
            scope.launch { snackbar.showMessage(queueDone) }
          },
          if (actions.canAddToPlaylist) {
            BarAction(KanadeIcons.AddToPlaylist, stringResource(R.string.action_add_to_playlist)) {
              actions.addToPlaylist(selectedTracks()); clear()
            }
          } else null,
        ),
      )
    }
  }
}

/**
 * 長押しで選択を始め、そのまま指を動かすと通った範囲を選択する。
 * 長押しした曲が選択済みなら、なぞった範囲の選択を外す。
 */
fun Modifier.dragToSelect(
  state: LazyListState,
  tracks: List<TrackEntity>,
  currentSelection: () -> Set<Long>,
  onStart: () -> Unit,
  onChange: (Set<Long>) -> Unit,
): Modifier = pointerInput(tracks) {
  var anchor = -1
  var base = emptySet<Long>()
  var adding = true

  fun indexAt(offset: Offset): Int? {
    val item = state.layoutInfo.visibleItemsInfo.firstOrNull { offset.y.toInt() in it.offset until it.offset + it.size }
    val key = item?.key as? Long ?: return null
    return tracks.indexOfFirst { it.id == key }.takeIf { it >= 0 }
  }

  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    // 長押しになる前に指が動いたら通常のスクロールに任せる
    val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
    val i = indexAt(longPress.position) ?: return@awaitEachGesture
    anchor = i
    onStart()
    val id = tracks[i].id
    base = currentSelection()
    adding = id !in base
    onChange(if (adding) base + id else base - id)
    longPress.consume()

    // 長押しの後の指の動きは、一覧のスクロールより先に受け取って範囲選択に使う
    while (true) {
      val event = awaitPointerEvent(PointerEventPass.Initial)
      val change = event.changes.firstOrNull { it.id == down.id } ?: break
      if (!change.pressed) {
        change.consume()
        break
      }
      indexAt(change.position)?.let { j ->
        val range = tracks.subList(minOf(anchor, j), maxOf(anchor, j) + 1).map { it.id }.toSet()
        onChange(if (adding) base + range else base - range)
      }
      change.consume()
    }
    anchor = -1
  }
}
