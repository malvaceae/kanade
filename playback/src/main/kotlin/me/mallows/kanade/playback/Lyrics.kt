package me.mallows.kanade.playback

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.mallows.kanade.core.domain.lyrics.Lyrics

/** 再生中の曲の歌詞 (再生サービスが曲ごとに更新し、画面が読む) */
@Singleton
class LyricsMonitor @Inject constructor() {
  private val state = MutableStateFlow<CurrentLyrics?>(null)
  val current: StateFlow<CurrentLyrics?> = state.asStateFlow()

  fun set(mediaId: String?, lyrics: Lyrics?) {
    state.value = mediaId?.let { CurrentLyrics(it, lyrics) }
  }
}

/** [mediaId] の曲の歌詞。歌詞が無ければ [lyrics] はnull */
data class CurrentLyrics(val mediaId: String, val lyrics: Lyrics?)

/**
 * 今の曲 ([mediaId]) の歌詞。別の曲の歌詞が残っていても出さない。
 * 曲は、ライブラリの曲 (MediaStoreのIDの文字列) も、外から開いたファイル ("ext:" で始まる文字列) もあるので、IDの文字列そのもので比べる
 */
fun CurrentLyrics?.forMedia(mediaId: String?): Lyrics? =
  if (this != null && mediaId != null && this.mediaId == mediaId) lyrics else null

/**
 * 曲に埋め込まれた歌詞を読む。
 * FLAC・Oggは Vorbisコメント (LYRICS・UNSYNCEDLYRICS)、MP4は ©lyr (Media3はUSLTとして渡す)、
 * MP3は ID3のUSLT (Media3は解析しないため、ここで読む)。
 */
@OptIn(UnstableApi::class)
fun lyricsOf(format: Format?): Lyrics? {
  val metadata = format?.metadata ?: return null
  for (i in 0 until metadata.length()) {
    val text = when (val e = metadata.get(i)) {
      is VorbisComment -> e.value.takeIf { e.key.uppercase() in VORBIS_KEYS }
      is TextInformationFrame -> e.values[0].takeIf { e.id == "USLT" }
      is BinaryFrame -> if (e.id == "USLT") parseUslt(e.data) else null
      else -> null
    }
    Lyrics.parse(text)?.let { return it }
  }
  return null
}

private val VORBIS_KEYS = setOf("LYRICS", "UNSYNCEDLYRICS", "UNSYNCED LYRICS", "SYNCEDLYRICS")

/** ID3のUSLT: 文字コード (1) + 言語 (3) + 説明 (終端まで) + 本文 */
private fun parseUslt(data: ByteArray): String? {
  if (data.size < 5) return null
  val (charset, terminator) = when (data[0].toInt()) {
    0 -> Charsets.ISO_8859_1 to 1
    1 -> Charsets.UTF_16 to 2
    2 -> Charsets.UTF_16BE to 2
    3 -> Charsets.UTF_8 to 1
    else -> return null
  }
  var i = 4
  // 説明を飛ばす (2バイト文字は偶数位置で区切る)
  while (i + terminator <= data.size) {
    if ((0 until terminator).all { data[i + it].toInt() == 0 }) break
    i += terminator
  }
  i += terminator
  if (i >= data.size) return null
  // UTF-16で本文にBOMがない場合は、説明のBOMに合わせる
  val body = data.copyOfRange(i, data.size)
  val cs: Charset = if (charset == Charsets.UTF_16 && !(body.size >= 2 && (body[0].toInt() and 0xFF) in setOf(0xFE, 0xFF))) {
    if ((data[4].toInt() and 0xFF) == 0xFF) Charsets.UTF_16LE else Charsets.UTF_16BE
  } else {
    charset
  }
  return String(body, cs).trimEnd('\u0000')
}
