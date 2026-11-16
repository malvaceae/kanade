package me.mallows.kanade.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/**
 * プリアンプ → 10バンドEQ → Bass/Treble → リミッター の順に処理する。
 * 再生スレッドから[process]を呼び、設定は別スレッドから[update]で差し替える (次の処理から反映)。
 *
 * 設定が変わったときは、古い設定と新しい設定の両方で約20ms処理し、出力を少しずつ入れ替える。
 * 係数を急に替えるとプツッと鳴るため。オンとオフの切り替えも同じようになめらかにする。
 */
class DspChain {

  /** 設定と、それから計算した係数の組。まとめて差し替える */
  private class Plan(
    val settings: EqSettings,
    val sampleRate: Double,
    val preampGain: Double,
    val filters: Array<BiquadCoefficients>,
  ) {
    /** 素通しでないフィルターの番号。無効のときは空 */
    val active: IntArray =
      if (settings.enabled) filters.indices.filter { !filters[it].isIdentity }.toIntArray() else IntArray(0)
    val gain: Double = if (settings.enabled) preampGain else 1.0
    val limiter: Boolean = settings.enabled && settings.limiter
  }

  @Volatile private var plan: Plan = buildPlan(EqSettings(), 48_000.0)
  /** 再生スレッドで使っている設定 */
  private var applied: Plan = plan

  private var channels = 2
  private var states: Array<Array<BiquadState>> = emptyArray()
  /** 入れ替え中の古い設定と、その状態 */
  private var fading: Plan? = null
  private var fadeStates: Array<Array<BiquadState>> = emptyArray()
  private var fadeLength = 960
  private var fadePosition = 0

  // リミッター
  private var limiterGain = 1.0
  private var releaseCoef = 0.0

  val settings: EqSettings get() = plan.settings

  /** 音声の形式が決まったときに呼ぶ */
  fun configure(sampleRate: Int, channelCount: Int) {
    channels = channelCount
    plan = buildPlan(plan.settings, sampleRate.toDouble())
    applied = plan
    states = newStates()
    fadeStates = newStates()
    releaseCoef = exp(-1.0 / (0.05 * sampleRate)) // 50ms で戻る
    fadeLength = (0.02 * sampleRate).toInt().coerceAtLeast(1) // 20ms で入れ替える
    reset()
  }

  fun update(settings: EqSettings) {
    plan = buildPlan(settings, plan.sampleRate)
  }

  fun reset() {
    states.forEach { s -> s.forEach(BiquadState::reset) }
    applied = plan
    fading = null
    limiterGain = 1.0
  }

  private fun newStates() = Array(plan.filters.size) { Array(channels) { BiquadState() } }

  /** 新しい設定に切り替える。今の状態を古い設定用に移し、新しい設定は同じ状態から始める */
  private fun beginFade(next: Plan) {
    if (states.size != next.filters.size) states = newStates()
    if (fadeStates.size != states.size) fadeStates = newStates()
    for (k in states.indices) for (c in 0 until channels) fadeStates[k][c].copyFrom(states[k][c])
    // 今まで使っていなかったフィルターは、古い値が残っているので空にする
    val wasActive = applied.active
    for (k in next.active) if (k !in wasActive) states[k].forEach(BiquadState::reset)
    fading = applied
    fadePosition = 0
    applied = next
  }

  /** [samples] (チャンネル順に並んだfloat) をその場で処理する */
  fun process(samples: FloatArray, count: Int) {
    val latest = plan
    // 入れ替え中に届いた変更は、今の入れ替えが終わってから反映する
    if (latest !== applied && fading == null) beginFade(latest)
    val p = applied
    if (states.size != p.filters.size) states = newStates()
    val frames = count / channels
    val threshold = 0.966 // -0.3dBFS
    for (f in 0 until frames) {
      val base = f * channels
      val old = fading
      val mix = if (old != null) (fadePosition + 1).toDouble() / fadeLength else 1.0
      var peak = 0.0
      for (c in 0 until channels) {
        val input = samples[base + c].toDouble()
        var x = input * p.gain
        for (k in p.active) x = states[k][c].process(p.filters[k], x)
        if (old != null) {
          var y = input * old.gain
          for (k in old.active) y = fadeStates[k][c].process(old.filters[k], y)
          x = y + (x - y) * mix
        }
        samples[base + c] = x.toFloat()
        peak = maxOf(peak, abs(x))
      }
      if (old != null && ++fadePosition >= fadeLength) fading = null
      val useLimiter = p.limiter || old?.limiter == true
      if (useLimiter) {
        // 超えそうなら即座に下げ、戻すときはゆっくり戻す
        val needed = if (peak > threshold) threshold / peak else 1.0
        limiterGain = if (needed < limiterGain) needed else needed + (limiterGain - needed) * releaseCoef
      } else {
        limiterGain = 1.0
      }
      for (c in 0 until channels) {
        samples[base + c] = (samples[base + c] * limiterGain).coerceIn(-1.0, 1.0).toFloat()
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
