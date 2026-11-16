package me.mallows.kanade.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.mallows.kanade.core.data.db.PlayStatsDao

/**
 * 再生回数を数える。曲を30秒または半分以上 (短い方) 聴いたら1回とし、1回の再生では1度だけ数える。
 * 聴いた時間は再生中の経過で数えるため、シークで飛ばした分は含めない。
 */
class PlayCounter(
  private val dao: PlayStatsDao,
  private val scope: CoroutineScope,
) : Player.Listener {

  private var player: Player? = null
  private var currentId: Long? = null
  private var listenedMs = 0L
  private var counted = false
  private var ticker: Job? = null

  fun attach(player: Player) {
    this.player = player
    player.addListener(this)
    start(player.currentMediaItem)
    if (player.isPlaying) tick()
  }

  override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
    // 同じ曲のリピートも、新しい1回として数える
    start(mediaItem)
  }

  override fun onIsPlayingChanged(isPlaying: Boolean) {
    if (isPlaying) tick() else ticker?.cancel()
  }

  private fun start(item: MediaItem?) {
    currentId = item?.mediaId?.toLongOrNull()
    listenedMs = 0
    counted = false
  }

  private fun tick() {
    ticker?.cancel()
    ticker = scope.launch {
      while (isActive) {
        delay(STEP_MS)
        val p = player ?: return@launch
        if (!p.isPlaying) return@launch
        listenedMs += (STEP_MS * p.playbackParameters.speed).toLong()
        val id = currentId ?: continue
        val duration = p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        if (!counted && listenedMs >= minOf(THRESHOLD_MS, duration / 2)) {
          counted = true
          dao.recordPlay(id, System.currentTimeMillis())
        }
      }
    }
  }

  private companion object {
    const val STEP_MS = 1_000L
    const val THRESHOLD_MS = 30_000L
  }
}
