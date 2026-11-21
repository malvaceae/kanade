package me.mallows.kanade.core.data.library

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import timber.log.Timber
import me.mallows.kanade.core.data.db.MEDIA_TYPE_VIDEO
import me.mallows.kanade.core.data.db.TrackEntity
import me.mallows.kanade.core.domain.folder.ScanFolder
import me.mallows.kanade.core.domain.folder.StoragePaths
import me.mallows.kanade.core.domain.folder.likePrefix
import me.mallows.kanade.core.domain.folder.normalize

/**
 * MediaStoreから端末内の音楽ファイルと動画ファイルを読み込む。
 * OSによる違いはこの中に閉じ込め、外からはボリューム名と相対パスだけで扱えるようにする。
 * - API 29以上: `RELATIVE_PATH`・`VOLUME_NAME`で読む
 * - API 28以下: 曲の絶対パス (`DATA`) で絞り、ボリューム名と相対パスは絶対パスから作る ([StoragePaths])
 * - ジャンル: API 30以上は`GENRE`の列、API 29以下はジャンルの表 (`Genres`) から引く
 * ボリューム名は、どの版でもAPI 29以上の`VOLUME_NAME`と同じ形なので、OSを上げても設定とDBはそのまま使える。
 * 動画は、音声と同じ対象フォルダの設定で読む。動画の列はOSによって違うので、端末が持っている列だけを読む ([videoProjection])。
 */
