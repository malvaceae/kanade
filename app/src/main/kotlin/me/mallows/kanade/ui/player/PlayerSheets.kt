package me.mallows.kanade.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.mallows.kanade.R
import me.mallows.kanade.playback.PlayerState
import me.mallows.kanade.ui.common.audioUri

/** スリープタイマーの時間を選ぶシート */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(active: Boolean, onSet: (minutes: Int, endOfTrack: Boolean) -> Unit, onDismiss: () -> Unit) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
      Text(
        stringResource(R.string.sleep_title),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
      )
      listOf(15, 30, 45, 60).forEach { m ->
        SheetText(stringResource(R.string.sleep_minutes, m)) { onSet(m, false) }
      }
      SheetText(stringResource(R.string.sleep_end_of_track)) { onSet(0, true) }
      if (active) SheetText(stringResource(R.string.sleep_cancel)) { onSet(0, false) }
    }
  }
}

@Composable
private fun SheetText(text: String, onClick: () -> Unit) {
  Text(
    text,
    style = MaterialTheme.typography.bodyLarge,
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
  )
}

/** 曲の情報 (形式・サンプルレート・ビット深度など) */
@Composable
fun TrackInfoDialog(state: PlayerState, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val id = state.mediaStoreId ?: return
  val format by produceState<TrackFormat?>(null, id) {
    value = readTrackFormat(context, audioUri(id), state.durationMs)
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(state.title.orEmpty()) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoRow(stringResource(R.string.info_artist), state.artist ?: stringResource(R.string.unknown_artist))
        InfoRow(stringResource(R.string.info_album), state.album ?: stringResource(R.string.unknown_album))
        val f = format
        if (f == null) {
          Text(stringResource(R.string.library_scanning), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
          InfoRow(stringResource(R.string.info_format), shortFormatName(f.mime) ?: "-")
          InfoRow(stringResource(R.string.info_sample_rate), f.sampleRate?.let { "%.1fkHz".format(it / 1000.0) } ?: "-")
          InfoRow(stringResource(R.string.info_bit_depth), f.bitDepth?.let { "${it}bit" } ?: "-")
          InfoRow(stringResource(R.string.info_channels), f.channels?.toString() ?: "-")
          InfoRow(stringResource(R.string.info_bitrate), f.bitrateKbps?.let { "${it}kbps" } ?: "-")
          InfoRow(stringResource(R.string.info_size), f.sizeBytes?.let { "%.1fMB".format(it / 1_048_576.0) } ?: "-")
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
  )
}

@Composable
private fun InfoRow(label: String, value: String) {
  Row(Modifier.fillMaxWidth().height(24.dp)) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
    Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
  }
}
