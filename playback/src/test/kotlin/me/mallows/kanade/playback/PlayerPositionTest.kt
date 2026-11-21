package me.mallows.kanade.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerPositionTest {
  @Test fun 今の項目が一致すれば位置を返す() = assertEquals(1_500L, positionIfCurrent(current = "a", position = 1_500, wanted = "a"))

  @Test fun 一致しなければnull() = assertNull(positionIfCurrent(current = "b", position = 1_500, wanted = "a"))

  @Test fun 今の項目がなければnull() = assertNull(positionIfCurrent(current = null, position = 0, wanted = null))
}
