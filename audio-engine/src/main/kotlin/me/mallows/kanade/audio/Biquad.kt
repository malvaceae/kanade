package me.mallows.kanade.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 2次IIRフィルター (biquad) の係数。a0で正規化済み。
 * 係数の式は RBJ Audio EQ Cookbook による。
 */
data class BiquadCoefficients(
  val b0: Double,
  val b1: Double,
  val b2: Double,
  val a1: Double,
  val a2: Double,
) {
  /** 素通し (何もしない) か */
  val isIdentity: Boolean get() = b0 == 1.0 && b1 == 0.0 && b2 == 0.0 && a1 == 0.0 && a2 == 0.0

  /** 周波数[freq]での増幅量 (dB) */
  fun magnitudeDb(freq: Double, sampleRate: Double): Double {
    val w = 2 * PI * freq / sampleRate
    val cw = cos(w)
    val c2w = cos(2 * w)
    val sw = sin(w)
    val s2w = sin(2 * w)
    val numRe = b0 + b1 * cw + b2 * c2w
    val numIm = -(b1 * sw + b2 * s2w)
    val denRe = 1 + a1 * cw + a2 * c2w
    val denIm = -(a1 * sw + a2 * s2w)
    val num = numRe * numRe + numIm * numIm
    val den = denRe * denRe + denIm * denIm
    return 10 * log10(num / den)
  }

  companion object {
    val IDENTITY = BiquadCoefficients(1.0, 0.0, 0.0, 0.0, 0.0)

    private fun normalize(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
      BiquadCoefficients(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)

    /** ピーキング (指定の周波数の周りだけを上げ下げする) */
    fun peaking(freq: Double, gainDb: Double, q: Double, sampleRate: Double): BiquadCoefficients {
      if (gainDb == 0.0 || freq >= sampleRate / 2) return IDENTITY
      val a = 10.0.pow(gainDb / 40)
      val w0 = 2 * PI * freq / sampleRate
      val alpha = sin(w0) / (2 * q)
      val cw = cos(w0)
      return normalize(
        b0 = 1 + alpha * a, b1 = -2 * cw, b2 = 1 - alpha * a,
        a0 = 1 + alpha / a, a1 = -2 * cw, a2 = 1 - alpha / a,
      )
    }

    /** ローシェルフ (指定の周波数より下をまとめて上げ下げする)。Bass用 */
    fun lowShelf(freq: Double, gainDb: Double, sampleRate: Double, slope: Double = 1.0): BiquadCoefficients {
      if (gainDb == 0.0) return IDENTITY
      val a = 10.0.pow(gainDb / 40)
      val w0 = 2 * PI * freq / sampleRate
      val cw = cos(w0)
      val alpha = sin(w0) / 2 * sqrt((a + 1 / a) * (1 / slope - 1) + 2)
      val sa = 2 * sqrt(a) * alpha
      return normalize(
        b0 = a * ((a + 1) - (a - 1) * cw + sa),
        b1 = 2 * a * ((a - 1) - (a + 1) * cw),
        b2 = a * ((a + 1) - (a - 1) * cw - sa),
        a0 = (a + 1) + (a - 1) * cw + sa,
        a1 = -2 * ((a - 1) + (a + 1) * cw),
        a2 = (a + 1) + (a - 1) * cw - sa,
      )
    }

    /** ハイシェルフ (指定の周波数より上をまとめて上げ下げする)。Treble用 */
    fun highShelf(freq: Double, gainDb: Double, sampleRate: Double, slope: Double = 1.0): BiquadCoefficients {
      if (gainDb == 0.0 || freq >= sampleRate / 2) return IDENTITY
      val a = 10.0.pow(gainDb / 40)
      val w0 = 2 * PI * freq / sampleRate
      val cw = cos(w0)
      val alpha = sin(w0) / 2 * sqrt((a + 1 / a) * (1 / slope - 1) + 2)
      val sa = 2 * sqrt(a) * alpha
      return normalize(
        b0 = a * ((a + 1) + (a - 1) * cw + sa),
        b1 = -2 * a * ((a - 1) + (a + 1) * cw),
        b2 = a * ((a + 1) + (a - 1) * cw - sa),
        a0 = (a + 1) - (a - 1) * cw + sa,
        a1 = 2 * ((a - 1) - (a + 1) * cw),
        a2 = (a + 1) - (a - 1) * cw - sa,
      )
    }
  }
}

/** 1チャンネル分のフィルターの状態 (転置直接形II)。計算はdoubleで行い、低い周波数でも精度を保つ */
class BiquadState {
  private var z1 = 0.0
  private var z2 = 0.0

  fun process(c: BiquadCoefficients, x: Double): Double {
    val y = c.b0 * x + z1
    z1 = c.b1 * x - c.a1 * y + z2
    z2 = c.b2 * x - c.a2 * y
    return y
  }

  fun reset() {
    z1 = 0.0
    z2 = 0.0
  }

  fun copyFrom(other: BiquadState) {
    z1 = other.z1
    z2 = other.z2
  }
}
