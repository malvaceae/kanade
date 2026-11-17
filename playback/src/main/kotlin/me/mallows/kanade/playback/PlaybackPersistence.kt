package me.mallows.kanade.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.mallows.kanade.core.data.db.PlaybackStateDao
import me.mallows.kanade.core.data.db.PlaybackStateEntity
import me.mallows.kanade.core.data.db.TrackDao
import timber.log.Timber

/**
 * 再生キューと再生位置を保存し、再生サービスの起動時に戻す。
 * 変化から1秒後にまとめて保存し、再生中は10秒ごとに位置も保存する。
 */
class PlaybackPersistence(
  private val stateDao: PlaybackStateDao,
  private val trackDao: TrackDao,
  /** 再生サービスのスコープ (メインスレッド) */
  private val scope: CoroutineScope,
) : Player.Listener {

  /** 再生サービスの終了後も保存を最後まで行うためのスコープ */
  private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private var player: Player? = null
  private var pending: Job? = null
  private var ticker: Job? = null

  fun attach(player: Player) {
    this.player = player
    player.addListener(this)
  }

  /** 保存した状態をプレイヤーに戻す。すでに曲が入っていれば何もしない */
  /** 保存した再生キューと位置 */
  class Saved(
    val items: List<MediaItem>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
  )

  /** 保存した再生キューを読む。無ければnull */
  suspend fun load(): Saved? {
    val ids = stateDao.queue()
    val saved = stateDao.state() ?: return null
    if (ids.isEmpty()) return null
    val tracks = trackDao.getByMediaStoreIds(ids).associateBy { it.mediaStoreId }
    // 端末から消えた曲とスキャン対象外の曲は除き、今の曲の位置を合わせ直す
    val currentId = ids.getOrNull(saved.currentIndex)
    val items = ids.mapNotNull { id -> tracks[id]?.takeUnless { it.excluded }?.toMediaItem() }
    if (items.isEmpty()) return null
    val index = items.indexOfFirst { it.mediaId == currentId?.toString() }
    return Saved(
      items = items,
      index = index.coerceAtLeast(0),
      positionMs = if (index >= 0) saved.positionMs else 0L,
      shuffle = saved.shuffle,
      repeatMode = saved.repeatMode,
    )
  }

  suspend fun restore(player: Player) {
    val saved = load() ?: return
    if (player.mediaItemCount > 0) return
    player.setMediaItems(saved.items, saved.index, saved.positionMs)
    player.shuffleModeEnabled = saved.shuffle
    player.repeatMode = saved.repeatMode
    player.prepare()
    Timber.d("再生状態を復元: %d曲, %d曲目, %dms", saved.items.size, saved.index, saved.positionMs)
  }

  override fun onEvents(player: Player, events: Player.Events) {
    if (events.containsAny(
        Player.EVENT_TIMELINE_CHANGED,
        Player.EVENT_MEDIA_ITEM_TRANSITION,
        Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
        Player.EVENT_REPEAT_MODE_CHANGED,
        Player.EVENT_IS_PLAYING_CHANGED,
        Player.EVENT_POSITION_DISCONTINUITY,
      )
    ) {
      pending?.cancel()
      pending = scope.launch {
        delay(1_000)
        save(player)
      }
    }
    if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
      ticker?.cancel()
      if (player.isPlaying) {
        ticker = scope.launch {
          while (isActive) {
            delay(10_000)
            save(player)
          }
        }
      }
    }
  }

  /** 今の状態をすぐに保存する (サービス終了時など) */
  fun saveNow() {
    pending?.cancel()
    ticker?.cancel()
    player?.let(::save)
  }

  private fun save(player: Player) {
    // プレイヤーはメインスレッドで読み、書き込みはIOスレッドで行う
    val ids = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).idOrNull() }
    val state = PlaybackStateEntity(
      currentIndex = player.currentMediaItemIndex,
      positionMs = player.currentPosition,
      shuffle = player.shuffleModeEnabled,
      repeatMode = player.repeatMode,
    )
    ioScope.launch {
      runCatching { stateDao.saveAll(ids, state) }
        .onFailure { Timber.e(it, "再生状態を保存できませんでした") }
    }
  }

  private fun MediaItem.idOrNull(): Long? = mediaId.toLongOrNull()
}
