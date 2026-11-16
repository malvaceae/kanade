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

/** 画面から再生サービスを操作する窓口。MediaController経由で通知やBluetoothと同じ経路を使う */
@Singleton
class PlayerConnection @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  private val scope = MainScope()
  private val _state = MutableStateFlow(PlayerState())
  val state: StateFlow<PlayerState> = _state.asStateFlow()

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
          override fun onEvents(player: Player, events: Player.Events) = publish(player)
        })
        publish(c)
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
