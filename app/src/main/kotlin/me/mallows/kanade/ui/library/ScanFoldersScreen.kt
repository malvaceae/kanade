package me.mallows.kanade.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.mallows.kanade.R
import me.mallows.kanade.core.domain.folder.FolderNode
import me.mallows.kanade.core.domain.folder.ScanFolder
import me.mallows.kanade.ui.icons.KanadeIcons

/**
 * スキャン対象のフォルダーの選択。ストレージごとにフォルダーの木を出し、チェックしたフォルダーの配下を読み込む。
 * 閉じるときに、変わっていれば保存して読み直す
 */
@Composable
fun ScanFoldersScreen(onBack: () -> Unit, viewModel: ScanFoldersViewModel = hiltViewModel()) {
  val tree by viewModel.tree.collectAsStateWithLifecycle()
  // 選択と開閉が変わったら描き直す (行の状態はViewModelの関数で調べる)
  val selected by viewModel.selected.collectAsStateWithLifecycle()
  val expanded by viewModel.expanded.collectAsStateWithLifecycle()
  val scanning by viewModel.scanning.collectAsStateWithLifecycle()
  var confirmEmpty by remember { mutableStateOf(false) }

  fun close() {
    // 画面の状態は1コマ遅れることがあるので、ViewModelの今の値で判断する
    if (viewModel.changed && viewModel.selected.value.isEmpty()) {
      confirmEmpty = true
    } else {
      viewModel.save()
      onBack()
    }
  }
  BackHandler { close() }

  Column(Modifier.fillMaxSize().statusBarsPadding()) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = ::close) { Icon(KanadeIcons.Back, contentDescription = stringResource(R.string.action_back)) }
      Text(
        stringResource(R.string.scan_folders_title),
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f).padding(start = 4.dp),
      )
    }
    // 読み込み中・スキャン中は細い進み具合を出す (高さは常に取って、表示がずれないようにする)
    Box(Modifier.fillMaxWidth().height(2.dp)) {
      if (tree == null || scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    Text(
      stringResource(R.string.scan_folders_hint),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
    val current = tree
    LazyColumn(Modifier.weight(1f)) {
      if (current != null && current.isEmpty()) {
        item {
          Text(
            stringResource(R.string.scan_folders_none_found),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp),
          )
        }
      }
      current?.forEach { (volume, nodes) ->
        item(key = "volume:$volume") {
          Text(
            viewModel.volumeLabels[volume] ?: stringResource(R.string.storage_unknown),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
          )
        }
        folderRows(nodes, depth = 0, viewModel = viewModel, expanded = expanded, selected = selected)
      }
    }
    HorizontalDivider()
    Row(
      Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.End,
    ) {
      Button(onClick = viewModel::rescan, enabled = !scanning) { Text(stringResource(R.string.action_rescan)) }
    }
  }

  if (confirmEmpty) {
    AlertDialog(
      onDismissRequest = { confirmEmpty = false },
      title = { Text(stringResource(R.string.scan_folders_empty_confirm_title)) },
      text = { Text(stringResource(R.string.scan_folders_empty_confirm)) },
      confirmButton = {
        TextButton(onClick = { confirmEmpty = false; viewModel.save(); onBack() }) { Text(stringResource(R.string.action_ok)) }
      },
      dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }
}

/** フォルダーの行を、開いているものは子を親の直後に並べる */
@Suppress("UNUSED_PARAMETER") // selectedは、選択が変わったときに描き直させるために受け取る
private fun LazyListScope.folderRows(
  nodes: List<FolderNode>,
  depth: Int,
  viewModel: ScanFoldersViewModel,
  expanded: Set<ScanFolder>,
  selected: List<ScanFolder>,
) {
  val visible = mutableListOf<Pair<FolderNode, Int>>()
  fun add(list: List<FolderNode>, d: Int) {
    for (node in list) {
      visible += node to d
      if (ScanFolder(node.volume, node.relativePath) in expanded) add(node.children, d + 1)
    }
  }
  add(nodes, depth)
  items(visible, key = { (node, _) -> "${node.volume}:${node.relativePath}" }) { (node, d) ->
    val folder = ScanFolder(node.volume, node.relativePath)
    FolderRow(
      node = node,
      depth = d,
      checked = viewModel.isChecked(folder),
      enabled = !viewModel.coveredByParent(folder),
      open = folder in expanded,
      onToggle = { viewModel.toggle(folder) },
      onExpand = { viewModel.toggleExpanded(folder) },
    )
  }
}

@Composable
private fun FolderRow(
  node: FolderNode,
  depth: Int,
  checked: Boolean,
  enabled: Boolean,
  open: Boolean,
  onToggle: () -> Unit,
  onExpand: () -> Unit,
) {
  Row(
    Modifier
      .fillMaxWidth()
      .height(56.dp)
      .clickable(enabled = enabled, onClick = onToggle)
      .padding(start = 8.dp + 24.dp * depth, end = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(checked = checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(horizontal = 4.dp))
    Column(Modifier.weight(1f).padding(start = 8.dp)) {
      Text(node.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(
        pluralStringResource(R.plurals.track_count, node.totalTracks, node.totalTracks),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (node.children.isNotEmpty()) {
      IconButton(onClick = onExpand) {
        Icon(
          KanadeIcons.ChevronDown,
          contentDescription = null,
          modifier = Modifier.size(22.dp).rotate(if (open) 180f else 0f),
        )
      }
    } else {
      Spacer(Modifier.width(48.dp))
    }
  }
}
