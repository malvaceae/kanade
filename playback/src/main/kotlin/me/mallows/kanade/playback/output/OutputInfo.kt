package me.mallows.kanade.playback.output

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 出力先の種類 */
enum class DeviceKind { SPEAKER, WIRED, BLUETOOTH, USB, HDMI, OTHER }

/** 出力先と、その出力先が受け付ける形式 */
data class OutputDevice(
  val kind: DeviceKind,
  val name: String,
  /** 対応サンプルレート。空なら任意 (端末が変換する) */
  val sampleRates: List<Int>,
  /** 対応ビット深度 (16・24・32) */
  val bitDepths: List<Int>,
  /** ビットパーフェクトで出せるサンプルレート (Android 14以降のUSB DACのみ) */
  val bitPerfectRates: List<Int>,
)

/** PCMの形式。[bits] が0なら不明 (圧縮音源など) */
data class StreamFormat(val bits: Int, val sampleRate: Int, val channels: Int, val float: Boolean = false)

/** ビットパーフェクト出力の状態 */
enum class BitPerfectState {
  /** Android 13以前 (APIがない) */
  UNAVAILABLE,
  /** 設定でオフ */
  OFF,
  /** 出力先がUSBではない */
  NOT_USB,
  /** USB DACまたは端末が対応していない */
  UNSUPPORTED_DEVICE,
  /** 対応するUSB DACがつながったが、まだ切り替えていない */
  WAITING,
  /** この曲のサンプルレート・ビット深度に対応していない */
  UNSUPPORTED_FORMAT,
  /** ビットパーフェクトで出力中 */
  ACTIVE,
}

/** 再生中の音が、どの形式で処理され、どこへ出ているか */
data class OutputInfo(
  /** 音源の形式 (ファイルの中身) */
  val source: StreamFormat? = null,
  /** EQなどの処理をfloatで行っているか */
  val floatProcessing: Boolean = false,
  /** 端末の音声システムに渡している形式 */
  val output: StreamFormat? = null,
  val device: OutputDevice? = null,
  val bitPerfect: BitPerfectState = BitPerfectState.UNAVAILABLE,
)

/** 再生サービスで集めた出力情報を画面と共有する */
@Singleton
class OutputMonitor @Inject constructor() {
  private val state = MutableStateFlow(OutputInfo())
  val info: StateFlow<OutputInfo> = state.asStateFlow()

  fun update(transform: (OutputInfo) -> OutputInfo) = state.update(transform)
}
