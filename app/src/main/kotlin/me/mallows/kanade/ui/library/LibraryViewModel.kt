package me.mallows.kanade.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.mallows.kanade.core.data.db.AlbumSummary
import me.mallows.kanade.core.data.db.GroupSummary
import me.mallows.kanade.core.data.db.SyncResult
import me.mallows.kanade.core.data.db.TrackEntity
import me.mallows.kanade.core.data.library.GroupKind
import me.mallows.kanade.core.data.library.LibraryRepository
import me.mallows.kanade.core.data.backup.BackupManager
import me.mallows.kanade.core.data.backup.RestoreResult
import me.mallows.kanade.core.data.settings.SettingsRepository
import me.mallows.kanade.core.data.settings.SortSetting

/** ソート条件を覚える一覧の名前 */
object SortCategory {
  const val TRACKS = "tracks"
  const val ALBUMS = "albums"
  fun group(kind: GroupKind) = kind.name.lowercase()
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
  private val library: LibraryRepository,
  private val settings: SettingsRepository,
  private val backup: BackupManager,
) : ViewModel() {

  /** アルバムなどの中の全曲 */
  suspend fun groupTracks(kind: GroupKind, key: String?): List<TrackEntity> = library.groupTracks(kind, key).first()

  suspend fun exportBackup(uri: android.net.Uri) = backup.export(uri)
  /** バックアップから戻す。スキャン対象のフォルダーも戻るので、読み直す */
  suspend fun restoreBackup(uri: android.net.Uri): RestoreResult =
    backup.restore(uri).also { library.launchInBackground { library.rescan() } }


  private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
    stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

  /** タブの並びと表示 */
  val tabs: StateFlow<List<TabSetting>> = settings.libraryTabs.map(TabSetting::decode).state(TabSetting.decode(null))

  fun setTabs(tabs: List<TabSetting>) {
    viewModelScope.launch { settings.setLibraryTabs(TabSetting.encode(tabs)) }
  }

  val trackSort: StateFlow<SortSetting?> = settings.sort(SortCategory.TRACKS).state(null)
  val albumSort: StateFlow<SortSetting?> = settings.sort(SortCategory.ALBUMS).state(null)
  val albumGrid: StateFlow<Boolean> = settings.gridView(SortCategory.ALBUMS).map { it ?: true }.state(true)

  /** null は読み込み前 */
  val tracks: StateFlow<List<TrackEntity>?> =
    combine(library.tracks, settings.sort(SortCategory.TRACKS)) { list, sort ->
      list.sortedWith(sort, TrackSort.entries.toTypedArray(), TrackSort.TITLE)
    }.flowOn(Dispatchers.Default).state(null) // 日本語の並べ替えは曲数が多いと重いので、画面のスレッドでは行わない

  val albums: StateFlow<List<AlbumSummary>?> =
    combine(library.albums, settings.sort(SortCategory.ALBUMS)) { list, sort ->
      list.sortedWith(sort, AlbumSort.entries.toTypedArray(), AlbumSort.TITLE)
    }.flowOn(Dispatchers.Default).state(null)

  private val groupFlows = mutableMapOf<GroupKind, StateFlow<List<GroupSummary>?>>()
  private val groupSortFlows = mutableMapOf<GroupKind, StateFlow<SortSetting?>>()

  fun groups(kind: GroupKind): StateFlow<List<GroupSummary>?> = groupFlows.getOrPut(kind) {
    combine(library.groups(kind), settings.sort(SortCategory.group(kind))) { list, sort ->
      list.sortedWith(sort, GroupSort.entries.toTypedArray(), GroupSort.NAME)
    }.flowOn(Dispatchers.Default).state(null)
  }

  fun groupSort(kind: GroupKind): StateFlow<SortSetting?> = groupSortFlows.getOrPut(kind) {
    settings.sort(SortCategory.group(kind)).state(null)
  }

  fun setSort(category: String, setting: SortSetting) {
    viewModelScope.launch { settings.setSort(category, setting) }
  }

  fun setAlbumGrid(grid: Boolean) {
    viewModelScope.launch { settings.setGridView(SortCategory.ALBUMS, grid) }
  }

  /** スキャン中か (どの画面から始めたスキャンでも) */
  val scanning: StateFlow<Boolean> = library.scanning

  /** ボリューム名 → 画面に出す名前 (フォルダーの一覧で、内部ストレージ以外に付ける) */
  val volumeLabels: Map<String, String> by lazy { library.volumeLabels() }

  /** 本当の初回だけスキャンする。アプリを起動しただけでは読み直さない */
  fun scanIfFirstTime() {
    library.launchInBackground { library.rescanIfFirstTime() }
  }

  /** スキャンが終わるたびの結果 */
  val scanResults: SharedFlow<SyncResult> = library.scanResults

  /** 右上の︙メニューの「再スキャン」 */
  fun rescan() {
    library.launchInBackground { library.rescan() }
  }

}
