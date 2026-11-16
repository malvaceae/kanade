package me.mallows.kanade.ui.player

import android.content.Context
import android.media.AudioFormat
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** ファイルから読み取った曲の形式 */
data class TrackFormat(
  val mime: String?,
  val sampleRate: Int?,
  val bitDepth: Int?,
  val channels: Int?,
  val bitrateKbps: Int?,
  val sizeBytes: Long?,
)

/** 曲のファイルを開いて形式を調べる */
suspend fun readTrackFormat(context: Context, uri: String, durationMs: Long): TrackFormat = withContext(Dispatchers.IO) {
  val extractor = MediaExtractor()
  try {
    extractor.setDataSource(context, uri.toUri(), null)
    val format = (0 until extractor.trackCount)
      .map(extractor::getTrackFormat)
      .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
    val size = runCatching {
      context.contentResolver.openAssetFileDescriptor(uri.toUri(), "r")?.use { it.length }
    }.getOrNull()?.takeIf { it > 0 }
    TrackFormat(
      mime = format?.getString(MediaFormat.KEY_MIME),
      sampleRate = format?.intOrNull(MediaFormat.KEY_SAMPLE_RATE),
      bitDepth = format?.bitDepth(),
      channels = format?.intOrNull(MediaFormat.KEY_CHANNEL_COUNT),
      bitrateKbps = format?.intOrNull(MediaFormat.KEY_BIT_RATE)?.div(1000)
        ?: size?.let { s -> if (durationMs > 0) (s * 8 / durationMs).toInt() else null },
      sizeBytes = size,
    )
  } catch (e: Exception) {
    TrackFormat(null, null, null, null, null, null)
  } finally {
    extractor.release()
  }
}

private fun MediaFormat.intOrNull(key: String): Int? = if (containsKey(key)) getInteger(key) else null

/** ビット深度。PCMは符号化方式から、FLACなどは "bits-per-sample" から読む */
private fun MediaFormat.bitDepth(): Int? {
  intOrNull("bits-per-sample")?.let { return it }
  return when (intOrNull(MediaFormat.KEY_PCM_ENCODING)) {
    AudioFormat.ENCODING_PCM_8BIT -> 8
    AudioFormat.ENCODING_PCM_16BIT -> 16
    AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
    AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 32
    else -> null
  }
}

/** 「audio/flac」→「FLAC」のように短い名前にする */
fun shortFormatName(mime: String?): String? = when (mime) {
  null -> null
  "audio/raw", "audio/wav", "audio/x-wav" -> "WAV (PCM)"
  "audio/mpeg" -> "MP3"
  "audio/mp4a-latm" -> "AAC"
  "audio/flac" -> "FLAC"
  "audio/alac" -> "ALAC"
  "audio/vorbis" -> "Vorbis"
  "audio/opus" -> "Opus"
  else -> mime.removePrefix("audio/").uppercase()
}
