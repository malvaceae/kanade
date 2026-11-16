package me.mallows.kanade.core.data.library

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import me.mallows.kanade.core.data.db.TrackDao
import me.mallows.kanade.core.data.db.TrackEntity
import timber.log.Timber

/** 端末内の曲の一覧 */
@Singleton
class LibraryRepository @Inject constructor(
  private val trackDao: TrackDao,
  private val scanner: MediaStoreScanner,
) {

  val tracks: Flow<List<TrackEntity>> = trackDao.observeAll()

  /** MediaStoreを読み直してデータベースに反映する。音楽ファイルの読み取り権限が必要 */
  suspend fun rescan() = withContext(Dispatchers.IO) {
    val found = scanner.scan()
    trackDao.sync(found)
    Timber.d("ライブラリを更新: %d曲", found.size)
  }
}
