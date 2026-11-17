package me.mallows.kanade.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.mallows.kanade.core.data.library.LibraryRepository
import me.mallows.kanade.core.domain.folder.FolderNode
import me.mallows.kanade.core.domain.folder.ScanFolder
import me.mallows.kanade.core.domain.folder.normalize

/** スキャン対象のフォルダーの選択 */
@HiltViewModel
class ScanFoldersViewModel @Inject constructor(
  private val library: LibraryRepository,
) : ViewModel() {

  /** ストレージ (ボリューム名) ごとのフォルダーの木。読み込み中はnull */
  private val _tree = MutableStateFlow<Map<String, List<FolderNode>>?>(null)
  val tree: StateFlow<Map<String, List<FolderNode>>?> = _tree.asStateFlow()

  private val _selected = MutableStateFlow<List<ScanFolder>>(emptyList())
  val selected: StateFlow<List<ScanFolder>> = _selected.asStateFlow()

  /** 開いている (子を見せている) フォルダー */
  private val _expanded = MutableStateFlow<Set<ScanFolder>>(emptySet())
  val expanded: StateFlow<Set<ScanFolder>> = _expanded.asStateFlow()

  val volumeLabels: Map<String, String> by lazy { library.volumeLabels() }

  val scanning: StateFlow<Boolean> = library.scanning

  /** 画面を開いたときの選択。閉じるときに比べて、変わっていれば保存して読み直す */
  private var initial: List<ScanFolder> = emptyList()

  init {
    viewModelScope.launch {
      initial = normalize(library.scanFolders())
      _selected.value = initial
      // 選んでいるフォルダーが見えるよう、その親を開いておく
      _expanded.value = initial.flatMap { ancestors(it) }.toSet()
      _tree.value = library.folderTree(initial)
    }
  }

  /** [folder]が選んだフォルダーの配下 (自分自身を含まない) にあるか。あれば個別には外せない */
  fun coveredByParent(folder: ScanFolder): Boolean =
    _selected.value.any { it != folder && it.contains(folder.volume, folder.relativePath) && !it.relativePath.equals(folder.relativePath, true) }

  fun isChecked(folder: ScanFolder): Boolean = _selected.value.any { it.contains(folder.volume, folder.relativePath) }

  fun toggle(folder: ScanFolder) {
    if (coveredByParent(folder)) return
    val current = _selected.value
    _selected.value = if (current.any { it.volume.equals(folder.volume, true) && it.relativePath.equals(folder.relativePath, true) }) {
      current.filterNot { it.volume.equals(folder.volume, true) && it.relativePath.equals(folder.relativePath, true) }
    } else {
      // 子を選んでいたら、親に含まれるので外す
      normalize(current + folder)
    }
  }

  fun toggleExpanded(folder: ScanFolder) {
    _expanded.value = _expanded.value.let { if (folder in it) it - folder else it + folder }
  }

  val changed: Boolean get() = normalize(_selected.value).toSet() != initial.toSet()

  /** 変わっていれば保存して読み直す */
  fun save() {
    if (!changed) return
    val folders = _selected.value
    initial = normalize(folders)
    // 画面を閉じた後も続けるため、アプリ全体のスコープで行う (LibraryRepositoryの処理は同時に2つ走らない)
    library.launchInBackground { library.setScanFolders(folders) }
  }

  /** 「再スキャン」。選択が変わっていれば保存してから読み直す */
  fun rescan() {
    if (changed) save() else library.launchInBackground { library.rescan() }
  }

  private fun ancestors(folder: ScanFolder): List<ScanFolder> {
    val parts = folder.relativePath.trimEnd('/').split('/')
    return (1 until parts.size).map { n -> ScanFolder(folder.volume, parts.take(n).joinToString("/") + "/") }
  }
}
