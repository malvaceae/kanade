package me.mallows.kanade.playback.output

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.AudioOutputProvider.FormatConfig
import androidx.media3.exoplayer.audio.AudioOutputProvider.OutputConfig
import androidx.media3.exoplayer.audio.ForwardingAudioOutput
import androidx.media3.exoplayer.audio.ForwardingAudioOutputProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder
import me.mallows.kanade.audio.PcmCodec

/**
 * AudioTrackを開く部分。ビットパーフェクト出力のときは、Media3が出すfloatを
 * ミキサーと同じ整数PCM (16bit・24bit・32bit) に戻してからAudioTrackに書く。
 *
 * Media3の [OutputConfig] の形式は書き換えない (音量のランプ処理などが形式を見てバッファを読むため)。
 * AudioTrackだけを別の形式で作り、書き込みのときに変換する。
 */
@OptIn(UnstableApi::class)
class KanadeOutputProvider(
  base: AudioOutputProvider,
  private val monitor: OutputMonitor,
) : ForwardingAudioOutputProvider(base) {

  /** AudioTrackに渡す形式。nullならMedia3が決めた形式のまま */
  @Volatile var targetEncoding: Int? = null

  override fun getAudioOutput(config: OutputConfig): AudioOutput {
    val from = config.encoding
    val target = targetEncoding
    val channels = Integer.bitCount(config.channelMask)
    if (target == null || target == from || config.isOffload || config.isTunneling ||
      !PcmCodec.isSupported(from) || !PcmCodec.isSupported(target)
    ) {
      report(from, config.sampleRate, channels)
      // 形式が同じでも、ビットパーフェクトの切り替え後に使い回されないよう包む
      return ConvertingAudioOutput(super.getAudioOutput(config), from, from) { targetEncoding == target }
    }
    val samples = config.bufferSize / PcmCodec.bytesPerSample(from)
    val converted = config.buildUpon()
      .setEncoding(target)
      .setBufferSize(samples * PcmCodec.bytesPerSample(target))
      .build()
    val output = super.getAudioOutput(converted)
    report(target, config.sampleRate, channels)
    return ConvertingAudioOutput(output, from, target) { targetEncoding == target }
  }

  private fun report(encoding: Int, sampleRate: Int, channels: Int) {
    monitor.update {
      it.copy(
        output = StreamFormat(
          bits = PcmCodec.bitDepth(encoding),
          sampleRate = sampleRate,
          channels = channels,
          float = encoding == PcmCodec.PCM_FLOAT,
        ),
      )
    }
  }
}

/** [from] のPCMを [to] に変換してから書き込む。同じ形式ならそのまま書く */
@OptIn(UnstableApi::class)
private class ConvertingAudioOutput(
  output: AudioOutput,
  private val from: Int,
  private val to: Int,
  private val stillValid: () -> Boolean,
) : ForwardingAudioOutput(output) {

  private val inBytes = if (from == to) 0 else PcmCodec.bytesPerSample(from)
  private val outBytes = if (from == to) 0 else PcmCodec.bytesPerSample(to)
  private var work = FloatArray(0)
  private var converted: ByteBuffer = ByteBuffer.allocateDirect(0)
  private var pending = false
  private var source: ByteBuffer? = null
  private var sourceStart = 0
  private var expectedPosition = 0

  override fun write(buffer: ByteBuffer, encodedAccessUnitCount: Int, presentationTimeUs: Long): Boolean {
    if (from == to) return super.write(buffer, encodedAccessUnitCount, presentationTimeUs)
    // 前回書き切れなかったバッファの続きでなければ、新しく変換する
    if (!pending || source !== buffer || buffer.position() != expectedPosition) {
      val samples = buffer.remaining() / inBytes
      if (work.size < samples) work = FloatArray(samples)
      PcmCodec.decode(buffer.duplicate(), from, work)
      if (converted.capacity() < samples * outBytes) {
        converted = ByteBuffer.allocateDirect(samples * outBytes).order(ByteOrder.nativeOrder())
      }
      converted.clear()
      PcmCodec.encode(work, samples, to, converted)
      converted.flip()
      source = buffer
      sourceStart = buffer.position()
      pending = true
    }
    val done = super.write(converted, encodedAccessUnitCount, presentationTimeUs)
    // 書けたサンプル数だけ、元のバッファも進める (Media3は残りのバイト数で書き込み量を数える)
    val written = converted.position() / outBytes
    buffer.position(sourceStart + written * inBytes)
    expectedPosition = buffer.position()
    if (done) {
      buffer.position(buffer.limit())
      pending = false
      source = null
    }
    return done
  }

  override fun flush() {
    pending = false
    source = null
    super.flush()
  }

  override fun canReuseAudioOutput(
    currentConfig: OutputConfig,
    newFormat: FormatConfig,
    newConfig: OutputConfig,
  ): Boolean = stillValid() && super.canReuseAudioOutput(currentConfig, newFormat, newConfig)
}
