package me.mallows.kanade.core.data.playlist

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * プレイリストのカバー画像。選んだ画像を縮めてアプリの中に保存する
 * (写真の選択画面から受け取ったURIは一時的なもので、後から読めなくなるため)
 */
class PlaylistCovers @Inject constructor(
  @param:ApplicationContext private val context: Context,
) {
  private val dir get() = File(context.filesDir, "playlist_covers").apply { mkdirs() }

  /** 画像を読んで保存し、ファイルのパスを返す。読めなければnull */
  suspend fun save(playlistId: Long, image: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
      val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, image)) { decoder, info, _ ->
        // 短い辺がSIZEになるよう縮める (一覧では正方形に切り抜いて出す)
        val shorter = minOf(info.size.width, info.size.height)
        if (shorter > SIZE) {
          val scale = SIZE.toFloat() / shorter
          decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
        }
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
      }
      // 同じ名前だと表示のキャッシュが古い画像を出すので、保存するたびに名前を変える
      val file = File(dir, "$playlistId-${System.currentTimeMillis()}.jpg")
      file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
      file.absolutePath
    }.onFailure { Timber.w(it, "カバー画像を読めませんでした") }.getOrNull()
  }

  fun delete(path: String?) {
    if (path != null) File(path).delete()
  }

  private companion object {
    const val SIZE = 800
  }
}
