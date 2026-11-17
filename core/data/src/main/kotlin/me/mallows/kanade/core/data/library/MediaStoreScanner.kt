package me.mallows.kanade.core.data.library

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.os.storage.StorageManager
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import me.mallows.kanade.core.data.db.TrackEntity
import me.mallows.kanade.core.domain.folder.ScanFolder
import me.mallows.kanade.core.domain.folder.likePrefix
import me.mallows.kanade.core.domain.folder.normalize

/**
 * MediaStoreから端末内の音楽ファイルを読み込む。
 * OSによる違い (API 29以上はRELATIVE_PATH・VOLUME_NAME、API 28以下はDATA) はこの中に閉じ込め、
 * 外からはボリューム名と相対パスだけで扱えるようにする。
 * 今の対応範囲 (API 31以上) ではAPI 29以上の問い合わせだけを使う。API 28以下へ広げるときの分岐は
 * docs/07-scan-folders.mdの4.3にまとめてある (パスの関数は core/domain の folder にある)。
 */
class MediaStoreScanner @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  private val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

  /** 端末にあるストレージのボリューム名 (内部ストレージ・SDカードなど) */
  fun volumes(): Set<String> = MediaStore.getExternalVolumeNames(context)

  /** ボリューム名 → 画面に出す名前 (「内部共有ストレージ」「SDカード」など、端末の言葉のまま) */
  fun volumeLabels(): Map<String, String> {
    val storage = context.getSystemService(StorageManager::class.java) ?: return emptyMap()
    return storage.storageVolumes.mapNotNull { v ->
      val name = v.mediaStoreVolumeName ?: return@mapNotNull null
      name to v.getDescription(context)
    }.toMap()
  }

  /**
   * 対象フォルダー ([folders]) の配下の音声ファイル。フォルダーが空なら何も読まない。
   * 「音楽」の印 (IS_MUSIC) では絞らない。選んだフォルダーの音声は全部読む (Recordings/ など、印の付かない場所の曲も読めるように)
   */
  fun scan(folders: List<ScanFolder>): List<TrackEntity> {
    val tracks = mutableListOf<TrackEntity>()
    // 古いSQLiteの変数の上限 (999) を超えないよう、1回の問い合わせのフォルダー数を抑える
    for (chunk in normalize(folders).chunked(FOLDERS_PER_QUERY)) {
      val selection = buildString {
        append("(")
        chunk.indices.forEach { i ->
          if (i > 0) append(" OR ")
          append("(").append(MediaStore.Audio.Media.VOLUME_NAME).append(" = ? AND ")
          append(MediaStore.Audio.Media.RELATIVE_PATH).append(" LIKE ? ESCAPE '\\')")
        }
        append(")")
      }
      val args = chunk.flatMap { listOf(it.volume, likePrefix(it.relativePath)) }.toTypedArray()
      context.contentResolver.query(collection, PROJECTION, selection, args, null)?.use { c ->
        while (c.moveToNext()) c.toTrack()?.let(tracks::add)
      }
    }
    // フォルダーは親子を除いてあるので重ならないが、念のためIDで1つにする
    return tracks.distinctBy { it.mediaStoreId }
  }

  /** 端末にある全曲のIDだけ。消えた曲と対象外の曲を見分けるのに使う (画面には出さない) */
  fun existingIds(): Set<Long> {
    val ids = HashSet<Long>()
    context.contentResolver.query(
      collection, arrayOf(MediaStore.Audio.Media._ID), null, null, null,
    )?.use { c ->
      val i = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
      while (c.moveToNext()) ids += c.getLong(i)
    }
    return ids
  }

  /** フォルダーごとの曲数 (直接入っている数)。フォルダーの選択画面を開いたときだけ使う */
  fun folderCounts(): List<Triple<String, String, Int>> {
    val counts = HashMap<Pair<String, String>, Int>()
    context.contentResolver.query(
      collection,
      arrayOf(MediaStore.Audio.Media.VOLUME_NAME, MediaStore.Audio.Media.RELATIVE_PATH),
      null, null, null,
    )?.use { c ->
      while (c.moveToNext()) {
        val volume = c.string(MediaStore.Audio.Media.VOLUME_NAME) ?: continue
        val path = ScanFolder.dir(c.string(MediaStore.Audio.Media.RELATIVE_PATH).orEmpty())
        val key = volume to path
        counts[key] = (counts[key] ?: 0) + 1
      }
    }
    return counts.map { (k, n) -> Triple(k.first, k.second, n) }
  }

  private fun Cursor.toTrack(): TrackEntity? {
    val id = long(MediaStore.Audio.Media._ID) ?: return null
    val number = TrackNumber.fromMediaStore(int(MediaStore.Audio.Media.TRACK))
    return TrackEntity(
      mediaStoreId = id,
      uri = ContentUris.withAppendedId(collection, id).toString(),
      title = string(MediaStore.Audio.Media.TITLE)
        ?: string(MediaStore.Audio.Media.DISPLAY_NAME)
        ?: id.toString(),
      artist = string(MediaStore.Audio.Media.ARTIST).orUnknownNull(),
      albumArtist = string(MediaStore.Audio.Media.ALBUM_ARTIST).orUnknownNull(),
      album = string(MediaStore.Audio.Media.ALBUM).orUnknownNull(),
      albumId = long(MediaStore.Audio.Media.ALBUM_ID),
      year = int(MediaStore.Audio.Media.YEAR)?.takeIf { it > 0 },
      trackNo = number.track,
      discNo = number.disc,
      durationMs = long(MediaStore.Audio.Media.DURATION) ?: 0,
      mime = string(MediaStore.Audio.Media.MIME_TYPE),
      dateAdded = long(MediaStore.Audio.Media.DATE_ADDED) ?: 0,
      dateModified = long(MediaStore.Audio.Media.DATE_MODIFIED) ?: 0,
      relativePath = string(MediaStore.Audio.Media.RELATIVE_PATH),
      genre = string(MediaStore.Audio.Media.GENRE).orUnknownNull(),
      volume = string(MediaStore.Audio.Media.VOLUME_NAME),
    )
  }

  private fun Cursor.index(column: String) = getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }
  private fun Cursor.string(column: String) = index(column)?.let(::getString)
  private fun Cursor.long(column: String) = index(column)?.let(::getLong)
  private fun Cursor.int(column: String) = index(column)?.let(::getInt)

  /** MediaStoreが不明な値に入れる「<unknown>」を空として扱う */
  private fun String?.orUnknownNull(): String? = this?.takeUnless { it.isBlank() || it == MediaStore.UNKNOWN_STRING }
}

private const val FOLDERS_PER_QUERY = 400

private val PROJECTION = arrayOf(
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
  MediaStore.Audio.Media.VOLUME_NAME,
)
