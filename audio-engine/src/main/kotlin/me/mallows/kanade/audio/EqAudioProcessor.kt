package me.mallows.kanade.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * PCM (16bit・24bit・32bit・float) を受け取り、floatにして [DspChain] を通し、floatで出す。
 * 16bitに落とさずにEQをかけるため、Media3の標準の処理の並びではなく、出力部品の手前で使う。
 * EQが無効でも常に通る (設定の切り替えを再生を止めずに反映するため)。
 */
@OptIn(UnstableApi::class)
class EqAudioProcessor(private val chain: DspChain) : BaseAudioProcessor() {

  private var inputEncoding = PcmCodec.PCM_16BIT
  private var work = FloatArray(0)

  override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
    val encoding = inputAudioFormat.encoding
    if (!PcmCodec.isSupported(encoding)) throw UnhandledAudioFormatException(inputAudioFormat)
    inputEncoding = encoding
    chain.configure(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
    return AudioFormat(inputAudioFormat.sampleRate, inputAudioFormat.channelCount, C.ENCODING_PCM_FLOAT)
  }

  override fun queueInput(inputBuffer: ByteBuffer) {
    val bytes = inputBuffer.remaining()
    if (bytes == 0) return
    val max = bytes / PcmCodec.bytesPerSample(inputEncoding)
    if (work.size < max) work = FloatArray(max)
    val count = PcmCodec.decode(inputBuffer, inputEncoding, work)
    chain.process(work, count)
    val output = replaceOutputBuffer(count * 4)
    PcmCodec.encode(work, count, PcmCodec.PCM_FLOAT, output)
    output.flip()
  }

  override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
    chain.reset()
  }
}
