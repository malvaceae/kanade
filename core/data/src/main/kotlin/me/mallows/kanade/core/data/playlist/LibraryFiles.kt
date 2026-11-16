package me.mallows.kanade.core.data.playlist

import android.content.Context
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

  companion object {
    /** 内部共有ストレージ。多くのプレイヤーが読める絶対パスにするときに前に付ける */
    const val STORAGE_ROOT = "/storage/emulated/0"
  }
}
