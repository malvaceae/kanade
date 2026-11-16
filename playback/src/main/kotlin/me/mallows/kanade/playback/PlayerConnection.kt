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
)

/** 再生キュー。[upcoming]は今の曲の後ろに並んでいる曲 (プレイヤー上の並び順) */
data class QueueState(
  val current: QueueItem? = null,
  val currentIndex: Int = 0,
  val upcoming: List<QueueItem> = emptyList(),
  val shuffle: Boolean = false,
)

/** 画面から再生サービスを操作する窓口。MediaController経由で通知やBluetoothと同じ経路を使う */
@Singleton
class PlayerConnection @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  private val scope = MainScope()
  private val _state = MutableStateFlow(PlayerState())
  val state: StateFlow<PlayerState> = _state.asStateFlow()

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
  fun playTracks(mediaStoreIds: List<Long>, startIndex: Int) = withController {
    it.setMediaItems(mediaStoreIds.map(::mediaItemRequest), startIndex, 0L)
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

  fun next() = withController { it.seekToNext() }

  /** 再生位置が3秒を過ぎていれば曲の頭に、そうでなければ前の曲に戻る */
  fun previous() = withController { it.seekToPrevious() }

  fun seekTo(positionMs: Long) = withController {
    it.seekTo(positionMs)
    publish(it)
  }

  fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

  /** オフ → 全曲 → 1曲 → オフ */
  fun cycleRepeat() = withController {
    it.repeatMode = when (it.repeatMode) {
      Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
      Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
      else -> Player.REPEAT_MODE_OFF
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
    val base = c.currentMediaItemIndex + 1
    val items = _queue.value.upcoming
    val removed = positions.sorted().mapNotNull { p -> items.getOrNull(p)?.let { p to it } }
    // 後ろから消すと位置がずれない
    removed.asReversed().forEach { (p, _) -> c.removeMediaItem(base + p) }
    return removed
  }

  /** [removeUpcoming]で外した曲を元の位置に戻す */
  suspend fun restoreUpcoming(removed: List<Pair<Int, QueueItem>>) {
    val c = awaitController() ?: return
    val base = c.currentMediaItemIndex + 1
    removed.forEach { (p, item) ->
      c.addMediaItem((base + p).coerceAtMost(c.mediaItemCount), mediaItemRequest(item.mediaStoreId, item.queued))
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
      )
    }
    val count = player.mediaItemCount
    _queue.value = QueueState(
      current = if (count > 0) itemAt(index) else null,
      currentIndex = index,
      upcoming = ((index + 1) until count).map(::itemAt),
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
    )
  }
}
