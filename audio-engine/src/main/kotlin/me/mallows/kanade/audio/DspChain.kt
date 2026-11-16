package me.mallows.kanade.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/**
 * プリアンプ → 10バンドEQ → Bass/Treble → リミッター の順に処理する。
 * 再生スレッドから[process]を呼び、設定は別スレッドから[update]で差し替える (次の処理から反映)。
 */
class DspChain {

  /** 設定と、それから計算した係数の組。まとめて差し替える */
  private class Plan(
    val settings: EqSettings,
    val sampleRate: Double,
    val preampGain: Double,
    val filters: Array<BiquadCoefficients>,
  ) {
    /** 素通しでないフィルターの番号 */
    val active: IntArray = filters.indices.filter { !filters[it].isIdentity }.toIntArray()
  }

  @Volatile private var plan: Plan = buildPlan(EqSettings(), 48_000.0)

  private var channels = 2
  private var states: Array<Array<BiquadState>> = emptyArray()
  /** 1フレーム分の作業領域 (再生中に配列を作らないよう使い回す) */
  private var frame = DoubleArray(2)

  // リミッター
  private var limiterGain = 1.0
  private var releaseCoef = 0.0

  /** プリアンプの急な変化でプツッと鳴らないよう、少しずつ近づける */
  private var currentPreamp = 1.0
  private var preampStep = 0.0

  val settings: EqSettings get() = plan.settings

  /** 音声の形式が決まったときに呼ぶ */
  fun configure(sampleRate: Int, channelCount: Int) {
    channels = channelCount
    frame = DoubleArray(channelCount)
    plan = buildPlan(plan.settings, sampleRate.toDouble())
    states = Array(plan.filters.size) { Array(channelCount) { BiquadState() } }
    releaseCoef = exp(-1.0 / (0.05 * sampleRate)) // 50ms で戻る
    preampStep = 1.0 / (0.02 * sampleRate) // 20ms で目標に達する
    reset()
  }

  fun update(settings: EqSettings) {
    plan = buildPlan(settings, plan.sampleRate)
  }

  fun reset() {
    states.forEach { s -> s.forEach(BiquadState::reset) }
    limiterGain = 1.0
    currentPreamp = plan.preampGain
  }

  /** [samples] (チャンネル順に並んだfloat) をその場で処理する */
  fun process(samples: FloatArray, count: Int) {
    val p = plan
    val s = p.settings
    if (!s.enabled) {
      // 無効のときもリミッターで±1を超えないようにだけしておく
      for (i in 0 until count) samples[i] = samples[i].coerceIn(-1f, 1f)
      return
    }
    val filters = p.filters
    val activeFilters = p.active
    if (states.size != filters.size) states = Array(filters.size) { Array(channels) { BiquadState() } }
    val frames = count / channels
    val threshold = 0.966 // -0.3dBFS
    for (f in 0 until frames) {
      // プリアンプを少しずつ目標に寄せる
      val target = p.preampGain
      currentPreamp = when {
        currentPreamp < target -> minOf(target, currentPreamp + preampStep * target.coerceAtLeast(0.01))
        currentPreamp > target -> maxOf(target, currentPreamp - preampStep * currentPreamp)
        else -> currentPreamp
      }
      var peak = 0.0
      val base = f * channels
      val out = frame
      for (c in 0 until channels) {
        var x = samples[base + c] * currentPreamp
        for (k in activeFilters) x = states[k][c].process(filters[k], x)
        out[c] = x
        peak = maxOf(peak, abs(x))
      }
      if (s.limiter) {
        // 超えそうなら即座に下げ、戻すときはゆっくり戻す
        val needed = if (peak > threshold) threshold / peak else 1.0
        limiterGain = if (needed < limiterGain) needed else needed + (limiterGain - needed) * releaseCoef
      } else {
        limiterGain = 1.0
      }
      for (c in 0 until channels) {
        samples[base + c] = (out[c] * limiterGain).coerceIn(-1.0, 1.0).toFloat()
      }
    }
  }

  companion object {
    private fun buildPlan(settings: EqSettings, sampleRate: Double): Plan {
      val bands = EqSettings.BAND_FREQUENCIES.mapIndexed { i, freq ->
        BiquadCoefficients.peaking(freq, settings.bands[i].toDouble(), EqSettings.BAND_Q, sampleRate)
      }
      val tone = listOf(
        BiquadCoefficients.lowShelf(EqSettings.BASS_FREQUENCY, settings.bassDb.toDouble(), sampleRate),
        BiquadCoefficients.highShelf(EqSettings.TREBLE_FREQUENCY, settings.trebleDb.toDouble(), sampleRate),
      )
      return Plan(
        settings = settings,
        sampleRate = sampleRate,
        preampGain = 10.0.pow(settings.preampDb / 20.0),
        filters = (bands + tone).toTypedArray(),
      )
    }

    /** 設定全体の周波数特性 (dB)。画面のカーブ表示用 */
    fun responseDb(settings: EqSettings, freq: Double, sampleRate: Double = 48_000.0): Double {
      val p = buildPlan(settings, sampleRate)
      return settings.preampDb + p.filters.sumOf { it.magnitudeDb(freq, sampleRate) }
    }
  }
}
