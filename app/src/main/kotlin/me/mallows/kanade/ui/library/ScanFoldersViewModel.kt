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
import me.mallows.kanade.core.domain.folder.ScanFolder
import me.mallows.kanade.core.domain.folder.normalize

/** スキャン対象のフォルダの編集。「保存」を押すまでは保存しない */
@HiltViewModel
class ScanFoldersViewModel @Inject constructor(
  private val library: LibraryRepository,
) : ViewModel() {

  private val _selected = MutableStateFlow<List<ScanFolder>?>(null)
  /** 今選んでいるフォルダ。読み込み中はnull */
  val selected: StateFlow<List<ScanFolder>?> = _selected.asStateFlow()

  private val _counts = MutableStateFlow<Map<ScanFolder, Int>>(emptyMap())
  /** フォルダごとの曲数 (端末にある音楽ファイルの数) */
  val counts: StateFlow<Map<ScanFolder, Int>> = _counts.asStateFlow()

  val volumeLabels: Map<String, String> by lazy { library.volumeLabels() }

  init {
    viewModelScope.launch {
      set(normalize(library.scanFolders()))
    }
  }

  private fun set(folders: List<ScanFolder>) {
    // 内部ストレージを先に、その中は名前の順に並べる
    _selected.value = folders.sortedWith(
      compareBy<ScanFolder>({ it.volume != ScanFolder.PRIMARY }, { it.volume.lowercase() }, { it.relativePath.lowercase() }),
    )
    viewModelScope.launch { _counts.value = library.trackCounts(folders) }
  }

  /** 追加した結果 */
  enum class AddResult { ADDED, ALREADY_INCLUDED }

  /** フォルダを足す。すでに選んでいるフォルダの中なら足さない。中のフォルダを選んでいたら、足したフォルダにまとめる */
  fun add(folder: ScanFolder): AddResult {
    val current = _selected.value.orEmpty()
    if (current.any { it.contains(folder.volume, folder.relativePath) }) return AddResult.ALREADY_INCLUDED
    set(normalize(current + folder))
    return AddResult.ADDED
  }

  fun remove(folder: ScanFolder) {
    set(_selected.value.orEmpty() - folder)
  }

  /** 保存して読み直す (変わっていなくても読み直す)。画面を閉じた後も続ける */
  fun save() {
    val folders = _selected.value ?: return
    library.launchInBackground { library.setScanFolders(folders) }
  }
}
