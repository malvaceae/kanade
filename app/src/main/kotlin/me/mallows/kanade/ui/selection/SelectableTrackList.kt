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
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import me.mallows.kanade.ui.common.LocalPlaySource
import me.mallows.kanade.playback.PlaySource
import androidx.compose.foundation.gestures.scrollBy
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import me.mallows.kanade.ui.common.initialOf
import me.mallows.kanade.ui.common.FastScroller
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
  fun play(tracks: List<TrackEntity>, index: Int, source: PlaySource? = null)
  /** シャッフルをオンにして、ランダムな曲から再生する */
  fun shufflePlay(tracks: List<TrackEntity>, source: PlaySource? = null)
  /** プレイヤー画面を開く (再生中の曲をタップしたとき。最初から再生し直さない) */
  fun openPlayer()
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
  /** この一覧を再生元として記録する名前など */
  source: PlaySource,
  modifier: Modifier = Modifier,
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

  // 再生元の一覧と同じときだけ「再生中」を出す (別の一覧で同じ曲を見たときは出さない)
  val playingId = if (LocalPlaySource.current?.key == source.key) playingMediaStoreId else null

  Box(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
      LazyColumn(
        state = listState,
        // 選択中は下の操作バー (と画面下のナビゲーションバー) に最後の曲が隠れないようにする
        contentPadding = PaddingValues(bottom = if (selecting) 88.dp + navigationBarHeight() else bottomPadding),
        modifier = Modifier
          .weight(1f)
          .dragToSelect(
            state = listState,
            keys = tracks.map { it.id },
            currentSelection = { selected },
            onStart = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
            onChange = { selected = it },
          ),
      ) {
        // 見出しは選択中も残す (消すと一覧がずれる)。選択中のバーは上に重ねて出す
        if (header != null) item(key = "header") { header() }
        itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
          val isSelected = track.id in selected
          TrackRow(
            track = track,
            number = index + 1,
            playing = track.mediaStoreId == playingId,
            selectionMode = selecting,
            selected = isSelected,
            onClick = {
              if (selecting) {
                selected = if (isSelected) selected - track.id else selected + track.id
              } else {
                // 再生中の曲なら最初からにせず、そうでなければこの一覧として再生し、どちらもプレイヤー画面を開く
                if (track.mediaStoreId != playingId) actions.play(tracks, index, source)
                actions.openPlayer()
              }
            },
            onLongClick = null,
          )
        }
      }
    }
    if (!selecting) {
      val offset = if (header != null) 1 else 0
      FastScroller(
        state = listState,
        label = { i -> initialOf(tracks.getOrNull(i - offset)?.title) },
        modifier = Modifier.align(Alignment.TopEnd),
        bottomPadding = bottomPadding,
      )
    }
    if (selecting) {
      SelectionTopBar(
        count = selected.size,
        allSelected = selected.size == tracks.size,
        onSelectAll = { selected = if (selected.size == tracks.size) emptySet() else ids },
        onClose = clear,
        modifier = Modifier.align(Alignment.TopCenter).background(MaterialTheme.colorScheme.background),
      )
    }
    if (selecting) {
      val n = selected.size
      val playNextDone = stringResource(R.string.message_play_next, n)
      val queueDone = stringResource(R.string.message_added_to_queue, n)
      SelectionBottomBar(
        modifier = Modifier.align(Alignment.BottomCenter),
        actions = listOfNotNull(
          BarAction(KanadeIcons.Play, stringResource(R.string.action_play)) {
            actions.play(selectedTracks(), 0, PlaySource("selection:${source.key}", source.title)); clear()
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
  keys: List<Long>,
  currentSelection: () -> Set<Long>,
  onStart: () -> Unit,
  onChange: (Set<Long>) -> Unit,
  /** 行の右端のこの幅は対象外 (ドラッグハンドル用) */
  excludeEnd: Dp = 0.dp,
): Modifier = pointerInput(keys) {
  val pointer = this
  coroutineScope {
    val scope = this
    var anchor = -1
    var base = emptySet<Long>()
    var adding = true
    var lastPosition = Offset.Zero
    var autoScroll: Job? = null
    val edge = 64.dp.toPx()

    fun indexAt(offset: Offset): Int? {
      val item = state.layoutInfo.visibleItemsInfo.firstOrNull { offset.y.toInt() in it.offset until it.offset + it.size }
      val key = item?.key as? Long ?: return null
      return keys.indexOf(key).takeIf { it >= 0 }
    }

    fun select(position: Offset) {
      indexAt(position)?.let { j ->
        val range = keys.subList(minOf(anchor, j), maxOf(anchor, j) + 1).toSet()
        onChange(if (adding) base + range else base - range)
      }
    }

    /** 指が上端・下端の帯にあるときのスクロールの速さ (1フレームあたりのpx)。端に近いほど速い */
    fun speedAt(y: Float): Float = when {
      y < edge -> -(edge - y.coerceAtLeast(0f)) / edge * 40f
      y > pointer.size.height - edge -> (y.coerceAtMost(pointer.size.height.toFloat()) - (pointer.size.height - edge)) / edge * 40f
      else -> 0f
    }

    pointer.awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false)
      if (down.position.x > size.width - excludeEnd.toPx()) return@awaitEachGesture
      // 長押しになる前に指が動いたら通常のスクロールに任せる
      val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
      val i = indexAt(longPress.position) ?: return@awaitEachGesture
      anchor = i
      onStart()
      val id = keys[i]
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
        lastPosition = change.position
        select(lastPosition)
        change.consume()
        // 指が端にあるあいだは一覧を自動でスクロールし、指の下に来た曲まで選択を広げる
        if (speedAt(lastPosition.y) != 0f && autoScroll?.isActive != true) {
          autoScroll = scope.launch {
            while (isActive) {
              val speed = speedAt(lastPosition.y)
              if (speed == 0f) break
              state.scrollBy(speed)
              select(lastPosition)
              delay(16)
            }
          }
        }
      }
      autoScroll?.cancel()
      anchor = -1
    }
  }
}

/** 画面下のナビゲーションバーの高さ */
@Composable
fun navigationBarHeight(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
