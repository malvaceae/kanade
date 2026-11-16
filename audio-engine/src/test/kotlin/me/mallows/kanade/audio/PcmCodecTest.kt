package me.mallows.kanade.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PcmCodecTest {

  private fun roundTrip(encoding: Int, bytes: ByteArray): ByteArray {
    val input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val floats = FloatArray(bytes.size)
    val count = PcmCodec.decode(input, encoding, floats)
    val out = ByteBuffer.allocate(count * PcmCodec.bytesPerSample(encoding))
    PcmCodec.encode(floats, count, encoding, out)
    return out.array()
  }

  @Test
  fun 十六ビットはfloatを経由しても元の値に戻る() {
    val b = ByteBuffer.allocate(65536 * 2).order(ByteOrder.LITTLE_ENDIAN)
    for (v in Short.MIN_VALUE..Short.MAX_VALUE) b.putShort(v.toShort())
    val bytes = b.array()
    assertArrayEquals(bytes, roundTrip(PcmCodec.PCM_16BIT, bytes))
  }

  @Test
  fun 二十四ビットはfloatを経由しても元の値に戻る() {
    // 端の値と、全範囲から間引いた値
    val values = (listOf(-8388608, -8388607, -1, 0, 1, 8388606, 8388607) + (-8388608..8388607 step 257)).toList()
    val b = ByteBuffer.allocate(values.size * 3)
    for (v in values) {
      b.put(v.toByte()); b.put((v shr 8).toByte()); b.put((v shr 16).toByte())
    }
    val bytes = b.array()
    assertArrayEquals(bytes, roundTrip(PcmCodec.PCM_24BIT, bytes))
  }

  @Test
  fun 二十四ビットの符号と大きさを正しく読む() {
    val bytes = byteArrayOf(0x00, 0x00, 0x80.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F, 0x00, 0x00, 0x40)
    val out = FloatArray(3)
    PcmCodec.decode(ByteBuffer.wrap(bytes), PcmCodec.PCM_24BIT, out)
    assertEquals(-1f, out[0], 0f)
    assertEquals(8388607f / 8388608f, out[1], 0f)
    assertEquals(0.5f, out[2], 0f)
  }

  @Test
  fun 範囲を超えたfloatは端の値に丸める() {
    val out = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
    PcmCodec.encode(floatArrayOf(1.5f, -2f), 2, PcmCodec.PCM_16BIT, out)
    out.flip()
    assertEquals(32767, out.getShort().toInt())
    assertEquals(-32768, out.getShort().toInt())
  }
}
