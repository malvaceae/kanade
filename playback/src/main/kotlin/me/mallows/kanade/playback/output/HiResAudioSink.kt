package me.mallows.kanade.playback.output

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.AudioSink.AudioSinkConfig
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer
import me.mallows.kanade.audio.DspChain
import me.mallows.kanade.audio.EqAudioProcessor
import me.mallows.kanade.audio.PcmCodec
import timber.log.Timber

/**
 * Media3の出力部品 (float出力を有効にしたDefaultAudioSink) の手前で、PCMをfloatにしてEQを通す。
 *
 * DefaultAudioSinkは、float出力を有効にするとハイレゾ音源で独自の処理を通さず、
 * 無効にすると独自の処理の前に16bitへ変換する。そのため、EQはここで自分で通す。
 * ビットパーフェクト出力のときはEQを通さず、そのまま渡す。
 */
@OptIn(UnstableApi::class)
class HiResAudioSink(
  sink: AudioSink,
  dsp: DspChain,
  private val provider: KanadeOutputProvider,
  private val router: OutputRouter,
  private val monitor: OutputMonitor,
) : ForwardingAudioSink(sink) {

  /** デコーダーに入る前の形式 (FLACやWAVならビット深度が分かる)。レンダラーが再生スレッドで設定する */
  @Volatile var sourceFormat: Format? = null

  private val eq = EqAudioProcessor(dsp)
  private var processing = false
  private var pending: ByteBuffer? = null
  private var pendingTimeUs = 0L

  override fun configure(audioSinkConfig: AudioSinkConfig) {
    val format = audioSinkConfig.format
    pending = null
    val source = sourceFormat
    if (format.sampleMimeType != MimeTypes.AUDIO_RAW || !PcmCodec.isSupported(format.pcmEncoding)) {
      processing = false
      provider.targetEncoding = null
      super.configure(audioSinkConfig)
      report(source, format, floatProcessing = false, Negotiation(BitPerfectState.UNAVAILABLE))
      return
    }
    val sourceEncoding = source?.pcmEncoding?.takeIf { it != Format.NO_VALUE }
    val negotiation = router.negotiate(sourceEncoding, format.pcmEncoding, format.sampleRate, format.channelCount)
    provider.targetEncoding = negotiation.targetEncoding
    processing = !negotiation.active
    if (processing) {
      eq.configure(AudioProcessor.AudioFormat(format.sampleRate, format.channelCount, format.pcmEncoding))
      eq.flush(AudioProcessor.StreamMetadata.DEFAULT)
      val floatFormat = format.buildUpon().setPcmEncoding(C.ENCODING_PCM_FLOAT).build()
      @Suppress("DEPRECATION")
      super.configure(
        AudioSinkConfig.Builder(floatFormat)
          .setPreferredBufferSizeOverride(audioSinkConfig.preferredBufferSizeOverride)
          .setOutputChannelMapping(audioSinkConfig.outputChannelMapping)
          .setTimeline(audioSinkConfig.timeline)
          .setMediaPeriodId(audioSinkConfig.mediaPeriodId)
          .build(),
      )
    } else {
      super.configure(audioSinkConfig)
    }
    Timber.d(
      "出力の設定: 音源=%s 入力=%dbit%s %dHz %dch 処理=%s ビットパーフェクト=%s",
      source?.sampleMimeType,
      PcmCodec.bitDepth(format.pcmEncoding),
      if (format.pcmEncoding == C.ENCODING_PCM_FLOAT) "(float)" else "",
      format.sampleRate,
      format.channelCount,
      if (processing) "float" else "なし",
      negotiation.state,
    )
    report(source, format, processing, negotiation)
  }

  override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
    if (!processing) return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    // 前回渡し切れなかった処理済みのバッファがあれば、それを先に渡す
    val output = pending ?: run {
      eq.queueInput(buffer)
      pendingTimeUs = presentationTimeUs
      eq.output.also { pending = it }
    }
    if (!super.handleBuffer(output, pendingTimeUs, encodedAccessUnitCount)) return false
    pending = null
    return true
  }

  override fun flush() {
    pending = null
    if (processing) eq.flush(AudioProcessor.StreamMetadata.DEFAULT)
    super.flush()
  }

  override fun reset() {
    pending = null
    eq.reset()
    processing = false
    super.reset()
  }

  override fun release() {
    router.clearMixer()
    super.release()
  }

  private fun report(source: Format?, input: Format, floatProcessing: Boolean, negotiation: Negotiation) {
    // 音源のビット深度: FLAC・WAVはファイルの値、それ以外 (圧縮音源) は不明
    val sourceBits = source?.pcmEncoding?.takeIf { it != Format.NO_VALUE }?.let(PcmCodec::bitDepth) ?: 0
    monitor.update {
      it.copy(
        source = StreamFormat(
          bits = sourceBits,
          sampleRate = source?.sampleRate?.takeIf { r -> r > 0 } ?: input.sampleRate,
          channels = source?.channelCount?.takeIf { c -> c > 0 } ?: input.channelCount,
          float = source?.pcmEncoding == C.ENCODING_PCM_FLOAT,
        ),
        floatProcessing = floatProcessing,
        bitPerfect = negotiation.state,
      )
    }
    router.refreshDevice()
  }
}
