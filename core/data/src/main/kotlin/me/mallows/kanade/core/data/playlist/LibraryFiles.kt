package me.mallows.kanade.core.data.playlist

import android.content.Context
import android.os.storage.StorageManager
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 端末内の曲のファイルのパス (M3Uやバックアップで、曲を端末をまたいで指すために使う) */
@Singleton
class LibraryFiles @Inject constructor(@ApplicationContext private val context: Context) {

  /** (MediaStoreのID, 「Music/Album/曲.flac」のようなパス)。[ids] を渡すとその曲だけ */
  fun list(ids: List<Long>? = null): List<Pair<Long, String>> {
    if (ids != null && ids.isEmpty()) return emptyList()
    val projection = arrayOf(
      MediaStore.Audio.Media._ID,
      MediaStore.Audio.Media.RELATIVE_PATH,
      MediaStore.Audio.Media.DISPLAY_NAME,
    )
    val selection = ids?.let { "${MediaStore.Audio.Media._ID} IN (${it.joinToString(",")})" }
    val result = mutableListOf<Pair<Long, String>>()
    context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, null)?.use { c ->
      while (c.moveToNext()) {
        val name = c.getString(2) ?: continue
        result += c.getLong(0) to (c.getString(1).orEmpty() + name)
      }
    }
    return result
  }

  /**
   * (MediaStoreのID → 絶対パス)。M3Uの書き出しで、ほかのプレイヤーも読めるパスにするために使う。
   * パスの先頭は曲のあるストレージの根 (内部ストレージは「/storage/emulated/0」、SDカードは「/storage/1234-ABCD」など)
   */
  fun absolutePaths(ids: List<Long>): Map<Long, String> {
    if (ids.isEmpty()) return emptyMap()
    val roots = storageRoots()
    val projection = arrayOf(
      MediaStore.Audio.Media._ID,
      MediaStore.Audio.Media.VOLUME_NAME,
      MediaStore.Audio.Media.RELATIVE_PATH,
      MediaStore.Audio.Media.DISPLAY_NAME,
    )
    val result = HashMap<Long, String>()
    for (chunk in ids.distinct().chunked(500)) {
      val selection = "${MediaStore.Audio.Media._ID} IN (${chunk.joinToString(",")})"
      context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, null)?.use { c ->
        while (c.moveToNext()) {
          val name = c.getString(3) ?: continue
          val volume = c.getString(1).orEmpty()
          val root = roots[volume] ?: fallbackRoot(volume)
          result[c.getLong(0)] = "$root/${c.getString(2).orEmpty()}$name"
        }
      }
    }
    return result
  }

  /** ボリューム名 → 根のパス (つながっているストレージだけ) */
  private fun storageRoots(): Map<String, String> {
    val storage = context.getSystemService(StorageManager::class.java) ?: return emptyMap()
    return storage.storageVolumes.mapNotNull { v ->
      val name = v.mediaStoreVolumeName ?: return@mapNotNull null
      val dir = v.directory ?: return@mapNotNull null
      name to dir.absolutePath.trimEnd('/')
    }.toMap()
  }

  /** 外したSDカードなど、根が分からないときの推測 (「/storage/<ボリューム名の大文字>」の形が多い) */
  private fun fallbackRoot(volume: String): String =
    if (volume.isEmpty() || volume == MediaStore.VOLUME_EXTERNAL_PRIMARY) PRIMARY_ROOT else "/storage/${volume.uppercase()}"

  private companion object {
    const val PRIMARY_ROOT = "/storage/emulated/0"
  }
}
