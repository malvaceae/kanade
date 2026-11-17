package me.mallows.kanade.core.data.library

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.mallows.kanade.core.data.db.AlbumSummary
import me.mallows.kanade.core.data.db.GroupSummary
import me.mallows.kanade.core.data.db.PlayStatsDao
import me.mallows.kanade.core.data.db.PlaylistDao
import me.mallows.kanade.core.data.db.TrackDao
import me.mallows.kanade.core.data.db.KanadeDatabase
import me.mallows.kanade.core.data.db.SyncResult
import me.mallows.kanade.core.data.db.TrackEntity
import me.mallows.kanade.core.data.settings.SettingsRepository
import me.mallows.kanade.core.domain.folder.ScanFolder
import timber.log.Timber

/** 曲をまとめる単位 */
enum class GroupKind {
  ALBUM, ARTIST, FOLDER, GENRE,
  /** 自動のリスト (最近追加・最近再生・よく再生) */
  RECENTLY_ADDED, RECENTLY_PLAYED, MOST_PLAYED,
}

/** 端末内の曲の一覧 */
@Singleton
class LibraryRepository @Inject constructor(
  private val db: KanadeDatabase,
  private val trackDao: TrackDao,
  private val playlistDao: PlaylistDao,
  private val playStatsDao: PlayStatsDao,
  private val scanner: MediaStoreScanner,
  private val settings: SettingsRepository,
) {
  private val scanLock = Mutex()
  /** 画面を閉じても続ける処理 (スキャンなど) のスコープ */
  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  /** 画面を閉じても止めずに続ける (スキャンの途中で画面を離れても、読み直しを最後まで行う) */
  fun launchInBackground(block: suspend () -> Unit) {
    appScope.launch { block() }
  }
  private val _scanning = MutableStateFlow(false)
  /** スキャン中か (どの画面から始めたスキャンでも) */
  val scanning: StateFlow<Boolean> = _scanning.asStateFlow()
  private val _scanResults = MutableSharedFlow<SyncResult>(extraBufferCapacity = 1)
  /** スキャンが終わるたびに結果を流す */
  val scanResults: SharedFlow<SyncResult> = _scanResults.asSharedFlow()

  val tracks: Flow<List<TrackEntity>> = trackDao.observeAll()
  val albums: Flow<List<AlbumSummary>> = trackDao.observeAlbums()

  fun groups(kind: GroupKind): Flow<List<GroupSummary>> = when (kind) {
    GroupKind.ARTIST -> trackDao.observeArtists()
    GroupKind.FOLDER -> trackDao.observeFolders()
    GroupKind.GENRE -> trackDao.observeGenres()
    GroupKind.ALBUM -> error("アルバムは albums を使う")
    GroupKind.RECENTLY_ADDED, GroupKind.RECENTLY_PLAYED, GroupKind.MOST_PLAYED -> error("自動のリストは groupTracks を使う")
  }

  fun albumTracks(albumId: Long?): Flow<List<TrackEntity>> = trackDao.observeAlbumTracks(albumId)

  fun groupTracks(kind: GroupKind, name: String?): Flow<List<TrackEntity>> = when (kind) {
    GroupKind.ARTIST -> trackDao.observeArtistTracks(name)
    GroupKind.FOLDER -> {
      // nameは「ボリューム名:フォルダ」(observeFoldersで作る)
      val key = name.orEmpty()
      trackDao.observeFolderTracks(key.substringBefore(':'), key.substringAfter(':'))
    }
    GroupKind.GENRE -> trackDao.observeGenreTracks(name)
    GroupKind.ALBUM -> trackDao.observeAlbumTracks(name?.toLongOrNull())
    GroupKind.RECENTLY_ADDED -> trackDao.observeRecentlyAdded(SMART_LIMIT)
    GroupKind.RECENTLY_PLAYED -> playStatsDao.observeRecentlyPlayed(SMART_LIMIT)
    GroupKind.MOST_PLAYED -> playStatsDao.observeMostPlayed(SMART_LIMIT)
  }

  fun search(query: String): Flow<List<TrackEntity>> = trackDao.search(query)

  suspend fun tracksByIds(ids: List<Long>): List<TrackEntity> {
    val byId = trackDao.getByIds(ids).associateBy { it.id }
    return ids.mapNotNull(byId::get)
  }

  /** 今の対象フォルダ。まだ選んでいなければ、今あるストレージそれぞれの「Music/」 */
  suspend fun scanFolders(): List<ScanFolder> =
    settings.scanFolders.first() ?: ScanFolder.defaults(withContext(Dispatchers.IO) { scanner.volumes() })

  /**
   * MediaStoreを読み直してデータベースに反映する。音楽ファイルの読み取り権限が必要。
   * 対象フォルダの外になった曲は消さずに印を付け、端末から消えた曲だけを消す。
   * 同時に2つ走らないよう、前のスキャンが終わるのを待つ。
   */
  suspend fun rescan(): SyncResult = scanLock.withLock {
    _scanning.value = true
    try {
      val folders = scanFolders()
      val result = withContext(Dispatchers.IO) {
        // MediaStoreの問い合わせはトランザクションの外で行い、DBへの書き込みだけをまとめる
        val included = scanner.scan(folders)
        val existing = scanner.existingIds()
        db.withTransaction {
          trackDao.sync(included, existing).also { playlistDao.deleteOrphans() }
        }
      }
      settings.setInitialScanDone()
      Timber.d("ライブラリを更新: 対象 %d曲、対象外 %d曲、削除 %d曲", result.included, result.excluded, result.deleted)
      _scanResults.tryEmit(result)
      result
    } finally {
      _scanning.value = false
    }
  }

  /** 本当の初回 (まだ1度もスキャンしていない) ときだけスキャンする。アプリを起動しただけでは読み直さない */
  suspend fun rescanIfFirstTime() {
    if (!settings.initialScanDone.first()) rescan()
  }

  /** 対象フォルダを保存して読み直す (0件でも保存する。そのときライブラリは空になる) */
  suspend fun setScanFolders(folders: List<ScanFolder>) {
    settings.setScanFolders(folders)
    rescan()
  }


  /** [folders]それぞれの配下の曲数 (端末にある音楽ファイルの数)。フォルダの選択画面に出す */
  suspend fun trackCounts(folders: List<ScanFolder>): Map<ScanFolder, Int> = withContext(Dispatchers.IO) {
    val counts = scanner.folderCounts()
    folders.associateWith { f -> counts.filter { (volume, path, _) -> f.contains(volume, path) }.sumOf { it.third } }
  }


  /** ボリューム名 → 画面に出す名前 */
  fun volumeLabels(): Map<String, String> = scanner.volumeLabels()

  /** スキャン対象外の曲のMediaStoreのID。再生キューで薄く出し、再生では飛ばすのに使う */
  val excludedIds: Flow<Set<Long>> = trackDao.observeExcludedIds().map { it.toSet() }

}

/** 自動のリストに出す最大の曲数 */
private const val SMART_LIMIT = 100
