package me.mallows.kanade.audio

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/**
 * Media3の再生処理に [DspChain] を差し込む。
 * 16bitまたはfloatのPCMを受け取り、floatで計算してfloatで出す。
 * EQが無効でも常に通る (設定の切り替えを再生を止めずに反映するため)。
 */
@OptIn(UnstableApi::class)
class EqAudioProcessor(private val chain: DspChain) : BaseAudioProcessor() {

  private var inputEncoding = C.ENCODING_PCM_16BIT
  private var work = FloatArray(0)

  override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
    val encoding = inputAudioFormat.encoding
    if (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) {
      throw UnhandledAudioFormatException(inputAudioFormat)
    }
    inputEncoding = encoding
    chain.configure(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
    timber.log.Timber.d(
      "EQ処理の形式: %dHz %dch 入力=%s 出力=float",
      inputAudioFormat.sampleRate,
      inputAudioFormat.channelCount,
      if (encoding == C.ENCODING_PCM_16BIT) "16bit" else "float",
    )
    return AudioFormat(inputAudioFormat.sampleRate, inputAudioFormat.channelCount, C.ENCODING_PCM_FLOAT)
  }

  override fun queueInput(inputBuffer: ByteBuffer) {
    val bytes = inputBuffer.remaining()
    if (bytes == 0) return
    val count = if (inputEncoding == C.ENCODING_PCM_16BIT) bytes / 2 else bytes / 4
    if (work.size < count) work = FloatArray(count)
    if (inputEncoding == C.ENCODING_PCM_16BIT) {
      for (i in 0 until count) work[i] = inputBuffer.getShort() / 32768f
    } else {
      for (i in 0 until count) work[i] = inputBuffer.getFloat()
    }
    chain.process(work, count)
    val output = replaceOutputBuffer(count * 4)
    for (i in 0 until count) output.putFloat(work[i])
    output.flip()
  }

  override fun onFlush() {
    chain.reset()
  }
}
