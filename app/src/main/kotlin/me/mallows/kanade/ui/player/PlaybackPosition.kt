package me.mallows.kanade.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import me.mallows.kanade.playback.PlayerState

/** 今の再生位置を読む関数。画面全体で1つの再生を表すので、上の画面から渡す */
val LocalPlaybackPosition = compositionLocalOf<() -> Long> { { 0L } }

/**
 * なめらかに進む再生位置。再生中は描画のたびにプレイヤーから今の位置を読む
 * (Media3の再生画面と同じやり方。シークや曲の切り替えはプレイヤーがその場で反映するので、自分で計算しない)
 */
@Composable
fun rememberPlaybackPosition(state: PlayerState): State<Long> {
  val read = LocalPlaybackPosition.current
  fun now(): Long {
    val p = read().coerceAtLeast(0)
    return if (state.durationMs > 0) p.coerceAtMost(state.durationMs) else 0
  }
  val position = remember { mutableLongStateOf(now()) }
  // 届いた状態が変わったら (曲・再生中かどうか・シーク) すぐ読み直し、再生中は毎コマ読む
  LaunchedEffect(state.mediaId, state.isPlaying, state.positionMs, state.durationMs) {
    position.longValue = now()
    while (state.isPlaying) withFrameMillis { position.longValue = now() }
  }
  return position
}
