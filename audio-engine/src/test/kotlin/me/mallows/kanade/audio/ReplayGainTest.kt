package me.mallows.kanade.audio

import kotlin.math.log10
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayGainTest {

  private fun db(linear: Double) = 20 * log10(linear)

  @Test
  fun タグの書き方の違いを読める() {
    val rg = ReplayGain.parse(
      mapOf(
        "replaygain_track_gain" to "-6.53 dB",
        "REPLAYGAIN_TRACK_PEAK" to "0.988",
        "ReplayGain_Album_Gain" to "+1,5 dB",
      ),
    )
    assertEquals(-6.53f, rg.trackGainDb!!, 1e-4f)
    assertEquals(0.988f, rg.trackPeak!!, 1e-4f)
    assertEquals(1.5f, rg.albumGainDb!!, 1e-4f)
  }

  @Test
  fun 曲ごととアルバムごとで値を選び無い方は代用する() {
    val rg = ReplayGain(trackGainDb = -6f, albumGainDb = -3f)
    assertEquals(-6.0, db(rg.linearGain(ReplayGainMode.TRACK)), 1e-6)
    assertEquals(-3.0, db(rg.linearGain(ReplayGainMode.ALBUM)), 1e-6)
    assertEquals(-6.0, db(ReplayGain(trackGainDb = -6f).linearGain(ReplayGainMode.ALBUM)), 1e-6)
    assertEquals(1.0, rg.linearGain(ReplayGainMode.OFF), 0.0)
    assertEquals(1.0, ReplayGain().linearGain(ReplayGainMode.TRACK), 0.0)
  }

  @Test
  fun ピークを超えて持ち上げない() {
    // +6dBの指定でも、ピークが0.8なら1/0.8倍 (約+1.9dB) までにする
    val gain = ReplayGain(trackGainDb = 6f, trackPeak = 0.8f).linearGain(ReplayGainMode.TRACK)
    assertEquals(1.25, gain, 1e-6)
    assertTrue(gain * 0.8f <= 1.0 + 1e-6)
  }
}
