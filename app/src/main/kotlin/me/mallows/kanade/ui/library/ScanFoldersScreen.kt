package me.mallows.kanade.ui.library

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.mallows.kanade.R
import me.mallows.kanade.core.domain.folder.ScanFolder
import me.mallows.kanade.ui.common.LocalShowMessage
import me.mallows.kanade.ui.icons.KanadeIcons

/**
 * スキャン対象のフォルダー。選んだフォルダーの一覧に、Androidの標準のフォルダー選択の画面でフォルダーを足す。
 * 「保存」で保存して読み直す。戻る・「キャンセル」では保存しない
 */
@Composable
fun ScanFoldersScreen(onBack: () -> Unit, viewModel: ScanFoldersViewModel = hiltViewModel()) {
  val selected by viewModel.selected.collectAsStateWithLifecycle()
  val counts by viewModel.counts.collectAsStateWithLifecycle()
  val showMessage = LocalShowMessage.current
  val res = LocalResources.current

  // 標準のフォルダー選択。選んだフォルダーの場所だけを受け取り、開く許可は保存しない (曲はMediaStoreから読む)
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
    if (uri == null) return@rememberLauncherForActivityResult
    val folder = ScanFolder.fromTree(uri.authority, DocumentsContract.getTreeDocumentId(uri))
    when {
      folder == null -> showMessage(res.getString(R.string.message_folder_not_supported))
      viewModel.add(folder) == ScanFoldersViewModel.AddResult.ALREADY_INCLUDED ->
        showMessage(res.getString(R.string.message_folder_already_included))
    }
  }
  // 最初は内部ストレージの「Music」を開く
  val musicUri = DocumentsContract.buildDocumentUri(ScanFolder.EXTERNAL_STORAGE_AUTHORITY, "primary:Music")

  Column(Modifier.fillMaxSize().statusBarsPadding()) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) { Icon(KanadeIcons.Back, contentDescription = stringResource(R.string.action_back)) }
      Text(
        stringResource(R.string.scan_folders_title),
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f).padding(start = 4.dp),
      )
    }
    Text(
      stringResource(R.string.scan_folders_hint),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
    LazyColumn(Modifier.weight(1f)) {
      val list = selected
      if (list != null && list.isEmpty()) {
        item {
          Text(
            stringResource(R.string.scan_folders_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
          )
        }
      }
      items(list.orEmpty(), key = { it.encode() }) { folder ->
        FolderRow(
          name = folderName(folder),
          storage = viewModel.volumeLabels[folder.volume] ?: stringResource(R.string.storage_unknown),
          tracks = counts[folder],
          onRemove = { viewModel.remove(folder) },
        )
      }
      item {
        OutlinedButton(
          onClick = { picker.launch(musicUri) },
          modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
          Icon(KanadeIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
          Text(stringResource(R.string.scan_folders_add), modifier = Modifier.padding(start = 8.dp))
        }
      }
    }
    HorizontalDivider()
    Row(
      Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
      TextButton(onClick = onBack) { Text(stringResource(R.string.action_cancel)) }
      Button(onClick = { viewModel.save(); onBack() }, enabled = viewModel.canSave(selected)) {
        Text(stringResource(R.string.action_save))
      }
    }
  }
}

/** 「Music › Artist」の形。ストレージ全体なら「ストレージ全体」 */
@Composable
private fun folderName(folder: ScanFolder): String =
  folder.relativePath.trimEnd('/').replace("/", " › ").ifEmpty { stringResource(R.string.scan_folders_whole_storage) }

@Composable
private fun FolderRow(name: String, storage: String, tracks: Int?, onRemove: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().height(64.dp).padding(start = 20.dp, end = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(KanadeIcons.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    Column(Modifier.weight(1f).padding(start = 16.dp)) {
      Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
      val sub = listOfNotNull(storage, tracks?.let { pluralStringResource(R.plurals.track_count, it, it) }).joinToString(" · ")
      Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
    IconButton(onClick = onRemove) {
      Icon(KanadeIcons.Close, contentDescription = stringResource(R.string.scan_folders_remove))
    }
  }
}
