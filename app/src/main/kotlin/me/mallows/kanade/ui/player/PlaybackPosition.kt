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
 * 再生中は最後に届いた位置から経過時間 (速度を掛ける) だけ毎フレーム進める
 */
@Composable
fun rememberPlaybackPosition(state: PlayerState): State<Long> {
  val position = remember { mutableLongStateOf(state.positionMs) }
  LaunchedEffect(state.positionMs, state.positionAt, state.isPlaying, state.speed, state.durationMs) {
    fun now(): Long {
      // 長さが分からない間は進めない (進めると、長さで割ったときにバーが端まで行ってしまう)
      if (!state.isPlaying || state.durationMs <= 0) return state.positionMs
      val elapsed = ((SystemClock.elapsedRealtime() - state.positionAt) * state.speed).toLong()
      val p = state.positionMs + elapsed.coerceAtLeast(0)
      return if (state.durationMs > 0) p.coerceAtMost(state.durationMs) else p
    }
    position.longValue = now()
    while (state.isPlaying) withFrameMillis { position.longValue = now() }
  }
  return position
}
