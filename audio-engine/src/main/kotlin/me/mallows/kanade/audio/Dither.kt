package me.mallows.kanade.audio

/**
 * floatを少ないビット数の整数にするときのディザ (TPDF: 三角分布)。
 * 丸めの誤差が音に相関したひずみにならないよう、1段分の大きさの雑音を足してから丸める。
 */
class Dither(seed: Long = 0x2545F4914F6CDD1DL) {
  private var state = if (seed == 0L) 1L else seed

  /** -0.5 ~ 0.5 の一様乱数 (xorshift64) */
  private fun uniform(): Float {
    var x = state
    x = x xor (x shl 13)
    x = x xor (x ushr 7)
    x = x xor (x shl 17)
    state = x
    return ((x ushr 40).toInt() / 16777216f) - 0.5f
  }

  /** [samples] の先頭 [count] 個に、[bits] ビットへ丸める前のディザをその場で足す */
  fun apply(samples: FloatArray, count: Int, bits: Int) {
    val lsb = 1f / (1 shl (bits - 1))
    for (i in 0 until count) samples[i] += (uniform() + uniform()) * lsb
  }
}
