package me.mallows.kanade.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DspTest {

  private val fs = 48_000.0

  @Test
  fun ピーキングは中心周波数で指定のゲインになる() {
    for (gain in listOf(-12.0, -3.0, 3.0, 6.0, 12.0)) {
      for (freq in EqSettings.BAND_FREQUENCIES) {
        val c = BiquadCoefficients.peaking(freq, gain, EqSettings.BAND_Q, fs)
        assertEquals("f=$freq gain=$gain", gain, c.magnitudeDb(freq, fs), 0.1)
      }
    }
  }

  @Test
  fun ピーキングは離れた周波数にほとんど影響しない() {
    val c = BiquadCoefficients.peaking(1000.0, 12.0, EqSettings.BAND_Q, fs)
    assertEquals(0.0, c.magnitudeDb(20.0, fs), 0.3)
    assertEquals(0.0, c.magnitudeDb(20_000.0, fs), 0.5)
  }

  @Test
  fun シェルフは端で指定のゲインになる() {
    val low = BiquadCoefficients.lowShelf(100.0, 6.0, fs)
    assertEquals(6.0, low.magnitudeDb(10.0, fs), 0.2)
    assertEquals(0.0, low.magnitudeDb(10_000.0, fs), 0.2)
    val high = BiquadCoefficients.highShelf(10_000.0, -6.0, fs)
    assertEquals(-6.0, high.magnitudeDb(23_000.0, fs), 0.5)
    assertEquals(0.0, high.magnitudeDb(100.0, fs), 0.2)
  }

  /** 正弦波を通して、出力の大きさ (dB) を測る */
  private fun gainOfSine(chain: DspChain, freq: Double, amplitude: Double = 0.1, sampleRate: Int = 48_000): Double {
    chain.configure(sampleRate, 2)
    val frames = sampleRate // 1秒
    val buf = FloatArray(frames * 2) { i -> (amplitude * sin(2 * PI * freq * (i / 2) / sampleRate)).toFloat() }
    chain.process(buf, buf.size)
    // 立ち上がりを除いた後半で測る
    val tail = buf.copyOfRange(buf.size / 2, buf.size)
    val rms = sqrt(tail.sumOf { (it * it).toDouble() } / tail.size)
    return 20 * log10(rms / (amplitude / sqrt(2.0)))
  }

  @Test
  fun 正弦波を通すと設定したバンドだけ大きくなる() {
    val chain = DspChain()
    chain.update(EqSettings(enabled = true, limiter = false).withBand(5, 6f)) // 1kHz +6dB
    assertEquals(6.0, gainOfSine(chain, 1000.0), 0.2)
    assertEquals(0.0, gainOfSine(chain, 62.0), 0.3)
  }

  @Test
  fun 無効のときは素通し() {
    val chain = DspChain()
    chain.update(EqSettings(enabled = false).withBand(5, 12f))
    assertEquals(0.0, gainOfSine(chain, 1000.0), 0.01)
  }

  @Test
  fun リミッターは大きくブーストしても1を超えない() {
    val chain = DspChain()
    chain.update(EqSettings(enabled = true, limiter = true, bands = List(10) { 15f }))
    chain.configure(48_000, 2)
    val buf = FloatArray(96_000) { i -> (0.9 * sin(2 * PI * 1000 * (i / 2) / 48_000.0)).toFloat() }
    chain.process(buf, buf.size)
    assertTrue(buf.all { abs(it) <= 1f && !it.isNaN() })
  }

  @Test
  fun 高いサンプルレートや極端な設定でも発散しない() {
    for (rate in listOf(44_100, 96_000, 192_000)) {
      val chain = DspChain()
      chain.update(EqSettings(enabled = true, bands = listOf(15f, -15f, 15f, -15f, 15f, -15f, 15f, -15f, 15f, -15f), bassDb = 12f, trebleDb = -12f))
      chain.configure(rate, 2)
      val buf = FloatArray(rate * 2) { i -> ((i * 7919 % 2001) / 1000.0 - 1.0).toFloat() * 0.5f } // ノイズに近い信号
      chain.process(buf, buf.size)
      assertTrue("rate=$rate", buf.all { it.isFinite() && abs(it) <= 1f })
    }
  }

  @Test
  fun 設定を保存用の文字列にして戻せる() {
    val s = EqSettings(enabled = true, preampDb = -3f, bassDb = 2f, trebleDb = -1.5f, limiter = false).withBand(3, 4.5f)
    assertEquals(s, EqSettings.decode(s.encode()))
    assertEquals(EqSettings(), EqSettings.decode("壊れた値"))
  }

  @Test
  fun 処理は再生に十分間に合う速さ() {
    // 192kHz・ステレオ・全バンド有効で10秒分を処理し、実時間の5%未満で終わることを確かめる (目安)
    val chain = DspChain()
    chain.update(EqSettings(enabled = true, bands = List(10) { 3f }, bassDb = 3f, trebleDb = 3f))
    chain.configure(192_000, 2)
    val buf = FloatArray(192_000 * 2) { 0.1f }
    repeat(3) { chain.process(buf, buf.size) } // ウォームアップ
    val start = System.nanoTime()
    repeat(10) { chain.process(buf, buf.size) }
    val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
    assertTrue("10秒分に ${elapsedMs}ms", elapsedMs < 500)
  }
}
