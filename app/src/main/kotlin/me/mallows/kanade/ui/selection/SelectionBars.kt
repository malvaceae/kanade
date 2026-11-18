package me.mallows.kanade.ui.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.mallows.kanade.R
import me.mallows.kanade.ui.icons.KanadeIcons

/** 選択モードの上部バー: 「N曲を選択中」「すべて選択」「×」 */
@Composable
fun SelectionTopBar(
  count: Int,
  allSelected: Boolean,
  onSelectAll: () -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
  text: String = stringResource(R.string.selection_count, count),
) {
  Row(
    modifier = modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onClose) { Icon(KanadeIcons.Close, contentDescription = stringResource(R.string.action_clear_selection)) }
    Text(
      text,
      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
      modifier = Modifier.weight(1f),
    )
    TextButton(onClick = onSelectAll) {
      Text(stringResource(if (allSelected) R.string.action_clear_selection else R.string.action_select_all))
    }
  }
}

/** 操作バーのボタン */
data class BarAction(
  val icon: ImageVector,
  val label: String,
  val enabled: Boolean = true,
  val onClick: () -> Unit,
)

/** 選択モードの下部バー。アイコンと短いラベルを横に並べる */
@Composable
fun SelectionBottomBar(actions: List<BarAction>, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surfaceContainer)
      .navigationBarsPadding()
      .height(72.dp)
      .padding(horizontal = 4.dp),
    horizontalArrangement = Arrangement.SpaceEvenly,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    actions.forEach { action ->
      Column(
        modifier = Modifier.weight(1f).alpha(if (action.enabled) 1f else 0.38f),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        IconButton(onClick = action.onClick, enabled = action.enabled, modifier = Modifier.size(40.dp)) {
          Icon(action.icon, contentDescription = action.label, modifier = Modifier.size(22.dp))
        }
        Text(action.label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), maxLines = 1)
      }
    }
  }
}
