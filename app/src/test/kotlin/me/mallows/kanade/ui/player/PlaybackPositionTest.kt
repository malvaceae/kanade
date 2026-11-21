package me.mallows.kanade.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPositionTest {

  @Test
  fun 再生中の小さな戻りは戻さない() {
    assertEquals(3038L, smoothedPosition(last = 3038, now = 2920, playing = true))
    assertEquals(3038L, smoothedPosition(last = 3038, now = 3038 - (BACKWARD_JITTER_MS - 1), playing = true))
  }

  @Test
  fun 進むときはそのまま() {
    assertEquals(3100L, smoothedPosition(last = 3038, now = 3100, playing = true))
    assertEquals(3038L, smoothedPosition(last = 3038, now = 3038, playing = true))
  }

  @Test
  fun 大きく戻ったらシークなのでそのまま() {
    assertEquals(0L, smoothedPosition(last = 3038, now = 0, playing = true))
    assertEquals(2538L, smoothedPosition(last = 3038, now = 3038 - BACKWARD_JITTER_MS, playing = true))
  }

  @Test
  fun 止まっているときは戻りもそのまま() {
    assertEquals(2920L, smoothedPosition(last = 3038, now = 2920, playing = false))
  }
}
