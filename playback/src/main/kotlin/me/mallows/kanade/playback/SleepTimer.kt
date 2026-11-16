package me.mallows.kanade.playback

import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.SessionCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/** スリープタイマーの指示 (画面 → 再生サービス) */
object SleepCommand {
  const val ACTION = "me.mallows.kanade.SLEEP_TIMER"
  const val ARG_MINUTES = "minutes"
  const val ARG_END_OF_TRACK = "end_of_track"

  val command = SessionCommand(ACTION, Bundle.EMPTY)

  fun args(minutes: Int, endOfTrack: Boolean) = Bundle().apply {
    putInt(ARG_MINUTES, minutes)
    putBoolean(ARG_END_OF_TRACK, endOfTrack)
  }
}

/** 指定時間後、または今の曲の終わりで一時停止する (再生サービス側で動く) */
class SleepTimer(private val player: ExoPlayer, private val scope: CoroutineScope) {

  private var job: Job? = null

  private val listener = object : Player.Listener {
    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
      if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
        // 曲の終わりで止まったら、次からは通常どおり続けて再生する
        player.pauseAtEndOfMediaItems = false
        player.removeListener(this)
        Timber.d("スリープタイマー: 曲の終わりで停止")
      }
    }
  }

  /** [minutes]分後に止める。[endOfTrack]なら今の曲の終わりで止める。両方なしなら解除 */
  fun set(minutes: Int, endOfTrack: Boolean) {
    cancel()
    when {
      endOfTrack -> {
        player.pauseAtEndOfMediaItems = true
        player.addListener(listener)
      }
      minutes > 0 -> job = scope.launch {
        delay(minutes * 60_000L)
        player.pause()
        Timber.d("スリープタイマー: %d分で停止", minutes)
      }
    }
  }

  fun cancel() {
    job?.cancel()
    job = null
    player.pauseAtEndOfMediaItems = false
    player.removeListener(listener)
  }
}
