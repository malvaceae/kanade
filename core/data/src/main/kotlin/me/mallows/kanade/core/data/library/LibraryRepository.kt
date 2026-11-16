package me.mallows.kanade.core.data.library

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import me.mallows.kanade.core.data.db.AlbumSummary
import me.mallows.kanade.core.data.db.GroupSummary
import me.mallows.kanade.core.data.db.PlayStatsDao
import me.mallows.kanade.core.data.db.PlaylistDao
import me.mallows.kanade.core.data.db.TrackDao
import me.mallows.kanade.core.data.db.TrackEntity
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
  private val trackDao: TrackDao,
  private val playlistDao: PlaylistDao,
  private val playStatsDao: PlayStatsDao,
  private val scanner: MediaStoreScanner,
) {

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
    GroupKind.FOLDER -> trackDao.observeFolderTracks(name)
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

  /** MediaStoreを読み直してデータベースに反映する。音楽ファイルの読み取り権限が必要 */
  suspend fun rescan() = withContext(Dispatchers.IO) {
    val found = scanner.scan()
    trackDao.sync(found)
    playlistDao.deleteOrphans()
    Timber.d("ライブラリを更新: %d曲", found.size)
  }
}

/** 自動のリストに出す最大の曲数 */
private const val SMART_LIMIT = 100
