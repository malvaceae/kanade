package me.mallows.kanade.ui.player

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.mallows.kanade.R
import me.mallows.kanade.playback.EqRepository
import me.mallows.kanade.playback.output.BitPerfectState
import me.mallows.kanade.playback.output.DeviceKind
import me.mallows.kanade.playback.output.OutputDevice
import me.mallows.kanade.playback.output.OutputInfo
import me.mallows.kanade.playback.output.OutputMonitor
import me.mallows.kanade.playback.output.OutputSettings
import me.mallows.kanade.playback.output.StreamFormat

@HiltViewModel
class OutputViewModel @Inject constructor(
  monitor: OutputMonitor,
  private val settings: OutputSettings,
  eq: EqRepository,
) : ViewModel() {
  val info: StateFlow<OutputInfo> = monitor.info
  val bitPerfect: StateFlow<Boolean> =
    settings.bitPerfect.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
  val eqEnabled: StateFlow<Boolean> =
    eq.settings.map { it.enabled }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

  fun setBitPerfect(enabled: Boolean) {
    viewModelScope.launch { settings.setBitPerfect(enabled) }
  }
}

/** 曲の情報に添える、出力の情報 (どこへ、どの形式で出ているか) */
@Composable
fun OutputSection(row: @Composable (label: String, value: String) -> Unit, viewModel: OutputViewModel = hiltViewModel()) {
  val info by viewModel.info.collectAsStateWithLifecycle()
  val bitPerfect by viewModel.bitPerfect.collectAsStateWithLifecycle()
  val eqEnabled by viewModel.eqEnabled.collectAsStateWithLifecycle()
  val scheme = MaterialTheme.colorScheme

  Text(
    stringResource(R.string.output_section),
    style = MaterialTheme.typography.labelLarge,
    color = scheme.primary,
    modifier = Modifier.padding(top = 8.dp),
  )
  info.device?.let { row(stringResource(R.string.output_device), deviceLabel(it)) }
  val processing = when {
    !info.floatProcessing -> stringResource(R.string.output_processing_none)
    eqEnabled -> stringResource(R.string.output_processing_eq)
    else -> stringResource(R.string.output_processing_float)
  }
  row(stringResource(R.string.output_processing), processing)
  info.output?.let { row(stringResource(R.string.output_format), formatText(it)) }
  info.replayGainDb?.let { row(stringResource(R.string.replay_gain), "%+.1fdB".format(it)) }
  info.device?.let { device ->
    val parts = buildList {
      device.sampleRates.maxOrNull()?.let { add(stringResource(R.string.output_max_rate, rateText(it))) }
      if (device.bitDepths.isNotEmpty()) add(device.bitDepths.joinToString("/") + "bit")
    }
    if (parts.isNotEmpty()) row(stringResource(R.string.output_supported), parts.joinToString(" · "))
  }

  // ビットパーフェクトはAndroid 14以降のみ (APIがない12・13では項目ごと出さない)
  if (Build.VERSION.SDK_INT >= 34) {
    // オンのときは説明の代わりに今の状態 (出力中・使えない理由) を出す
    val description = if (!bitPerfect) {
      stringResource(R.string.output_bit_perfect_switch_desc)
    } else {
      stringResource(
        when (info.bitPerfect) {
          BitPerfectState.ACTIVE -> R.string.bit_perfect_active
          BitPerfectState.NOT_USB -> R.string.bit_perfect_not_usb
          BitPerfectState.UNSUPPORTED_DEVICE -> R.string.bit_perfect_unsupported_device
          BitPerfectState.UNSUPPORTED_FORMAT -> R.string.bit_perfect_unsupported_format
          // オフ・未対応はスイッチを入れた直後だけ (設定の反映待ち)
          BitPerfectState.WAITING, BitPerfectState.OFF, BitPerfectState.UNAVAILABLE -> R.string.bit_perfect_waiting
        },
      )
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(stringResource(R.string.output_bit_perfect_switch), style = MaterialTheme.typography.bodyMedium)
        Text(
          description,
          style = MaterialTheme.typography.bodySmall,
          color = scheme.onSurfaceVariant,
        )
      }
      Switch(checked = bitPerfect, onCheckedChange = viewModel::setBitPerfect, modifier = Modifier.padding(start = 12.dp))
    }
  }
}

/** 出力先の表示名。「Bluetooth · 製品名」のように種類と製品名を並べる (内蔵スピーカーの製品名は端末名なので出さない) */
@Composable
fun deviceLabel(device: OutputDevice): String {
  val kind = stringResource(
    when (device.kind) {
      DeviceKind.SPEAKER -> R.string.device_speaker
      DeviceKind.WIRED -> R.string.device_wired
      DeviceKind.BLUETOOTH -> R.string.device_bluetooth
      DeviceKind.USB -> R.string.device_usb
      DeviceKind.HDMI -> R.string.device_hdmi
      DeviceKind.OTHER -> R.string.device_other
    },
  )
  val showName = device.name.isNotBlank() && device.kind != DeviceKind.SPEAKER
  return if (showName) "$kind · ${device.name}" else kind
}

@Composable
private fun formatText(f: StreamFormat): String {
  val depth = if (f.float) stringResource(R.string.output_float) else "${f.bits}bit"
  return "$depth / ${rateText(f.sampleRate)}"
}

private fun rateText(hz: Int): String =
  if (hz % 1000 == 0) "${hz / 1000}kHz" else "%.1fkHz".format(hz / 1000.0)
