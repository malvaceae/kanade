package me.mallows.kanade.core.data.playlist

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.scale
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
      val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) decodeWithImageDecoder(image) else decodeWithBitmapFactory(image)
      // 同じ名前だと表示のキャッシュが古い画像を出すので、保存するたびに名前を変える
      val file = File(dir, "$playlistId-${System.currentTimeMillis()}.jpg")
      file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
      file.absolutePath
    }.onFailure { Timber.w(it, "カバー画像を読めませんでした") }.getOrNull()
  }

  /** Android 9以降: 向き (EXIF) も反映して、短い辺がSIZEになるよう縮めて読む (一覧では正方形に切り抜いて出す) */
  @RequiresApi(Build.VERSION_CODES.P)
  private fun decodeWithImageDecoder(image: Uri): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, image)) { decoder, info, _ ->
      val shorter = minOf(info.size.width, info.size.height)
      if (shorter > SIZE) {
        val scale = SIZE.toFloat() / shorter
        decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
      }
      decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }

  /** Android 8.1以前 (ImageDecoderがない): BitmapFactoryで、短い辺がSIZEになるよう縮めて読む。写真の向き (EXIF) は反映しない */
  private fun decodeWithBitmapFactory(image: Uri): Bitmap {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(image)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = resolver.openInputStream(image)?.use { BitmapFactory.decodeStream(it, null, options) }
      ?: error("画像を開けませんでした")
    // 2のべき乗でしか縮められないので、短い辺がちょうどSIZEになるよう仕上げに縮める (ImageDecoderの場合と同じ大きさにそろえる)
    val shorter = minOf(decoded.width, decoded.height)
    if (shorter <= SIZE) return decoded
    val scale = SIZE.toFloat() / shorter
    return decoded.scale((decoded.width * scale).toInt(), (decoded.height * scale).toInt())
  }

  fun delete(path: String?) {
    if (path != null) File(path).delete()
  }

  private companion object {
    const val SIZE = 800
  }
}
