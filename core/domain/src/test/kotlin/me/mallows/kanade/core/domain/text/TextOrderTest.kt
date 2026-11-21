package me.mallows.kanade.core.domain.text

import org.junit.Assert.assertEquals
import org.junit.Test

class TextOrderTest {

  @Test
  fun 数字と記号と英字とかなと漢字の順に並ぶ() {
    val titles = listOf("夜明け", "あさひ", "Zebra", "apple", "01 Intro", "カナリア", "Ｂｅｔａ", "ｶﾞﾗｽ", "#hash")
    assertEquals(
      listOf("#hash", "01 Intro", "apple", "Ｂｅｔａ", "Zebra", "あさひ", "カナリア", "ｶﾞﾗｽ", "夜明け"),
      titles.sortedWith(TextOrder),
    )
  }

  @Test
  fun かなとカナや英字の大小は区別せずに並ぶ() {
    assertEquals(listOf("あい", "イウ", "うえ"), listOf("うえ", "イウ", "あい").sortedWith(TextOrder))
    assertEquals(listOf("alpha", "Beta", "gamma"), listOf("gamma", "Beta", "alpha").sortedWith(TextOrder))
  }
}
