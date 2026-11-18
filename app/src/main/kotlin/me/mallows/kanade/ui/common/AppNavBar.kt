package me.mallows.kanade.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.mallows.kanade.R
import me.mallows.kanade.ui.icons.KanadeIcons

/** 画面下の固定のメニューの行き先 */
enum class NavItem(val icon: ImageVector, val label: Int) {
  LIBRARY(KanadeIcons.Grid, R.string.library_title),
  EQUALIZER(KanadeIcons.Equalizer, R.string.eq_title),
  SEARCH(KanadeIcons.Search, R.string.action_search),
  QUEUE(KanadeIcons.Queue, R.string.queue_title),
}

/** 画面下の固定のメニューの高さ (ナビゲーションバーの分は含まない) */
val AppNavBarHeight = 56.dp

/**
 * 画面下の固定のメニュー。どの画面からでもライブラリ・EQ・検索・再生キューへ移れる。
 * Powerampと同じく、アイコンだけを並べる (名前は読み上げ用)
 */
@Composable
fun AppNavBar(selected: NavItem?, onSelect: (NavItem) -> Unit, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surfaceContainer)
      .navigationBarsPadding()
      .height(AppNavBarHeight)
      .padding(horizontal = 8.dp)
      .selectableGroup(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    NavItem.entries.forEach { item ->
      val isSelected = item == selected
      val label = stringResource(item.label)
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(item) })
          .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          item.icon,
          contentDescription = null,
          tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(26.dp),
        )
      }
    }
  }
}
