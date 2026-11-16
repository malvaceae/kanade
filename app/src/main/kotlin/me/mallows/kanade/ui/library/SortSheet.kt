package me.mallows.kanade.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.mallows.kanade.R
import me.mallows.kanade.core.data.settings.SortSetting

/** ソート条件を選ぶシート */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <S : SortOption<*>> SortSheet(
  options: Array<S>,
  current: SortSetting?,
  default: S,
  onSelect: (SortSetting) -> Unit,
  onDismiss: () -> Unit,
) {
  val selected = options.firstOrNull { it.key == current?.key } ?: default
  val descending = current?.descending == true
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
    Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
      Text(
        stringResource(R.string.sort_title_sheet),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
      )
      options.forEach { option ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable { onSelect(SortSetting(option.key, descending)) }
            .padding(horizontal = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          RadioButton(selected = option == selected, onClick = { onSelect(SortSetting(option.key, descending)) })
          Text(stringResource(option.label), style = MaterialTheme.typography.bodyLarge)
        }
      }
      Row(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChip(
          selected = !descending,
          onClick = { onSelect(SortSetting(selected.key, false)) },
          label = { Text(stringResource(R.string.sort_ascending)) },
        )
        FilterChip(
          selected = descending,
          onClick = { onSelect(SortSetting(selected.key, true)) },
          label = { Text(stringResource(R.string.sort_descending)) },
        )
      }
    }
  }
}
