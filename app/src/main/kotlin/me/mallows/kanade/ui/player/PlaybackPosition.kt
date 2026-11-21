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
  // 項目が切り替わった直後の1回だけ、プレイヤーの見積もり (read()) が前の項目の位置を返すことがあり、
  // 新しい項目の長さで割ると、バーが一瞬飛ぶ。サービスから届いた位置 (state.positionMs) は、題名や長さと同時に届くので正しい。
  // そのため、項目が変わって最初の描画だけは、サービスの位置を使い、あとは、描画のたびに読んで、なめらかに進める
  val settledId = remember { arrayOfNulls<String>(1) }
  fun now(): Long {
    val live = if (state.mediaId == settledId[0]) read() else state.positionMs
    return currentPosition(live, state.positionMs, state.durationMs)
  }
  val position = remember { mutableLongStateOf(now()) }
  // position がどの項目の位置か。項目が変わった最初の描画では、position はまだ前の項目のもの (更新は描画のあと) なので、使わない
  val owner = remember { mutableStateOf(state.mediaId) }
  // 直前に見せた位置。曲が変わる・位置が飛ぶ (シークなど) と、持ち越さない
  val shown = remember(state.mediaId, state.discontinuity) { longArrayOf(NONE) }
  fun settle(n: Long): Long = settledPosition(shown[0], n, state.isPlaying).also { shown[0] = it }
  // 届いた状態が変わったら (曲・再生中かどうか・シーク) すぐ読み直し、再生中は毎コマ読む
  LaunchedEffect(state.mediaId, state.isPlaying, state.positionMs, state.durationMs) {
    position.longValue = settle(now())
    owner.value = state.mediaId
    // 最初の描画を終えたら、この項目は、描画のたびに読んでよい
    settledId[0] = state.mediaId
    while (state.isPlaying) withFrameMillis { position.longValue = settle(now()) }
  }
  return remember {
    object : State<Long> {
      override val value: Long
        get() {
          val s = latest.value
          return if (owner.value == s.mediaId) position.longValue else currentPosition(s.positionMs, s.positionMs, s.durationMs)
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

/**
 * 今の項目の再生位置。[live]はプレイヤーから読んだ位置、[published]は、今の項目の状態と同時に届いた位置。
 * 項目が変わった直後は、[live]がまだ前の項目の位置のことがある。項目の長さ ([duration]) を超える位置は、
 * 今の項目のものではないので使わず、[published]を使う (使うと、長さまで丸められて、バーが一瞬、右端に飛ぶ)
 */
internal fun currentPosition(live: Long, published: Long, duration: Long): Long {
  val position = if (duration > 0 && live > duration) published else live
  return if (duration > 0) position.coerceIn(0, duration) else 0
}
