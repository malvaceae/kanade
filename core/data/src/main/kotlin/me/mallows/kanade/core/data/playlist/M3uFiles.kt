package me.mallows.kanade.core.data.playlist

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.mallows.kanade.core.data.db.PlaylistDao
import me.mallows.kanade.core.data.db.TrackDao
import me.mallows.kanade.core.domain.m3u.M3u
import me.mallows.kanade.core.domain.m3u.M3uEntry
import me.mallows.kanade.core.domain.m3u.PathMatcher

/** 読み込みの結果 */
data class M3uImportResult(val playlistId: Long, val name: String, val added: Int, val missing: Int)

/** プレイリストをM3U8ファイルに書き出し、M3U・M3U8ファイルから読み込む */
@Singleton
class M3uFiles @Inject constructor(
  @ApplicationContext private val context: Context,
  private val playlists: PlaylistRepository,
  private val playlistDao: PlaylistDao,
  private val trackDao: TrackDao,
  private val files: LibraryFiles,
) {
  private val resolver get() = context.contentResolver

  /** [uri] のファイルを読み、ファイル名を名前にした新しいプレイリストを作る */
  suspend fun import(uri: Uri): M3uImportResult = withContext(Dispatchers.IO) {
    val text = resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
      ?: error("ファイルを開けない: $uri")
    val entries = M3u.parse(text)
    val matcher = PathMatcher(files.list())
    val mediaIds = entries.mapNotNull { matcher.match(it.path) }
    val tracks = trackDao.getByMediaStoreIds(mediaIds.distinct()).associateBy { it.mediaStoreId }
    val trackIds = mediaIds.mapNotNull { tracks[it]?.id }
    val name = displayName(uri)?.substringBeforeLast('.')?.ifBlank { null } ?: "M3U"
    val id = playlists.create(name)
    playlists.add(id, trackIds)
    M3uImportResult(id, name, trackIds.size, entries.size - trackIds.size)
  }

  /** プレイリストを [uri] にM3U8 (UTF-8・絶対パス) で書き出す。戻り値は書いた曲数 */
  suspend fun export(playlistId: Long, uri: Uri): Int = withContext(Dispatchers.IO) {
    val items = playlistDao.observeItems(playlistId).first().sortedBy { it.position }
    val paths = files.list(items.map { it.track.mediaStoreId }).toMap()
    val entries = items.mapNotNull { item ->
      val t = item.track
      val path = paths[t.mediaStoreId] ?: return@mapNotNull null
      val title = listOfNotNull(t.artist, t.title).joinToString(" - ")
      M3uEntry("${LibraryFiles.STORAGE_ROOT}/$path", t.durationMs / 1000, title)
    }
    resolver.openOutputStream(uri, "wt")?.use { it.write(M3u.format(entries).toByteArray(Charsets.UTF_8)) }
      ?: error("ファイルに書けない: $uri")
    entries.size
  }

  private fun displayName(uri: Uri): String? =
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
      if (c.moveToFirst()) c.getString(0) else null
    }
}
