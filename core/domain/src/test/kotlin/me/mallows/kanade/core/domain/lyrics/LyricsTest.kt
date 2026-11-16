package me.mallows.kanade.core.domain.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTest {

  @Test
  fun 時刻付きの歌詞を時刻の順に読む() {
    val lrc = """
      [ar:歌手]
      [ti:曲]
      [00:12.30]一行目
      [00:05.5]はじめ
      [01:02.345][00:20.00]くり返し
      [00:30.00]
    """.trimIndent()
    val lyrics = Lyrics.parse(lrc)!!
    assertTrue(lyrics.synced)
    assertEquals(
      listOf(5_500L to "はじめ", 12_300L to "一行目", 20_000L to "くり返し", 30_000L to "", 62_345L to "くり返し"),
      lyrics.lines.map { it.timeMs to it.text },
    )
  }

  @Test
  fun 今の行を時刻から求める() {
    val lyrics = Lyrics.parse("[00:01.00]a\n[00:05.00]b\n[00:09.00]c")!!
    assertEquals(-1, lyrics.indexAt(500))
    assertEquals(0, lyrics.indexAt(1_000))
    assertEquals(1, lyrics.indexAt(8_999))
    assertEquals(2, lyrics.indexAt(60_000))
  }

  @Test
  fun offsetで時刻をずらす() {
    val lyrics = Lyrics.parse("[offset:+500]\n[00:02.00]a")!!
    assertEquals(1_500L, lyrics.lines.single().timeMs)
  }

  @Test
  fun 時刻のない歌詞は行ごとの文字にする() {
    val lyrics = Lyrics.parse("\r\n一行目\r\n\r\n二行目\r\n")!!
    assertFalse(lyrics.synced)
    assertEquals(listOf("一行目", "", "二行目"), lyrics.lines.map { it.text })
    assertEquals(-1, lyrics.indexAt(1_000))
    assertNull(Lyrics.parse("  "))
  }
}
