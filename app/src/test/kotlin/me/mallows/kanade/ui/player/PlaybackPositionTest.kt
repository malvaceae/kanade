package me.mallows.kanade.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPositionTest {

  @Test
  fun 連続して再生中は戻らない() {
    // 戻りの大きさに関係なく、戻さない
    assertEquals(3038L, settledPosition(previous = 3038, now = 3037, playing = true))
    assertEquals(3038L, settledPosition(previous = 3038, now = 2920, playing = true))
    assertEquals(3038L, settledPosition(previous = 3038, now = 1000, playing = true))
  }

  @Test
  fun 進むときはそのまま() {
    assertEquals(3100L, settledPosition(previous = 3038, now = 3100, playing = true))
    assertEquals(3038L, settledPosition(previous = 3038, now = 3038, playing = true))
  }

  @Test
  fun 飛んだあとの最初はそのまま() {
    // シークや曲の切り替えで、呼び出し側が直前の位置を戻したとき
    assertEquals(0L, settledPosition(previous = NONE, now = 0, playing = true))
    assertEquals(500L, settledPosition(previous = NONE, now = 500, playing = true))
  }

  @Test
  fun 止まっているときはそのまま() {
    assertEquals(2920L, settledPosition(previous = 3038, now = 2920, playing = false))
  }

  @Test
  fun 長さを超える位置は前の項目のものなので使わない() {
    // 21秒の項目の10秒付近で、1秒の項目に移った直後
    assertEquals(0L, currentPosition(live = 10_000, published = 0, duration = 1_000))
    assertEquals(120L, currentPosition(live = 10_000, published = 120, duration = 1_000))
  }

  @Test
  fun 長さ以内の位置はそのまま() {
    assertEquals(500L, currentPosition(live = 500, published = 0, duration = 1_000))
    assertEquals(1_000L, currentPosition(live = 1_000, published = 0, duration = 1_000))
  }

  @Test
  fun 長さが分からないときは0() {
    assertEquals(0L, currentPosition(live = 500, published = 100, duration = 0))
  }
}
