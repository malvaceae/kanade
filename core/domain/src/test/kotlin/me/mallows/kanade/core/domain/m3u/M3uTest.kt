package me.mallows.kanade.core.domain.m3u

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uTest {

  @Test
  fun 書き出したものを読み戻せる() {
    val entries = listOf(
      M3uEntry("/storage/emulated/0/Music/A/01 曲.flac", 245, "歌手 - 曲"),
      M3uEntry("/storage/emulated/0/Music/B/02.mp3", null, null),
    )
    val parsed = M3u.parse(M3u.format(entries))
    assertEquals(entries[0], parsed[0])
    assertEquals(entries[1].path, parsed[1].path)
    assertNull(parsed[1].durationSec)
  }

  @Test
  fun 拡張情報のない形式やBOMやCRLFも読める() {
    val text = "\uFEFF# comment\r\nMusic\\A\\01.flac\r\n\r\nB/02.mp3\r\n"
    assertEquals(listOf("Music\\A\\01.flac", "B/02.mp3"), M3u.parse(text).map { it.path })
  }

  @Test
  fun ファイル名で照合し同名はフォルダで区別する() {
    val m = PathMatcher(
      listOf(
        1L to "Music/AlbumA/01 Intro.flac",
        2L to "Music/AlbumB/01 Intro.flac",
        3L to "Music/Other/Song.mp3",
      ),
    )
    assertEquals(2L, m.match("D:\\Music\\AlbumB\\01 Intro.flac"))
    assertEquals(1L, m.match("/storage/emulated/0/Music/AlbumA/01%20Intro.flac"))
    assertEquals(3L, m.match("../other/SONG.MP3"))
    assertNull(m.match("Missing.mp3"))
  }
}
