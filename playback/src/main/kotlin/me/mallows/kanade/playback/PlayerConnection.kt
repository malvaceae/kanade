package me.mallows.kanade.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/** 画面に見せる再生状態 */
data class PlayerState(
  val connected: Boolean = false,
  /** 再生中の曲のMediaStore ID */
  val mediaStoreId: Long? = null,
  val title: String? = null,
  val artist: String? = null,
  val album: String? = null,
  /** 曲に埋め込まれた画像 */
  val artworkData: ByteArray? = null,
  val isPlaying: Boolean = false,
  val positionMs: Long = 0,
  val durationMs: Long = 0,
  val shuffle: Boolean = false,
  val repeatMode: Int = Player.REPEAT_MODE_OFF,
  val hasNext: Boolean = false,
  val hasPrevious: Boolean = false,
  /** 再生速度 (1が等速) */
  val speed: Float = 1f,
) {
  val hasMedia: Boolean get() = mediaStoreId != null
}

/** キューの1曲 */
data class QueueItem(
  val mediaStoreId: Long,
  val title: String,
  val artist: String?,
  /** 「次に再生」「キューに追加」で入れた曲 */
  val queued: Boolean,
  /** プレイヤー上の位置 (シャッフル中は表示の順と違う) */
  val index: Int = -1,
)

/** 再生キュー。[upcoming]は今の曲の後ろに並んでいる曲 (プレイヤー上の並び順) */
data class QueueState(
  val current: QueueItem? = null,
  val currentIndex: Int = 0,
  val upcoming: List<QueueItem> = emptyList(),
  val shuffle: Boolean = false,
)

/**
 * 再生元の一覧。[key] で一覧を見分け (「再生中」の表示はこの一覧の中だけに出す)、[title] はプレイヤー画面の上部に出す。
 * 例: key = "tracks" (すべての曲)、"group:ALBUM:12" (アルバム)、"playlist:3" (プレイリスト)
 */
data class PlaySource(val key: String, val title: String?)

