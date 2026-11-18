package me.mallows.kanade.playback.output

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import me.mallows.kanade.audio.ReplayGain

/**
 * 音源の形式に付いたタグからReplayGainを読む。
 * FLAC・Oggは Vorbisコメント、MP3は ID3のTXXX、MP4 (AAC・ALAC) は iTunesの独自項目に書かれる。
 */
@OptIn(UnstableApi::class)
fun replayGainOf(format: Format?): ReplayGain {
  val metadata = format?.metadata ?: return ReplayGain()
  val tags = mutableMapOf<String, String>()
  for (i in 0 until metadata.length()) {
    when (val e = metadata.get(i)) {
      is VorbisComment -> tags[e.key] = e.value
      is TextInformationFrame -> if (e.id == "TXXX" && e.description != null) tags[e.description!!] = e.values[0]
      is InternalFrame -> tags[e.description] = e.text
    }
  }
  return ReplayGain.parse(tags)
}
