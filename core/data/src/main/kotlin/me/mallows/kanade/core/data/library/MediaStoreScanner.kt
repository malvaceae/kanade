package me.mallows.kanade.core.data.library

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.os.storage.StorageManager
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import me.mallows.kanade.core.data.db.TrackEntity
import me.mallows.kanade.core.domain.folder.ScanFolder
import me.mallows.kanade.core.domain.folder.StoragePaths
import me.mallows.kanade.core.domain.folder.likePrefix
import me.mallows.kanade.core.domain.folder.normalize

/**
 * MediaStoreから端末内の音楽ファイルを読み込む。
 * OSによる違いはこの中に閉じ込め、外からはボリューム名と相対パスだけで扱えるようにする。
 * - API 29以上: `RELATIVE_PATH`・`VOLUME_NAME`で読む
 * - API 28以下: 曲の絶対パス (`DATA`) で絞り、ボリューム名と相対パスは絶対パスから作る ([StoragePaths])
 * - ジャンル: API 30以上は`GENRE`の列、API 29以下はジャンルの表 (`Genres`) から引く
 * ボリューム名は、どの版でもAPI 29以上の`VOLUME_NAME`と同じ形なので、OSを上げても設定とDBはそのまま使える。
 */
class MediaStoreScanner @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val storage: StorageVolumes,
) {
  private val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

  /** 端末にあるストレージのボリューム名 (内部ストレージ・SDカードなど) */
  fun volumes(): Set<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.getExternalVolumeNames(context)
    else storage.list().map { it.name }.toSet()

  /** ボリューム名 → 画面に出す名前 (「内部共有ストレージ」「SDカード」など、端末の言葉のまま) */
  fun volumeLabels(): Map<String, String> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
      // Android 6.0は端末の名前を取れないので、自前の名前にする
      return storage.list().associate { it.name to if (it.primary) "内部ストレージ" else "SDカード" }
    }
    val manager = context.getSystemService(StorageManager::class.java) ?: return emptyMap()
    return manager.storageVolumes.mapNotNull { v ->
      val name = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> v.mediaStoreVolumeName
        v.isPrimary -> ScanFolder.PRIMARY
        else -> v.uuid?.lowercase()
      } ?: return@mapNotNull null
      name to v.getDescription(context)
    }.toMap()
  }

  /**
   * 対象フォルダ ([folders]) の配下の音声ファイル。フォルダが空なら何も読まない。
   * 「音楽」の印 (IS_MUSIC) では絞らない。選んだフォルダの音声は全部読む (Recordings/ など、印の付かない場所の曲も読めるように)
   */
  fun scan(folders: List<ScanFolder>): List<TrackEntity> {
    val tracks = mutableListOf<TrackEntity>()
    val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    // API 28以下は、絶対パスから相対パスとボリューム名を作るため、ストレージの根が要る
    val volumes = if (modern) emptyList() else storage.list()
    val genres = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) null else genresByTrack()
    // 古いSQLiteの変数の上限 (999) を超えないよう、1回の問い合わせのフォルダ数を抑える
    for (chunk in normalize(folders).chunked(FOLDERS_PER_QUERY)) {
      val selection: String
      val args: Array<String>
      if (modern) {
        selection = buildString {
          append("(")
          chunk.indices.forEach { i ->
            if (i > 0) append(" OR ")
            append("(").append(MediaStore.Audio.Media.VOLUME_NAME).append(" = ? AND ")
            append(MediaStore.Audio.Media.RELATIVE_PATH).append(" LIKE ? ESCAPE '\\')")
          }
          append(")")
        }
        args = chunk.flatMap { listOf(it.volume, likePrefix(it.relativePath)) }.toTypedArray()
      } else {
        // つながっていないストレージのフォルダは、探しようがないので飛ばす
        val prefixes = chunk.mapNotNull { f ->
          volumes.firstOrNull { it.name.equals(f.volume, ignoreCase = true) }?.let { StoragePaths.likePrefix(it.root, f.relativePath) }
        }
        if (prefixes.isEmpty()) continue
        selection = prefixes.joinToString(" OR ", "(", ")") { "$DATA LIKE ? ESCAPE '\\'" }
        args = prefixes.toTypedArray()
      }
      context.contentResolver.query(collection, projection(), selection, args, null)?.use { c ->
        while (c.moveToNext()) c.toTrack(volumes, genres)?.let(tracks::add)
      }
    }
    // フォルダは親子を除いてあるので重ならないが、念のためIDで1つにする
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

  /** フォルダごとの曲数 (直接入っている数)。フォルダの選択画面を開いたときだけ使う */
  fun folderCounts(): List<Triple<String, String, Int>> {
    val counts = HashMap<Pair<String, String>, Int>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
    } else {
      val volumes = storage.list()
      context.contentResolver.query(collection, arrayOf(DATA), null, null, null)?.use { c ->
        while (c.moveToNext()) {
          val (volume, path) = c.string(DATA)?.let { locate(volumes, it) } ?: continue
          val key = volume to path
          counts[key] = (counts[key] ?: 0) + 1
        }
      }
    }
    return counts.map { (k, n) -> Triple(k.first, k.second, n) }
  }

  /** API 28以下: 曲の絶対パスから、(ボリューム名, 相対パス) を作る。どのストレージにも当たらなければnull */
  private fun locate(volumes: List<StorageVolumes.Volume>, absolutePath: String): Pair<String, String>? {
    val located = StoragePaths.relativePathOf(volumes.map { it.root }, absolutePath) ?: return null
    val volume = volumes.first { it.root.trimEnd('/').equals(located.root, ignoreCase = true) }
    return volume.name to located.relativePath
  }

  /**
   * API 29以下: ジャンルの表から「曲のID → ジャンル名」を作る。ジャンルの一覧と、各ジャンルの曲を1回ずつ読む (曲ごとには問い合わせない)。
   * 複数のジャンルに入っている曲は、最初のジャンルにする
   */
  private fun genresByTrack(): Map<Long, String> {
    val result = HashMap<Long, String>()
    val resolver = context.contentResolver
    resolver.query(
      MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
      arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME), null, null, null,
    )?.use { genres ->
      while (genres.moveToNext()) {
        val name = genres.getString(1)?.takeUnless { it.isBlank() } ?: continue
        val members = MediaStore.Audio.Genres.Members.getContentUri("external", genres.getLong(0))
        resolver.query(members, arrayOf(MediaStore.Audio.Media._ID), null, null, null)?.use { c ->
          while (c.moveToNext()) {
            // putIfAbsentはAndroid 7.0以降なので使わない
            val id = c.getLong(0)
            if (id !in result) result[id] = name
          }
        }
      }
    }
    return result
  }

  private fun Cursor.toTrack(volumes: List<StorageVolumes.Volume>, genres: Map<Long, String>?): TrackEntity? {
    val id = long(MediaStore.Audio.Media._ID) ?: return null
    val number = TrackNumber.fromMediaStore(int(MediaStore.Audio.Media.TRACK))
    val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    val located = if (modern) null else string(DATA)?.let { locate(volumes, it) }
    return TrackEntity(
      mediaStoreId = id,
      uri = ContentUris.withAppendedId(collection, id).toString(),
      title = string(MediaStore.Audio.Media.TITLE)
        ?: string(MediaStore.Audio.Media.DISPLAY_NAME)
        ?: id.toString(),
      artist = string(MediaStore.Audio.Media.ARTIST).orUnknownNull(),
      albumArtist = string(ALBUM_ARTIST).orUnknownNull(),
      album = string(MediaStore.Audio.Media.ALBUM).orUnknownNull(),
      albumId = long(MediaStore.Audio.Media.ALBUM_ID),
      year = int(MediaStore.Audio.Media.YEAR)?.takeIf { it > 0 },
      trackNo = number.track,
      discNo = number.disc,
      durationMs = long(MediaStore.Audio.Media.DURATION) ?: 0,
      mime = string(MediaStore.Audio.Media.MIME_TYPE),
      dateAdded = long(MediaStore.Audio.Media.DATE_ADDED) ?: 0,
      dateModified = long(MediaStore.Audio.Media.DATE_MODIFIED) ?: 0,
      relativePath = if (modern) string(MediaStore.Audio.Media.RELATIVE_PATH) else located?.second,
      genre = if (genres == null) string(MediaStore.Audio.Media.GENRE).orUnknownNull() else genres[id],
      volume = if (modern) string(MediaStore.Audio.Media.VOLUME_NAME) else located?.first,
    )
  }

  private fun Cursor.index(column: String) = getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }
  private fun Cursor.string(column: String) = index(column)?.let(::getString)
  private fun Cursor.long(column: String) = index(column)?.let(::getLong)
  private fun Cursor.int(column: String) = index(column)?.let(::getInt)

  /** MediaStoreが不明な値に入れる「<unknown>」を空として扱う */
  private fun String?.orUnknownNull(): String? = this?.takeUnless { it.isBlank() || it == MediaStore.UNKNOWN_STRING }

  /** 読む列。API 29以上は`RELATIVE_PATH`・`VOLUME_NAME`、API 28以下は`DATA`。`GENRE`の列はAPI 30以上 */
  private fun projection(): Array<String> = buildList {
    addAll(BASE_PROJECTION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      add(MediaStore.Audio.Media.RELATIVE_PATH)
      add(MediaStore.Audio.Media.VOLUME_NAME)
    } else {
      add(DATA)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(MediaStore.Audio.Media.GENRE)
  }.toTypedArray()
}