/** 画面から再生サービスを操作する窓口。MediaController経由で通知やBluetoothと同じ経路を使う */
@Singleton
class PlayerConnection @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  private val scope = MainScope()
  private val _state = MutableStateFlow(PlayerState())
  val state: StateFlow<PlayerState> = _state.asStateFlow()

  private val _source = MutableStateFlow<PlaySource?>(null)
  /** 今の再生キューを、どの一覧から再生したか。アプリを開き直した後などは分からないのでnull */
  val source: StateFlow<PlaySource?> = _source.asStateFlow()

  private val _queue = MutableStateFlow(QueueState())
  val queue: StateFlow<QueueState> = _queue.asStateFlow()

  private var controller: CompletableDeferred<MediaController>? = null

  /** 再生サービスにつなぐ。何度呼んでもよい */
  fun connect() {
    if (controller != null) return
    val deferred = CompletableDeferred<MediaController>()
    controller = deferred
    scope.launch {
      try {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val c = MediaController.Builder(context, token).buildAsync().await()
        c.addListener(object : Player.Listener {
          override fun onEvents(player: Player, events: Player.Events) {
            publish(player)
            if (events.containsAny(
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
              )
            ) publishQueue(player)
          }
        })
        publish(c)
        publishQueue(c)
        deferred.complete(c)
        // 再生位置の表示を進める
        while (isActive) {
          delay(500)
          if (c.isPlaying) publish(c)
        }
      } catch (e: Exception) {
        Timber.e(e, "再生サービスにつなげませんでした")
        controller = null
        deferred.completeExceptionally(e)
      }
    }
  }

  /** [mediaStoreIds]を再生順に並べ、[startIndex]の曲から再生する */
  /** [shuffle] ならシャッフルをオンにする (並びはそのまま。解除すれば元の順に戻る) */
  fun playTracks(mediaStoreIds: List<Long>, startIndex: Int, shuffle: Boolean = false, source: PlaySource? = null) = withController {
    _source.value = source
    it.setMediaItems(mediaStoreIds.map(::mediaItemRequest), startIndex, 0L)
    if (shuffle) it.shuffleModeEnabled = true
    it.prepare()
    it.play()
  }

  /** 今の曲の直後に入れる */
  fun playNext(mediaStoreIds: List<Long>) = withController {
    if (it.mediaItemCount == 0) {
      it.setMediaItems(mediaStoreIds.map { id -> mediaItemRequest(id, queued = true) })
      it.prepare()
      return@withController
    }
    it.addMediaItems(it.currentMediaItemIndex + 1, mediaStoreIds.map { id -> mediaItemRequest(id, queued = true) })
  }

  /** 「次に再生」「キューに追加」で入れた曲の後ろに入れる */
  fun addToQueue(mediaStoreIds: List<Long>) = withController {
    if (it.mediaItemCount == 0) {
      it.setMediaItems(mediaStoreIds.map { id -> mediaItemRequest(id, queued = true) })
      it.prepare()
      return@withController
    }
    var index = it.currentMediaItemIndex + 1
    while (index < it.mediaItemCount && it.getMediaItemAt(index).isQueued) index++
    it.addMediaItems(index, mediaStoreIds.map { id -> mediaItemRequest(id, queued = true) })
  }

  fun togglePlayPause() = withController {
    if (it.isPlaying) {
      it.pause()
    } else {
      if (it.playbackState == Player.STATE_IDLE) it.prepare()
      if (it.playbackState == Player.STATE_ENDED) it.seekToDefaultPosition()
      it.play()
    }
  }

  /** 次の曲へ。リピートなしで最後の曲なら、先頭の曲に戻して一時停止する (何も起きないと壊れて見えるため) */
  fun next() = withController {
    if (!it.hasNextMediaItem() && it.repeatMode == Player.REPEAT_MODE_OFF && it.mediaItemCount > 0) {
      val first = it.currentTimeline.getFirstWindowIndex(it.shuffleModeEnabled).coerceAtLeast(0)
      it.seekTo(first, 0L)
      it.pause()
    } else {
      it.seekToNext()
    }
  }

  /** 再生位置が3秒を過ぎていれば曲の頭に、そうでなければ前の曲に戻る */
  fun previous() = withController { it.seekToPrevious() }

  fun seekTo(positionMs: Long) = withController {
    it.seekTo(positionMs)
    publish(it)
  }

  fun setSpeed(speed: Float) = withController { it.setPlaybackSpeed(speed) }
  fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

  /** オフ → 全曲 → 1曲 → オフ */
  fun cycleRepeat() = withController {
    it.repeatMode = when (it.repeatMode) {
      Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
      Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
      else -> Player.REPEAT_MODE_OFF
    }
  }

  /** スリープタイマーの状態。[until]は止まる時刻 (ミリ秒)、[endOfTrack]は曲の終わりで止める指定 */
  data class Sleep(val until: Long? = null, val endOfTrack: Boolean = false) {
    val active: Boolean get() = until != null || endOfTrack
  }

  private val _sleep = MutableStateFlow(Sleep())
  val sleep: StateFlow<Sleep> = _sleep.asStateFlow()

  /** [minutes]分後に止める。[endOfTrack]なら今の曲の終わりで止める。両方なしなら解除 */
  fun setSleepTimer(minutes: Int, endOfTrack: Boolean) = withController {
    it.sendCustomCommand(SleepCommand.command, SleepCommand.args(minutes, endOfTrack))
    _sleep.value = when {
      endOfTrack -> Sleep(endOfTrack = true)
      minutes > 0 -> Sleep(until = System.currentTimeMillis() + minutes * 60_000L)
      else -> Sleep()
    }
  }

  /** キューの[index]番目 (プレイヤー上の位置) から再生する */
  fun playAt(index: Int) = withController {
    it.seekToDefaultPosition(index)
    it.play()
  }

  /**
   * 今の曲の後ろの曲を[order]の並びにする。[order]は今の並びでの位置 (0始まり) を並べたもの。
   * 元に戻すための並びを返す。
   */
  suspend fun reorderUpcoming(order: List<Int>): List<Int>? {
    val c = awaitController() ?: return null
    val base = c.currentMediaItemIndex + 1
    val n = c.mediaItemCount - base
    if (order.size != n || order.sorted() != (0 until n).toList()) return null
    // working[p] = 位置pに今ある曲の、元の位置
    val working = (0 until n).toMutableList()
    order.forEachIndexed { p, original ->
      val from = working.indexOf(original)
      if (from != p) {
        c.moveMediaItem(base + from, base + p)
        working.add(p, working.removeAt(from))
      }
    }
    val inverse = IntArray(n)
    order.forEachIndexed { p, original -> inverse[original] = p }
    return inverse.toList()
  }

  /** 今の曲の後ろの曲のうち、[positions] (0始まり) の曲を外す。元に戻すための内容を返す */
  suspend fun removeUpcoming(positions: Set<Int>): List<Pair<Int, QueueItem>> {
    val c = awaitController() ?: return emptyList()
    val items = _queue.value.upcoming
    val removed = positions.sorted().mapNotNull { p -> items.getOrNull(p)?.let { p to it } }
    // プレイヤー上の位置の後ろから消すと、位置がずれない
    removed.sortedByDescending { it.second.index }.forEach { (_, item) -> c.removeMediaItem(item.index) }
    return removed
  }

  /** [removeUpcoming]で外した曲を元の位置に戻す */
  suspend fun restoreUpcoming(removed: List<Pair<Int, QueueItem>>) {
    val c = awaitController() ?: return
    // 前から元の位置に入れ直す
    removed.sortedBy { it.second.index }.forEach { (_, item) ->
      c.addMediaItem(item.index.coerceAtMost(c.mediaItemCount), mediaItemRequest(item.mediaStoreId, item.queued))
    }
  }

  private suspend fun awaitController(): MediaController? {
    connect()
    return runCatching { controller?.await() }.getOrNull()
  }

  private fun publishQueue(player: Player) {
    val index = player.currentMediaItemIndex
    fun itemAt(i: Int): QueueItem {
      val item = player.getMediaItemAt(i)
      return QueueItem(
        mediaStoreId = item.mediaId.toLongOrNull() ?: -1,
        title = item.mediaMetadata.title?.toString().orEmpty(),
        artist = item.mediaMetadata.artist?.toString(),
        queued = item.isQueued,
        index = i,
      )
    }
    val count = player.mediaItemCount
    // シャッフル中は、実際に流れる順 (シャッフルの順) で並べる
    val upcomingIndices = if (player.shuffleModeEnabled && count > 0) {
      val timeline = player.currentTimeline
      generateSequence(timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true).takeIf { it != C.INDEX_UNSET }) {
        timeline.getNextWindowIndex(it, Player.REPEAT_MODE_OFF, true).takeIf { n -> n != C.INDEX_UNSET }
      }.toList()
    } else {
      ((index + 1) until count).toList()
    }
    _queue.value = QueueState(
      current = if (count > 0) itemAt(index) else null,
      currentIndex = index,
      upcoming = upcomingIndices.map(::itemAt),
      shuffle = player.shuffleModeEnabled,
    )
  }

  private fun withController(block: (MediaController) -> Unit) {
    connect()
    val deferred = controller ?: return
    scope.launch {
      runCatching { deferred.await() }.onSuccess(block)
    }
  }

  private fun publish(player: Player) {
    // スリープタイマーで止まった後は表示を消す
    val sleep = _sleep.value
    if (sleep.until != null && System.currentTimeMillis() >= sleep.until) _sleep.value = Sleep()
    if (sleep.endOfTrack && !player.playWhenReady) _sleep.value = Sleep()
    val metadata = player.mediaMetadata
    _state.value = PlayerState(
      connected = true,
      mediaStoreId = player.currentMediaItem?.mediaId?.toLongOrNull(),
      title = metadata.title?.toString(),
      artist = metadata.artist?.toString(),
      album = metadata.albumTitle?.toString(),
      artworkData = metadata.artworkData,
      isPlaying = player.isPlaying,
      positionMs = player.currentPosition,
      durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: 0,
      shuffle = player.shuffleModeEnabled,
      repeatMode = player.repeatMode,
      hasNext = player.hasNextMediaItem(),
      hasPrevious = player.hasPreviousMediaItem(),
      speed = player.playbackParameters.speed,
    )
  }
}
