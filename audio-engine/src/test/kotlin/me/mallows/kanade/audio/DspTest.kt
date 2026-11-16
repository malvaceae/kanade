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

  /** 再生中に設定を変えたとき、隣り合うサンプルの差の最大 (変更の前後と、落ち着いた後) */
  private fun jumpsAroundChange(before: EqSettings, after: EqSettings): Pair<Double, Double> {
    val fs = 48_000
    val chain = DspChain()
    chain.update(before)
    chain.configure(fs, 2)
    val block = 480
    var phase = 0
    var last = 0f
    var changeMax = 0.0
    var steadyMax = 0.0
    for (b in 0 until 200) { // 2秒
      if (b == 100) chain.update(after)
      val buf = FloatArray(block * 2) { i -> (0.3 * sin(2 * PI * 200.0 * (phase + i / 2) / fs)).toFloat() }
      phase += block
      chain.process(buf, buf.size)
      for (i in buf.indices step 2) {
        val d = abs((buf[i] - last).toDouble())
        last = buf[i]
        if (b in 100..104) changeMax = maxOf(changeMax, d)
        if (b >= 150) steadyMax = maxOf(steadyMax, d)
      }
    }
    return changeMax to steadyMax
  }

  @Test
  fun 再生中にバンドを大きく変えても波形が跳ねない() {
    val flat = EqSettings(enabled = true, limiter = false)
    val boosted = flat.withBand(3, 12f).copy(preampDb = -6f) // 250Hz +12dB
    val (change, steady) = jumpsAroundChange(flat, boosted)
    // 変更の途中の変化は、変更前後どちらの音より大きく跳ねない
    val (_, before) = jumpsAroundChange(flat, flat)
    assertTrue("変更中 $change / 前 $before / 後 $steady", change <= maxOf(before, steady) * 1.05)
  }

  @Test
  fun オンとオフを切り替えても波形が跳ねない() {
    val on = EqSettings(enabled = true, limiter = false).withBand(3, 12f)
    val off = on.copy(enabled = false)
    val (change, steady) = jumpsAroundChange(on, off)
    val (_, before) = jumpsAroundChange(on, on)
    assertTrue("変更中 $change / 前 $before / 後 $steady", change <= maxOf(before, steady) * 1.05)
  }

  /** 左だけに音がある信号を通して、落ち着いた後の左右の最大値を返す */
  private fun leftOnlyThrough(settings: EqSettings): Pair<Double, Double> {
    val chain = DspChain()
    chain.update(settings)
    chain.configure(48_000, 2)
    val buf = FloatArray(48_000 * 2) { i -> if (i % 2 == 0) (0.5 * sin(2 * PI * 440.0 * (i / 2) / 48_000)).toFloat() else 0f }
    chain.process(buf, buf.size)
    val tail = buf.size / 2
    var l = 0.0
    var r = 0.0
    for (i in tail until buf.size step 2) {
      l = maxOf(l, abs(buf[i].toDouble()))
      r = maxOf(r, abs(buf[i + 1].toDouble()))
    }
    return l to r
  }

  @Test
  fun モノラルは左右を混ぜて同じにする() {
    val (l, r) = leftOnlyThrough(EqSettings(mono = true))
    assertEquals(0.25, l, 0.01)
    assertEquals(l, r, 1e-9)
  }

  @Test
  fun バランスは片側だけを下げる() {
    val (l, r) = leftOnlyThrough(EqSettings(mono = true, balance = -1f))
    assertEquals(0.25, l, 0.01)
    assertEquals(0.0, r, 1e-9)
    val (l2, _) = leftOnlyThrough(EqSettings(balance = 0.5f))
    assertEquals(0.25, l2, 0.01)
  }

  @Test
  fun バランスとモノラルを足す前の保存データも読める() {
    val old = "true;-3.0;0.0;0.0;true;6.0,5.0,4.0,2.0,0.0,0.0,0.0,0.0,0.0,0.0"
    val s = EqSettings.decode(old)
    assertEquals(-3f, s.preampDb)
    assertEquals(0f, s.balance)
    assertEquals(false, s.mono)
    assertEquals(s.copy(balance = 0.3f, mono = true), EqSettings.decode(s.copy(balance = 0.3f, mono = true).encode()))
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
