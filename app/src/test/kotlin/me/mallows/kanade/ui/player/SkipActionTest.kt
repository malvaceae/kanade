package me.mallows.kanade.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SkipActionTest {
  private val duration = 100_000L

  @Test fun 戻るときは10秒前へ() =
    assertEquals(Skip.SeekTo(15_000), skipAction(25_000, duration, direction = -1, hasNext = true))

  @Test fun 戻るときは曲の頭より前へ行かない() =
    assertEquals(Skip.SeekTo(0), skipAction(4_000, duration, direction = -1, hasNext = true))

  @Test fun 進むときは10秒先へ() =
    assertEquals(Skip.SeekTo(35_000), skipAction(25_000, duration, direction = 1, hasNext = true))

  @Test fun 曲の終わりの手前までなら同じ曲で進む() =
    assertEquals(Skip.SeekTo(99_000), skipAction(89_000, duration, direction = 1, hasNext = true))

  @Test fun 曲の終わりを越えるなら次の曲へ() =
    assertEquals(Skip.Next, skipAction(95_000, duration, direction = 1, hasNext = true))

  @Test fun 曲の終わりにちょうど着くときも次の曲へ() =
    assertEquals(Skip.Next, skipAction(90_000, duration, direction = 1, hasNext = true))

  @Test fun 次の曲がなければ曲の終わりへ() =
    assertEquals(Skip.SeekTo(duration), skipAction(95_000, duration, direction = 1, hasNext = false))

  @Test fun 直前のダブルタップがなければ今の再生位置から数える() =
    assertEquals(42_000L, skipBase(positionMs = 42_000, lastTarget = null, lastAt = 0, now = 1_000_000))

  @Test fun 直前のダブルタップから1秒以内なら前の目標から数える() =
    assertEquals(50_000L, skipBase(positionMs = 40_000, lastTarget = 50_000, lastAt = 1_000_000, now = 1_000_500))

  @Test fun 直前のダブルタップから1秒たてば今の再生位置から数える() =
    assertEquals(40_000L, skipBase(positionMs = 40_000, lastTarget = 50_000, lastAt = 1_000_000, now = 1_001_000))

  @Test fun 長さが分からない間はそのままずらす() {
    assertEquals(Skip.SeekTo(35_000), skipAction(25_000, 0, direction = 1, hasNext = true))
    assertEquals(Skip.SeekTo(0), skipAction(4_000, 0, direction = -1, hasNext = true))
  }
}
