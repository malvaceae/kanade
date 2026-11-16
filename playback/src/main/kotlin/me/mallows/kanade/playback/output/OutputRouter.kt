package me.mallows.kanade.playback.output

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import android.os.Handler
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import me.mallows.kanade.audio.PcmCodec
import timber.log.Timber

/** ビットパーフェクトの交渉結果。[targetEncoding] は端末に渡す整数PCMの形式 */
data class Negotiation(val state: BitPerfectState, val targetEncoding: Int? = null) {
  val active: Boolean get() = state == BitPerfectState.ACTIVE
}

/**
 * 出力先の監視と、USB DACのビットパーフェクト出力の設定。
 * ビットパーフェクトはAndroid 14 (API 34) 以降の `setPreferredMixerAttributes` を使う。
 */
@Singleton
class OutputRouter @Inject constructor(
  @ApplicationContext context: Context,
  private val monitor: OutputMonitor,
) {
  private val audioManager = context.getSystemService(AudioManager::class.java)

  /** プレイヤーと同じ用途 (音楽) の属性。ミキサー属性はこの用途に対して設定する */
  private val attributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
    .build()

  /** 設定の「USBビットパーフェクト」 */
  @Volatile var bitPerfectEnabled: Boolean = false

  /** ミキサー属性を設定したデバイス (解除するため) */
  private var mixerDevice: AudioDeviceInfo? = null

  private val callback = object : AudioDeviceCallback() {
    override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refreshDevice()
    override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
      val gone = mixerDevice
      if (gone != null && removedDevices.any { it.id == gone.id }) mixerDevice = null
      refreshDevice()
    }
  }

  fun start(handler: Handler) {
    audioManager.registerAudioDeviceCallback(callback, handler)
    refreshDevice()
  }

  fun stop() {
    audioManager.unregisterAudioDeviceCallback(callback)
    clearMixer()
  }

  /** 音楽の出力先。Android 13以降は実際の経路、12は接続中の機器から推定する */
  fun currentDevice(): AudioDeviceInfo? {
    if (Build.VERSION.SDK_INT >= 33) {
      runCatching { audioManager.getAudioDevicesForAttributes(attributes) }
        .onFailure { Timber.w(it, "出力先を取得できない") }
        .getOrNull()?.firstOrNull()?.let { return it }
    }
    return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
      .filter { it.isSink }
      .maxByOrNull { priority(kindOf(it.type)) }
  }

  fun refreshDevice() {
    val device = currentDevice()
    monitor.update { info ->
      val described = device?.let(::describe)
      val state = when {
        Build.VERSION.SDK_INT < 34 -> BitPerfectState.UNAVAILABLE
        !bitPerfectEnabled -> BitPerfectState.OFF
        described == null || described.kind != DeviceKind.USB -> BitPerfectState.NOT_USB
        described.bitPerfectRates.isEmpty() -> BitPerfectState.UNSUPPORTED_DEVICE
        info.bitPerfect == BitPerfectState.ACTIVE || info.bitPerfect == BitPerfectState.UNSUPPORTED_FORMAT -> info.bitPerfect
        // 出力の形式は曲を開くときに決まるため、次の曲 (または再生し直し) から反映される
        else -> BitPerfectState.WAITING
      }
      info.copy(device = described, bitPerfect = state)
    }
  }

  /**
   * 次に流す曲の形式に合わせて、ビットパーフェクト出力を設定する。再生スレッドから呼ぶ。
   * @param sourceEncoding 音源のビット深度 (分かれば)。デコーダーがfloatを出すときに使う
   * @param inputEncoding 出力部品に届く形式
   */
  fun negotiate(sourceEncoding: Int?, inputEncoding: Int, sampleRate: Int, channels: Int): Negotiation {
    if (Build.VERSION.SDK_INT < 34) return Negotiation(BitPerfectState.UNAVAILABLE)
    if (!bitPerfectEnabled) {
      clearMixer()
      return Negotiation(BitPerfectState.OFF)
    }
    val device = currentDevice()
    if (device == null || kindOf(device.type) != DeviceKind.USB) {
      clearMixer()
      return Negotiation(BitPerfectState.NOT_USB)
    }
    return negotiateUsb(device, sourceEncoding, inputEncoding, sampleRate, channels)
  }

  @RequiresApi(34)
  private fun negotiateUsb(
    device: AudioDeviceInfo,
    sourceEncoding: Int?,
    inputEncoding: Int,
    sampleRate: Int,
    channels: Int,
  ): Negotiation {
    val mixers = bitPerfectMixers(device)
    if (mixers.isEmpty()) {
      clearMixer()
      return Negotiation(BitPerfectState.UNSUPPORTED_DEVICE)
    }
    for (encoding in candidates(sourceEncoding, inputEncoding)) {
      val mixer = mixers.firstOrNull {
        it.format.sampleRate == sampleRate &&
          it.format.encoding == encoding &&
          it.format.channelCount == channels
      } ?: continue
      val ok = runCatching { audioManager.setPreferredMixerAttributes(attributes, device, mixer) }
        .onFailure { Timber.w(it, "ミキサー属性を設定できない") }
        .getOrDefault(false)
      if (ok) {
        mixerDevice = device
        Timber.i("ビットパーフェクト: %dHz %dbit %dch", sampleRate, PcmCodec.bitDepth(encoding), channels)
        return Negotiation(BitPerfectState.ACTIVE, encoding)
      }
    }
    clearMixer()
    return Negotiation(BitPerfectState.UNSUPPORTED_FORMAT)
  }

  /**
   * 端末に渡す形式の候補。音源と同じビット深度を優先し、無ければ値を変えずに入る、より大きい整数にする。
   * (16bitの値は24bit・32bitに、24bitの値は32bitに、そのまま入る)
   */
  private fun candidates(sourceEncoding: Int?, inputEncoding: Int): List<Int> {
    val exact = when {
      inputEncoding != PcmCodec.PCM_FLOAT -> inputEncoding
      sourceEncoding != null && PcmCodec.isSupported(sourceEncoding) -> sourceEncoding
      else -> PcmCodec.PCM_24BIT
    }
    val wider = when (exact) {
      PcmCodec.PCM_16BIT -> listOf(PcmCodec.PCM_24BIT, PcmCodec.PCM_32BIT)
      PcmCodec.PCM_24BIT -> listOf(PcmCodec.PCM_32BIT)
      PcmCodec.PCM_FLOAT -> listOf(PcmCodec.PCM_32BIT, PcmCodec.PCM_24BIT)
      else -> emptyList()
    }
    return listOf(exact) + wider
  }

  fun clearMixer() {
    val device = mixerDevice ?: return
    mixerDevice = null
    if (Build.VERSION.SDK_INT >= 34) {
      runCatching { audioManager.clearPreferredMixerAttributes(attributes, device) }
    }
  }

  @RequiresApi(34)
  private fun bitPerfectMixers(device: AudioDeviceInfo): List<AudioMixerAttributes> =
    runCatching { audioManager.getSupportedMixerAttributes(device) }
      .getOrDefault(emptyList())
      .filter { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }

  @SuppressLint("NewApi")
  private fun describe(device: AudioDeviceInfo): OutputDevice {
    val kind = kindOf(device.type)
    var rates = device.sampleRates.toList()
    var encodings = device.encodings.toList()
    // Android 13以降は、端末が直接受け付ける形式 (ミキサーを通さない経路) も見る
    if (Build.VERSION.SDK_INT >= 33) {
      runCatching { audioManager.getDirectProfilesForAttributes(attributes) }.getOrNull()?.let { profiles ->
        rates = (rates + profiles.flatMap { it.sampleRates.toList() }).distinct()
        encodings = (encodings + profiles.map { it.format }).distinct()
      }
    }
    val bitPerfectRates = if (Build.VERSION.SDK_INT >= 34 && kind == DeviceKind.USB) {
      bitPerfectMixers(device).map { it.format.sampleRate }.distinct().sorted()
    } else {
      emptyList()
    }
    return OutputDevice(
      kind = kind,
      name = device.productName?.toString().orEmpty(),
      sampleRates = rates.filter { it > 0 }.distinct().sorted(),
      bitDepths = encodings.mapNotNull(::bitsOf).distinct().sorted(),
      bitPerfectRates = bitPerfectRates,
    )
  }

  private fun bitsOf(encoding: Int): Int? = when (encoding) {
    AudioFormat.ENCODING_PCM_16BIT -> 16
    AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
    AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 32
    else -> null
  }

  private fun priority(kind: DeviceKind): Int = when (kind) {
    DeviceKind.USB -> 5
    DeviceKind.WIRED -> 4
    DeviceKind.BLUETOOTH -> 3
    DeviceKind.HDMI -> 2
    DeviceKind.SPEAKER -> 1
    DeviceKind.OTHER -> 0
  }

  companion object {
    fun kindOf(type: Int): DeviceKind = when (type) {
      AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
      AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
      AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
      -> DeviceKind.SPEAKER
      AudioDeviceInfo.TYPE_WIRED_HEADSET,
      AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
      AudioDeviceInfo.TYPE_LINE_ANALOG,
      AudioDeviceInfo.TYPE_LINE_DIGITAL,
      AudioDeviceInfo.TYPE_AUX_LINE,
      -> DeviceKind.WIRED
      AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
      AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
      AudioDeviceInfo.TYPE_BLE_HEADSET,
      AudioDeviceInfo.TYPE_BLE_SPEAKER,
      AudioDeviceInfo.TYPE_BLE_BROADCAST,
      -> DeviceKind.BLUETOOTH
      AudioDeviceInfo.TYPE_USB_DEVICE,
      AudioDeviceInfo.TYPE_USB_ACCESSORY,
      AudioDeviceInfo.TYPE_USB_HEADSET,
      -> DeviceKind.USB
      AudioDeviceInfo.TYPE_HDMI,
      AudioDeviceInfo.TYPE_HDMI_ARC,
      AudioDeviceInfo.TYPE_HDMI_EARC,
      -> DeviceKind.HDMI
      else -> DeviceKind.OTHER
    }
  }
}
