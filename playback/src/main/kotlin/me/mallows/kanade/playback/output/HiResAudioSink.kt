package me.mallows.kanade.playback.output

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.AudioSink.AudioSinkConfig
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder
import me.mallows.kanade.audio.DspChain
import me.mallows.kanade.audio.EqAudioProcessor
import me.mallows.kanade.audio.PcmCodec
import me.mallows.kanade.audio.ReplayGain
import me.mallows.kanade.audio.ReplayGainMode
import kotlin.math.log10
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
  private val dsp: DspChain,
  private val provider: KanadeOutputProvider,
  private val router: OutputRouter,
  private val monitor: OutputMonitor,
) : ForwardingAudioSink(sink) {

  /** デコーダーに入る前の形式 (FLACやWAVならビット深度が分かる) */
  @Volatile private var sourceFormat: Format? = null

  /** ReplayGainの使い方 (設定) */
  @Volatile var replayGainMode: ReplayGainMode = ReplayGainMode.OFF
  @Volatile private var replayGain = ReplayGain()

  /** 次の曲の形式を受け取る。レンダラーが再生スレッドで、その曲のデータより先に呼ぶ */
  fun onSourceFormat(format: Format?) {
    sourceFormat = format
    replayGain = replayGainOf(format)
    applyReplayGain()
  }

  /** 今の曲と設定に合わせて、ReplayGainの倍率を音声処理に渡す */
  fun applyReplayGain() {
    val linear = replayGain.linearGain(replayGainMode)
    dsp.setInputGain(linear)
    monitor.update {
      it.copy(replayGainDb = if (replayGainMode == ReplayGainMode.OFF || replayGain.isEmpty) null else (20 * log10(linear)).toFloat())
    }
  }

  private val eq = EqAudioProcessor(dsp)
  private var processing = false
  private var pending: ByteBuffer? = null
  private var pendingTimeUs = 0L

  /** プレイヤーが求めている再生速度 */
  @Volatile private var requestedSpeed = 1f
  /**
   * 16bitにしてMedia3の速度変更 (Sonic) を通しているか。
   * Media3の速度変更は16bitにしか効かず、floatで出すと速度が変わらないため、等速以外のときだけ16bitにする。
   */
  private var toInt16 = false
  private var work = FloatArray(0)
  private var int16: ByteBuffer = ByteBuffer.allocateDirect(0)

  /** 今の速度に合わない形式で開いているか (等速と等速以外を切り替えたら、開き直す必要がある) */
  fun needsReopen(): Boolean = processing && toInt16 != (requestedSpeed != 1f)

  override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
    requestedSpeed = playbackParameters.speed
    super.setPlaybackParameters(playbackParameters)
  }

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
    val variableSpeed = requestedSpeed != 1f
    // 速度を変えているときはビットパーフェクトにしない (時間伸縮で値が変わるため)
    val negotiation = if (variableSpeed) {
      router.clearMixer()
      Negotiation(BitPerfectState.OFF)
    } else {
      router.negotiate(sourceEncoding, format.pcmEncoding, format.sampleRate, format.channelCount)
    }
    provider.targetEncoding = negotiation.targetEncoding
    processing = !negotiation.active
    toInt16 = processing && variableSpeed
    if (processing) {
      eq.configure(AudioProcessor.AudioFormat(format.sampleRate, format.channelCount, format.pcmEncoding))
      eq.flush(AudioProcessor.StreamMetadata.DEFAULT)
      val floatFormat = format.buildUpon().setPcmEncoding(if (toInt16) C.ENCODING_PCM_16BIT else C.ENCODING_PCM_FLOAT).build()
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
      if (!processing) "なし" else if (toInt16) "float → 16bit (速度変更)" else "float",
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
      val processed = eq.output
      (if (toInt16) toInt16(processed) else processed).also { pending = it }
    }
    if (!super.handleBuffer(output, pendingTimeUs, encodedAccessUnitCount)) return false
    pending = null
    return true
  }

  /** EQ後のfloatを16bitにする (速度変更のときだけ) */
  private fun toInt16(floats: ByteBuffer): ByteBuffer {
    val samples = floats.remaining() / 4
    if (work.size < samples) work = FloatArray(samples)
    PcmCodec.decode(floats, PcmCodec.PCM_FLOAT, work)
    if (int16.capacity() < samples * 2) int16 = ByteBuffer.allocateDirect(samples * 2).order(ByteOrder.nativeOrder())
    int16.clear()
    PcmCodec.encode(work, samples, PcmCodec.PCM_16BIT, int16)
    int16.flip()
    return int16
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
