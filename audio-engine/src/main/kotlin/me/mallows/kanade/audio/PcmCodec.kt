package me.mallows.kanade.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 整数PCMとfloatの変換。
 * 16bit・24bitの値はfloatの仮数部 (24bit) に収まるため、float → 同じビット数の整数に戻すと元の値と一致する。
 */
object PcmCodec {

  /** 対応する形式 (Media3の C.ENCODING_* と同じ値) */
  const val PCM_16BIT = 2
  const val PCM_FLOAT = 4
  const val PCM_24BIT = 0x15
  const val PCM_32BIT = 0x16

  fun isSupported(encoding: Int): Boolean =
    encoding == PCM_16BIT || encoding == PCM_24BIT || encoding == PCM_32BIT || encoding == PCM_FLOAT

  fun bytesPerSample(encoding: Int): Int = when (encoding) {
    PCM_16BIT -> 2
    PCM_24BIT -> 3
    PCM_32BIT, PCM_FLOAT -> 4
    else -> throw IllegalArgumentException("未対応の形式: $encoding")
  }

  fun bitDepth(encoding: Int): Int = when (encoding) {
    PCM_16BIT -> 16
    PCM_24BIT -> 24
    PCM_32BIT, PCM_FLOAT -> 32
    else -> 0
  }

  /** [input] の残りを読み、-1 ~ 1のfloatとして [out] に入れる。戻り値はサンプル数 */
  fun decode(input: ByteBuffer, encoding: Int, out: FloatArray): Int {
    val b = input.order(ByteOrder.LITTLE_ENDIAN)
    val count = b.remaining() / bytesPerSample(encoding)
    when (encoding) {
      PCM_16BIT -> for (i in 0 until count) out[i] = b.getShort() / 32768f
      PCM_24BIT -> for (i in 0 until count) {
        val lo = b.get().toInt() and 0xFF
        val mid = b.get().toInt() and 0xFF
        val hi = b.get().toInt()
        out[i] = ((hi shl 16) or (mid shl 8) or lo) / 8388608f
      }
      PCM_32BIT -> for (i in 0 until count) out[i] = (b.getInt() / 2147483648.0).toFloat()
      PCM_FLOAT -> for (i in 0 until count) out[i] = b.getFloat()
    }
    return count
  }

  /** floatを [encoding] の整数PCMにして [out] に書く。範囲外は丸める */
  fun encode(samples: FloatArray, count: Int, encoding: Int, out: ByteBuffer) {
    val b = out.order(ByteOrder.LITTLE_ENDIAN)
    when (encoding) {
      PCM_16BIT -> for (i in 0 until count) {
        b.putShort(toInt(samples[i], 32768.0, -32768, 32767).toShort())
      }
      PCM_24BIT -> for (i in 0 until count) {
        val v = toInt(samples[i], 8388608.0, -8388608, 8388607)
        b.put(v.toByte())
        b.put((v shr 8).toByte())
        b.put((v shr 16).toByte())
      }
      PCM_32BIT -> for (i in 0 until count) {
        b.putInt(toInt(samples[i], 2147483648.0, Int.MIN_VALUE, Int.MAX_VALUE))
      }
      PCM_FLOAT -> for (i in 0 until count) b.putFloat(samples[i])
      else -> throw IllegalArgumentException("未対応の形式: $encoding")
    }
  }

  private fun toInt(sample: Float, scale: Double, min: Int, max: Int): Int {
    val v = Math.round(sample * scale)
    return v.coerceIn(min.toLong(), max.toLong()).toInt()
  }
}
