package me.mallows.kanade.core.data.library

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackNumberTest {

  @Test
  fun 値なしや0は不明() {
    assertEquals(TrackNumber(null, null), TrackNumber.fromMediaStore(null))
    assertEquals(TrackNumber(null, null), TrackNumber.fromMediaStore(0))
  }

  @Test
  fun 千未満はトラック番号だけ() {
    assertEquals(TrackNumber(null, 7), TrackNumber.fromMediaStore(7))
  }

  @Test
  fun 千以上はディスク番号付き() {
    assertEquals(TrackNumber(2, 3), TrackNumber.fromMediaStore(2003))
    assertEquals(TrackNumber(1, 12), TrackNumber.fromMediaStore(1012))
  }

  @Test
  fun ディスク番号だけの値() {
    assertEquals(TrackNumber(1, null), TrackNumber.fromMediaStore(1000))
  }
}
