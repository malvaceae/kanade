package me.mallows.kanade.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DitherTest {

  @Test
  fun 雑音は1段分の大きさで平均は0() {
    val n = 200_000
    val s = FloatArray(n)
    Dither().apply(s, n, 16)
    val lsb = 1.0 / 32768
    assertTrue(s.all { abs(it) <= lsb + 1e-9 })
    assertEquals(0.0, s.average(), lsb * 0.01)
  }

  @Test
  fun 一段より小さい音もディザがあれば平均として残る() {
    // 振幅0.4段の正弦波は、そのまま丸めると0になって消えるが、ディザがあれば平均として残る
    val n = 48_000
    val lsb = 1f / 32768
    val signal = FloatArray(n) { (0.4f * lsb * sin(2 * PI * 100.0 * it / 48_000)).toFloat() }
    fun roundTrip(dither: Boolean): FloatArray {
      val work = signal.copyOf()
      if (dither) Dither().apply(work, n, 16)
      val buf = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
      PcmCodec.encode(work, n, PcmCodec.PCM_16BIT, buf)
      buf.flip()
      val out = FloatArray(n)
      PcmCodec.decode(buf, PcmCodec.PCM_16BIT, out)
      return out
    }
    // 正弦波との相関 (含まれている成分の大きさ)
    fun correlation(x: FloatArray) = x.indices.sumOf { (x[it] * signal[it]).toDouble() }
    assertEquals(0.0, correlation(roundTrip(false)), 0.0)
    assertTrue(correlation(roundTrip(true)) > correlation(signal) * 0.5)
  }
}
