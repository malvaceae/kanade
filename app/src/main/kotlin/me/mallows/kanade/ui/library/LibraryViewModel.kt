package me.mallows.kanade.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.mallows.kanade.core.data.db.TrackEntity
import me.mallows.kanade.core.data.library.LibraryRepository

@HiltViewModel
class LibraryViewModel @Inject constructor(
  private val library: LibraryRepository,
) : ViewModel() {

  /** null は読み込み前 */
  val tracks: StateFlow<List<TrackEntity>?> = library.tracks
    .map<List<TrackEntity>, List<TrackEntity>?> { it }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  private val _scanning = MutableStateFlow(false)
  val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

  private var scanned = false

  /** 画面を開いたときに1回だけMediaStoreを読み直す */
  fun scanOnce() {
    if (scanned) return
    scanned = true
    viewModelScope.launch {
      _scanning.value = true
      try {
        library.rescan()
      } finally {
        _scanning.value = false
      }
    }
  }
}