class MediaStoreScanner @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val storage: StorageVolumes,
) {
  private val audioCollection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
  private val videoCollection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

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
   * 動画を読む権限があるか。Android 13以降は、音声とは別の権限 (`READ_MEDIA_VIDEO`) が要る。
   * なければ動画は読めず、ライブラリから消すのではなく、隠れたままにする ([LibraryRepository])
   */
  fun canReadVideo(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      context.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED

  /**
   * 対象フォルダ ([folders]) の配下の音声ファイルと動画ファイル。フォルダが空なら何も読まない。
   * 「音楽」の印 (IS_MUSIC) では絞らない。選んだフォルダの音声は全部読む (Recordings/ など、印の付かない場所の曲も読めるように)
   */
  fun scan(folders: List<ScanFolder>): List<TrackEntity> {
    val tracks = mutableListOf<TrackEntity>()
    val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    // API 28以下は、絶対パスから相対パスとボリューム名を作るため、ストレージの根が要る
    val volumes = if (modern) emptyList() else storage.list()
    val genres = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) null else genresByTrack()
    queryInFolders(audioCollection, projection(), folders, volumes) { c -> c.toTrack(volumes, genres)?.let(tracks::add) }
    if (canReadVideo()) {
      // 動画が読めなくても、音声のスキャンは止めない
      runCatching {
        queryInFolders(videoCollection, videoProjection(), folders, volumes) { c -> c.toVideo(volumes)?.let(tracks::add) }
      }.onFailure { Timber.w(it, "動画を読めませんでした") }
    }
    // フォルダは親子を除いてあるので重ならないが、念のためIDで1つにする
    return tracks.distinctBy { it.mediaStoreId }
  }

  /** [collection]から、対象フォルダ ([folders]) の配下の行を読み、1行ずつ[onRow]に渡す */
  private fun queryInFolders(
    collection: Uri,
    projection: Array<String>,
    folders: List<ScanFolder>,
    volumes: List<StorageVolumes.Volume>,
    onRow: (Cursor) -> Unit,
  ) {
    val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
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
      context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
        while (c.moveToNext()) onRow(c)
      }
    }
  }

  /** 端末にある全曲 (音声と動画) のIDだけ。消えた曲と対象外の曲を見分けるのに使う (画面には出さない)。動画を読む権限がなければ、動画は含まれない */
  fun existingIds(): Set<Long> {
    val ids = HashSet<Long>()
    for (collection in readableCollections()) {
      context.contentResolver.query(collection, arrayOf(MediaStore.Audio.Media._ID), null, null, null)?.use { c ->
        val i = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        while (c.moveToNext()) ids += c.getLong(i)
      }
    }
    return ids
  }

  /** 今読める集まり (音声は常に、動画は権限があるとき) */
  private fun readableCollections(): List<Uri> =
    if (canReadVideo()) listOf(audioCollection, videoCollection) else listOf(audioCollection)

  /** フォルダごとの曲数 (直接入っている数。音声と動画の合計)。フォルダの選択画面を開いたときだけ使う */
  fun folderCounts(): List<Triple<String, String, Int>> {
    val counts = HashMap<Pair<String, String>, Int>()
    val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    val volumes = if (modern) emptyList() else storage.list()
    for (collection in readableCollections()) {
      if (modern) {
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
        context.contentResolver.query(collection, arrayOf(DATA), null, null, null)?.use { c ->
          while (c.moveToNext()) {
            val (volume, path) = c.string(DATA)?.let { locate(volumes, it) } ?: continue
            val key = volume to path
            counts[key] = (counts[key] ?: 0) + 1
          }
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
      uri = ContentUris.withAppendedId(audioCollection, id).toString(),
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
      genre = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) string(MediaStore.Audio.Media.GENRE).orUnknownNull() else genres?.get(id),
      volume = if (modern) string(MediaStore.Audio.Media.VOLUME_NAME) else located?.first,
    )
  }

  /** 動画1行を[TrackEntity]にする。動画の列はOSで違うので、無い列はnullとして扱う (トラック番号・アルバムのIDは動画にない) */
  private fun Cursor.toVideo(volumes: List<StorageVolumes.Volume>): TrackEntity? {
    val id = long(MediaStore.Video.Media._ID) ?: return null
    val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    val located = if (modern) null else string(DATA)?.let { locate(volumes, it) }
    return TrackEntity(
      mediaStoreId = id,
      uri = ContentUris.withAppendedId(videoCollection, id).toString(),
      title = string(MediaStore.Video.Media.TITLE)
        ?: string(MediaStore.Video.Media.DISPLAY_NAME)
        ?: id.toString(),
      artist = string(MediaStore.Video.Media.ARTIST).orUnknownNull(),
      albumArtist = string(ALBUM_ARTIST).orUnknownNull(),
      // タグのない動画は、MediaStoreがアルバム名に置き場所のフォルダ名を入れる。それはアルバムではないので外す
      album = videoAlbum(string(MediaStore.Video.Media.ALBUM).orUnknownNull(), string(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)),
      albumId = null,
      year = int(YEAR)?.takeIf { it > 0 },
      trackNo = null,
      discNo = null,
      durationMs = long(MediaStore.Video.Media.DURATION) ?: 0,
      mime = string(MediaStore.Video.Media.MIME_TYPE),
      dateAdded = long(MediaStore.Video.Media.DATE_ADDED) ?: 0,
      dateModified = long(MediaStore.Video.Media.DATE_MODIFIED) ?: 0,
      relativePath = if (modern) string(MediaStore.Video.Media.RELATIVE_PATH) else located?.second,
      genre = string(GENRE).orUnknownNull(),
      volume = if (modern) string(MediaStore.Video.Media.VOLUME_NAME) else located?.first,
      mediaType = MEDIA_TYPE_VIDEO,
    )
  }

  /**
   * 動画で読む列。Android 6.0には`album_artist`・`genre`・`year`・`relative_path`・`volume_name`の列がないので、
   * 端末が実際に持っている列だけを読む (無い列を問い合わせると、問い合わせごと失敗するため)。
   * 実際に確かめた違い: Android 6.0 (API 23) は上の5列が無く、Android 12 (API 31) は全部ある
   */
  private fun videoProjection(): Array<String> {
    val available = context.contentResolver.query(
      videoCollection, null, "${MediaStore.Video.Media._ID} = ?", arrayOf("-1"), null,
    )?.use { it.columnNames.toSet() }.orEmpty()
    return VIDEO_COLUMNS.filter { it == MediaStore.Video.Media._ID || it in available }.toTypedArray()
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

/** 動画の列のうち、OSによってはないもの (列の名前は、音声と同じ) */
private const val YEAR = "year"
private const val GENRE = "genre"

/** 動画で読みたい列 (無い列は、[MediaStoreScanner.videoProjection]で外す) */
private val VIDEO_COLUMNS = listOf(
  MediaStore.Video.Media._ID,
  MediaStore.Video.Media.TITLE,
  MediaStore.Video.Media.ARTIST,
  ALBUM_ARTIST,
  MediaStore.Video.Media.ALBUM,
  YEAR,
  GENRE,
  MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
  MediaStore.Video.Media.DURATION,
  MediaStore.Video.Media.MIME_TYPE,
  MediaStore.Video.Media.DISPLAY_NAME,
  MediaStore.Video.Media.DATE_ADDED,
  MediaStore.Video.Media.DATE_MODIFIED,
  MediaStore.Video.Media.RELATIVE_PATH,
  MediaStore.Video.Media.VOLUME_NAME,
  DATA,
)

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
