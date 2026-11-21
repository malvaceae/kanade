package me.mallows.kanade.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.mallows.kanade.R
import me.mallows.kanade.ui.common.rememberFullSheetState
import me.mallows.kanade.ui.icons.KanadeIcons
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** タブ1つの設定 */
data class TabSetting(val tab: LibraryTab, val visible: Boolean) {
  companion object {
    /** 保存した文字列から戻す。足りないタブは末尾に表示で足し、どれも表示されなければ曲のタブを出す */
    fun decode(text: String?): List<TabSetting> {
      val saved = text.orEmpty().split(',').mapNotNull { part ->
        val hidden = part.startsWith("!")
        LibraryTab.entries.firstOrNull { it.name == part.removePrefix("!") }?.let { TabSetting(it, !hidden) }
      }.distinctBy { it.tab }
      val all = saved + LibraryTab.entries.filter { t -> saved.none { it.tab == t } }.map { TabSetting(it, true) }
      return if (all.none { it.visible }) all.map { if (it.tab == LibraryTab.TRACKS) it.copy(visible = true) else it } else all
    }

    fun encode(tabs: List<TabSetting>): String =
      tabs.joinToString(",") { (if (it.visible) "" else "!") + it.tab.name }
  }
}

/** タブの並び替えと表示・非表示。並びはドラッグで入れ替える */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsSheet(tabs: List<TabSetting>, onChange: (List<TabSetting>) -> Unit, onDismiss: () -> Unit) {
  var list by remember { mutableStateOf(tabs) }
  val haptic = LocalHapticFeedback.current
  val listState = rememberLazyListState()
  val reorderState = rememberReorderableLazyListState(listState) { from, to ->
    val fi = list.indexOfFirst { it.tab.name == from.key }
    val ti = list.indexOfFirst { it.tab.name == to.key }
    if (fi < 0 || ti < 0) return@rememberReorderableLazyListState
    list = list.toMutableList().apply { add(ti, removeAt(fi)) }
    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
  }
  ModalBottomSheet(
    onDismissRequest = { onChange(list); onDismiss() },
    sheetState = rememberFullSheetState(),
  ) {
    Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
      Text(
        stringResource(R.string.tabs_title),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
      )
      Text(
        stringResource(R.string.tabs_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp),
      )
      LazyColumn(state = listState) {
        items(list, key = { it.tab.name }) { item ->
          ReorderableItem(reorderState, key = item.tab.name) { dragging ->
            val visibleCount = list.count { it.visible }
            // 最後の1つは隠せない
            val canToggle = !item.visible || visibleCount > 1
            Row(
              Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                .clickable(enabled = canToggle) {
                  list = list.map { if (it.tab == item.tab) it.copy(visible = !it.visible) else it }
                  onChange(list)
                }
                .padding(start = 12.dp, end = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Checkbox(checked = item.visible, onCheckedChange = null, enabled = canToggle)
              Text(
                stringResource(item.tab.label),
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
              )
              IconButton(
                onClick = {},
                modifier = Modifier.draggableHandle(
                  onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                  onDragStopped = { onChange(list) },
                ),
              ) {
                Icon(KanadeIcons.Grip, contentDescription = stringResource(R.string.action_drag))
              }
            }
          }
        }
      }
    }
  }
}
