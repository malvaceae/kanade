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

  @Test
  fun ラテン文字の判定は主な範囲でUnicodeScriptと同じ() {
    // 本体はAndroid 6.0でも動くよう範囲で調べている。実際の判定 (UnicodeScript) と、よく使う範囲で食い違わないことを確かめる
    val ranges = listOf(0x41..0x7A, 0xC0..0x24F, 0x1E00..0x1EFF, 0xFF21..0xFF5A)
    for (code in ranges.flatMap { it }) {
      val c = code.toChar()
      if (!c.isLetter()) continue
      val expected = Character.UnicodeScript.of(code) == Character.UnicodeScript.LATIN
      assertEquals("U+%04X".format(code), expected, TextOrder.groupOf(c.toString()) == 1)
    }
  }
}
