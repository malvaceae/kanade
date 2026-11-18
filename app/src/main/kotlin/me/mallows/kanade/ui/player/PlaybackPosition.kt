package me.mallows.kanade.ui.player

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import me.mallows.kanade.playback.PlayerState

/**
 * なめらかに進む再生位置。再生位置は0.5秒ごとにしか届かないので、
 * 再生中は最後に届いた位置から経過時間 (速度を掛ける) だけ毎フレーム進める。
 *
 * 曲が変わった直後は、古い位置や「読み込み中なのに再生中」の知らせが続けて届くことがあり、
 * そのまま出すとつまみが一瞬前後に動く。そこで次のようにする。
 * - 曲が変わったらすぐ0にし、変わってから少しの間は、実際にたった時間より先には進めない
 * - 同じ曲で少しだけ戻る知らせ (時計のずれ) では戻さず、追いつくまで待つ。大きく戻るのはシークとして従う
 */
@Composable
fun rememberPlaybackPosition(state: PlayerState): State<Long> {
  val position = remember { mutableLongStateOf(state.positionMs) }
  /** 表示している曲と、その曲に変わった時刻 */
  val track = remember { TrackClock(state.mediaStoreId, 0L) }
  // 曲が変わったら、その場 (同じコマ) で0にする。後から直すと、新しい曲の長さと古い位置で1コマ描いてしまう
  if (state.mediaStoreId != track.id) {
    track.id = state.mediaStoreId
    track.changedAt = SystemClock.elapsedRealtime()
    position.longValue = 0
  }
  LaunchedEffect(state.positionMs, state.positionAt, state.isPlaying, state.speed, state.durationMs, state.mediaStoreId) {
    fun raw(): Long {
      // 長さが分からない間は進めない (進めると、長さで割ったときにバーが端まで行ってしまう)
      if (!state.isPlaying || state.durationMs <= 0) return state.positionMs
      val elapsed = ((SystemClock.elapsedRealtime() - state.positionAt) * state.speed).toLong()
      return state.positionMs + elapsed.coerceAtLeast(0)
    }
    fun now(previous: Long): Long {
      var p = raw()
      // 曲が変わった直後は、変わってからの実際の時間より先には出さない (古い曲の位置が混ざっても飛ばない)。
      // 落ち着いた後のシークを止めないよう、この制限は変わってから少しの間だけにする
      if (track.changedAt > 0) {
        val since = SystemClock.elapsedRealtime() - track.changedAt
        if (since < SETTLE_MS) p = p.coerceAtMost((since * state.speed).toLong())
      }
      // 少しだけ戻る知らせでは戻さない (シークのような大きな戻りには従う)
      if (state.isPlaying && p < previous && previous - p < SMALL_BACKWARD_MS) p = previous
      if (state.durationMs > 0) p = p.coerceAtMost(state.durationMs)
      return p.coerceAtLeast(0)
    }
    position.longValue = now(position.longValue)
    while (state.isPlaying) withFrameMillis { position.longValue = now(position.longValue) }
  }
  return position
}

/** 表示している曲と、その曲に変わった時刻 (変わる前から表示していた曲は0) */
private class TrackClock(var id: Long?, var changedAt: Long)

/** これより小さく戻る知らせは、時計のずれとして扱って戻さない */
private const val SMALL_BACKWARD_MS = 600L

/** 曲が変わってから、位置の知らせが落ち着くまでの時間 */
private const val SETTLE_MS = 2_500L