private const val FOLDERS_PER_QUERY = 400

/**
 * アルバムアーティストの列。公開された定数 (`ALBUM_ARTIST`) はAPI 30以上だが、列そのものは、
 * Android 6.0・9のエミュレーターで、API 29以下にも同じ名前であることを確かめた。
 */
private const val ALBUM_ARTIST = "album_artist"

/** 曲の絶対パスの列。API 29で非推奨だが、API 28以下では、これでしか場所が分からないので使う */
@Suppress("DEPRECATION")
private val DATA = MediaStore.Audio.Media.DATA

private val BASE_PROJECTION = listOf(
  MediaStore.Audio.Media._ID,
  MediaStore.Audio.Media.TITLE,
  MediaStore.Audio.Media.ARTIST,
  ALBUM_ARTIST,
  MediaStore.Audio.Media.ALBUM,
  MediaStore.Audio.Media.ALBUM_ID,
  MediaStore.Audio.Media.YEAR,
  MediaStore.Audio.Media.TRACK,
  MediaStore.Audio.Media.DURATION,
  MediaStore.Audio.Media.MIME_TYPE,
  MediaStore.Audio.Media.DISPLAY_NAME,
  MediaStore.Audio.Media.DATE_ADDED,
  MediaStore.Audio.Media.DATE_MODIFIED,
)
