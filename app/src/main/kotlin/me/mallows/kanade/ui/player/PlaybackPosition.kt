package me.mallows.kanade.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
  val latest = rememberUpdatedState(state)
  // position がどの項目の位置か。項目が変わった最初の描画では、position はまだ前の項目のもの (更新は描画のあと) だし、
  // プレイヤーの見積もり (read()) も、前の項目の位置を返すことがある。新しい項目の長さで割ると、バーが一瞬飛ぶ。
  // サービスから届いた位置 (state.positionMs) は、題名や長さと同時に届くので正しい。
  // そのため、項目が変わって最初の1回だけは、サービスの位置を使い、あとは、描画のたびに読んで、なめらかに進める
  val owner = remember { mutableStateOf<String?>(null) }
  val position = remember { mutableLongStateOf(0L) }
  // 直前に見せた位置。曲が変わる・位置が飛ぶ (シークなど) と、持ち越さない
  val shown = remember(state.mediaId, state.discontinuity) { longArrayOf(NONE) }
  fun now(): Long {
    val raw = if (owner.value == state.mediaId) read() else state.positionMs
    return settledPosition(shown[0], withinDuration(raw, state.durationMs), state.isPlaying).also { shown[0] = it }
  }
  // 届いた状態が変わったら (曲・再生中かどうか・シーク) すぐ読み直し、再生中は毎コマ読む
  LaunchedEffect(state.mediaId, state.isPlaying, state.positionMs, state.durationMs) {
    position.longValue = now()
    owner.value = state.mediaId
    while (state.isPlaying) withFrameMillis { position.longValue = now() }
  }
  return remember {
    object : State<Long> {
      override val value: Long
        get() {
          val s = latest.value
          return if (owner.value == s.mediaId) position.longValue else withinDuration(s.positionMs, s.durationMs)
        }
    }
  }
}

internal const val NONE = Long.MIN_VALUE

/**
 * 表示する再生位置。連続して再生しているあいだの実際の位置は戻らない ([previous]は、その間に見せた直前の位置)。
 * プレイヤーから読む位置は見積もりなので、更新で直されるとき、小さく戻ることがある。それは表示に出さない。
 * 位置が飛ぶ (シーク・曲の切り替えなど) ときは、呼び出し側が[previous]を[NONE]に戻すので、そのまま見せる。止まっているときも、そのまま
 */
internal fun settledPosition(previous: Long, now: Long, playing: Boolean): Long =
  if (playing && previous != NONE) maxOf(previous, now) else now

/** 位置を、項目の長さの範囲に収める。長さが分からないときは0 */
internal fun withinDuration(position: Long, duration: Long): Long =
  if (duration > 0) position.coerceIn(0, duration) else 0
