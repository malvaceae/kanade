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
  // 直前に見せた位置 (曲が変わったら持ち越さない)。再生中の小さな戻りを、戻さずに保つために使う
  val last = remember(state.mediaId) { longArrayOf(0L) }
  fun settle(n: Long): Long = smoothedPosition(last[0], n, state.isPlaying).also { last[0] = it }
  // 届いた状態が変わったら (曲・再生中かどうか・シーク) すぐ読み直し、再生中は毎コマ読む
  LaunchedEffect(state.mediaId, state.isPlaying, state.positionMs, state.durationMs) {
    position.longValue = settle(now())
    while (state.isPlaying) withFrameMillis { position.longValue = settle(now()) }
  }
  return position
}

/** 再生中に、読んだ位置がこの幅より小さく戻ったときは、音の時計の補正などによる揺れとして、戻さない。これより大きく戻ったら、シークなどなので、そのまま見せる */
internal const val BACKWARD_JITTER_MS = 500L

/**
 * 表示する再生位置。再生中に、[now]が直前の[last]より少しだけ小さいときは、[last]のままにする
 * (プレイヤーの位置は、音の出力の時計の補正で、数十~百数十ミリ秒、戻ることがある。短い動画では、シークバーが戻って見える)
 */
internal fun smoothedPosition(last: Long, now: Long, playing: Boolean): Long =
  if (playing && now < last && last - now < BACKWARD_JITTER_MS) last else now
