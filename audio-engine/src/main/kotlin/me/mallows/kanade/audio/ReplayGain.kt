package me.mallows.kanade.audio

import kotlin.math.pow

/** ReplayGainの使い方 */
enum class ReplayGainMode { OFF, TRACK, ALBUM }

/** 曲のタグに書かれたReplayGainの値 */
data class ReplayGain(
  val trackGainDb: Float? = null,
  val trackPeak: Float? = null,
  val albumGainDb: Float? = null,
  val albumPeak: Float? = null,
) {
  /**
   * [mode] で掛ける倍率。値の無い方はもう一方で代用し、どちらも無ければ1 (そのまま)。
   * ピーク値があれば、補正で1を超えない (音が割れない) ように抑える。
   */
  fun linearGain(mode: ReplayGainMode): Double {
    val (gain, peak) = when (mode) {
      ReplayGainMode.OFF -> return 1.0
      ReplayGainMode.TRACK -> (trackGainDb ?: albumGainDb) to (trackPeak ?: albumPeak)
      ReplayGainMode.ALBUM -> (albumGainDb ?: trackGainDb) to (albumPeak ?: trackPeak)
    }
    val linear = 10.0.pow((gain ?: return 1.0) / 20.0)
    return if (peak != null && peak > 0f) minOf(linear, 1.0 / peak) else linear
  }

  val isEmpty: Boolean get() = trackGainDb == null && albumGainDb == null

  companion object {
    /** タグの名前と値の組から読む。名前の大文字と小文字は区別しない ("REPLAYGAIN_TRACK_GAIN" = "-6.53 dB" など) */
    fun parse(tags: Map<String, String>): ReplayGain {
      val t = tags.mapKeys { it.key.uppercase() }
      fun number(key: String): Float? =
        t[key]?.trim()?.removeSuffix("dB")?.removeSuffix("DB")?.trim()?.replace(',', '.')?.toFloatOrNull()
          ?.takeIf { it.isFinite() }
      return ReplayGain(
        trackGainDb = number("REPLAYGAIN_TRACK_GAIN")?.coerceIn(-30f, 30f),
        trackPeak = number("REPLAYGAIN_TRACK_PEAK"),
        albumGainDb = number("REPLAYGAIN_ALBUM_GAIN")?.coerceIn(-30f, 30f),
        albumPeak = number("REPLAYGAIN_ALBUM_PEAK"),
      )
    }
  }
}
