package me.mallows.kanade.core.data.library

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import me.mallows.kanade.core.data.db.TrackEntity

/** MediaStoreから端末内の音楽ファイルを読み込む */
class MediaStoreScanner @Inject constructor(
  @ApplicationContext private val context: Context,
) {

  fun scan(): List<TrackEntity> {
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
      MediaStore.Audio.Media._ID,
      MediaStore.Audio.Media.TITLE,
      MediaStore.Audio.Media.ARTIST,
      MediaStore.Audio.Media.ALBUM_ARTIST,
      MediaStore.Audio.Media.ALBUM,
      MediaStore.Audio.Media.ALBUM_ID,
      MediaStore.Audio.Media.YEAR,
      MediaStore.Audio.Media.TRACK,
      MediaStore.Audio.Media.DURATION,
      MediaStore.Audio.Media.MIME_TYPE,
      MediaStore.Audio.Media.DISPLAY_NAME,
      MediaStore.Audio.Media.DATE_ADDED,
      MediaStore.Audio.Media.DATE_MODIFIED,
      MediaStore.Audio.Media.RELATIVE_PATH,
      MediaStore.Audio.Media.GENRE,
    )
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    val tracks = mutableListOf<TrackEntity>()
    context.contentResolver.query(collection, projection, selection, null, null)?.use { c ->
      while (c.moveToNext()) {
        val id = c.long(MediaStore.Audio.Media._ID) ?: continue
        val number = TrackNumber.fromMediaStore(c.int(MediaStore.Audio.Media.TRACK))
        tracks += TrackEntity(
          mediaStoreId = id,
          uri = ContentUris.withAppendedId(collection, id).toString(),
          title = c.string(MediaStore.Audio.Media.TITLE)
            ?: c.string(MediaStore.Audio.Media.DISPLAY_NAME)
            ?: id.toString(),
          artist = c.string(MediaStore.Audio.Media.ARTIST).orUnknownNull(),
          albumArtist = c.string(MediaStore.Audio.Media.ALBUM_ARTIST).orUnknownNull(),
          album = c.string(MediaStore.Audio.Media.ALBUM).orUnknownNull(),
          albumId = c.long(MediaStore.Audio.Media.ALBUM_ID),
          year = c.int(MediaStore.Audio.Media.YEAR)?.takeIf { it > 0 },
          trackNo = number.track,
          discNo = number.disc,
          durationMs = c.long(MediaStore.Audio.Media.DURATION) ?: 0,
          mime = c.string(MediaStore.Audio.Media.MIME_TYPE),
          dateAdded = c.long(MediaStore.Audio.Media.DATE_ADDED) ?: 0,
          dateModified = c.long(MediaStore.Audio.Media.DATE_MODIFIED) ?: 0,
          relativePath = c.string(MediaStore.Audio.Media.RELATIVE_PATH),
          genre = c.string(MediaStore.Audio.Media.GENRE).orUnknownNull(),
        )
      }
    }
    return tracks
  }

  private fun Cursor.index(column: String) = getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }
  private fun Cursor.string(column: String) = index(column)?.let(::getString)
  private fun Cursor.long(column: String) = index(column)?.let(::getLong)
  private fun Cursor.int(column: String) = index(column)?.let(::getInt)

  /** MediaStoreが不明な値に入れる「<unknown>」を空として扱う */
  private fun String?.orUnknownNull(): String? = this?.takeUnless { it.isBlank() || it == MediaStore.UNKNOWN_STRING }
}
