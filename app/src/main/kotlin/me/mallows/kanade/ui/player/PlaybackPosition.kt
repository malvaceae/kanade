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
  // 直前に見せた位置。曲が変わる・位置が飛ぶ (シークなど) と、持ち越さない
  val shown = remember(state.mediaId, state.discontinuity) { longArrayOf(NONE) }
  fun settle(n: Long): Long = settledPosition(shown[0], n, state.isPlaying).also { shown[0] = it }
  // 届いた状態が変わったら (曲・再生中かどうか・シーク) すぐ読み直し、再生中は毎コマ読む
  LaunchedEffect(state.mediaId, state.isPlaying, state.positionMs, state.durationMs) {
    position.longValue = settle(now())
    while (state.isPlaying) withFrameMillis { position.longValue = settle(now()) }
  }
  return position
}

internal const val NONE = Long.MIN_VALUE

/**
 * 表示する再生位置。連続して再生しているあいだの実際の位置は戻らない ([previous]は、その間に見せた直前の位置)。
 * プレイヤーから読む位置は見積もりなので、更新で直されるとき、小さく戻ることがある。それは表示に出さない。
 * 位置が飛ぶ (シーク・曲の切り替えなど) ときは、呼び出し側が[previous]を[NONE]に戻すので、そのまま見せる。止まっているときも、そのまま
 */
internal fun settledPosition(previous: Long, now: Long, playing: Boolean): Long =
  if (playing && previous != NONE) maxOf(previous, now) else now
