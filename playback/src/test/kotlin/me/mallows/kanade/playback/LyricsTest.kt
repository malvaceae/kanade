package me.mallows.kanade.playback

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.BinaryFrame
import me.mallows.kanade.core.domain.lyrics.LyricLine
import me.mallows.kanade.core.domain.lyrics.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsTest {
  private val lyrics = Lyrics(listOf(LyricLine(0, "あ")), synced = true)

  @Test fun ライブラリの曲の歌詞は同じIDの曲で出る() =
    assertEquals(lyrics, CurrentLyrics("42", lyrics).forMedia("42"))

  @Test fun 外から開いた曲の歌詞も同じIDで出る() {
    val id = "ext:content://media/external/file/7"
    assertEquals(lyrics, CurrentLyrics(id, lyrics).forMedia(id))
  }

  @Test fun 別の曲の歌詞は出さない() {
    assertNull(CurrentLyrics("42", lyrics).forMedia("43"))
    assertNull(CurrentLyrics("ext:content://a", lyrics).forMedia("ext:content://b"))
  }

  @Test fun 曲がない歌詞がないときは出さない() {
    assertNull(CurrentLyrics("42", lyrics).forMedia(null))
    assertNull(null.forMedia("42"))
    assertNull(CurrentLyrics("42", null).forMedia("42"))
  }

  /** 実際のMP3 (ID3v2.3、USLTはUTF-16、LRCの時刻付き) のUSLTフレームの中身 */
  private val usltSample = (
    "016a706efffe0000fffe5b00740069003a001c596e30e130ed30c730a330fc305d000a005b006100" +
    "72003a0043006f006400650078002000b530f330d730eb305d000a005b00300030003a0030003000" +
    "2e00300030005d0057886e306f708a304c3020005d3063306830fa638c3066300a005b0030003000" +
    "3a00300034002e00300030005d00a8986b307e304e308c305f3020000f5c55306a304c6b0a005b00" +
    "300030003a00300038002e00300030005d00609044300e66e56578302000f397923064306a304e30" +
    "0a005b00300030003a00310032002e00300030005d00ca4e1c596e307a7a6b30200022599230cf63" +
    "4f300a00"
    ).chunked(2).map { it.toInt(16).toByte() }.toByteArray()

  @OptIn(UnstableApi::class)
  @Test fun MP3のUSLTのUTF16とLRCを時刻付きの歌詞として読む() {
    val format = Format.Builder().setMetadata(Metadata(BinaryFrame("USLT", usltSample))).build()
    val lyrics = lyricsOf(format)!!
    assertEquals(true, lyrics.synced)
    assertEquals(
      listOf(
        LyricLine(0, "街の灯りが そっと揺れて"),
        LyricLine(4_000, "風にまぎれた 小さな歌"),
        LyricLine(8_000, "遠い明日へ 音をつなぎ"),
        LyricLine(12_000, "今夜の空に 夢を描く"),
      ),
      lyrics.lines,
    )
  }
}
