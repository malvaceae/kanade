package me.mallows.kanade.ui.common

import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.mallows.kanade.ui.icons.KanadeIcons

/** 一覧用の小さなアートワークのキャッシュ */
private object ThumbnailCache {
  private val bitmaps = LruCache<String, Bitmap>(16 * 1024 * 1024) // 約16MB
  private val missing = mutableSetOf<String>()

  fun get(key: String): Bitmap? = bitmaps.get(key)
  fun isMissing(key: String) = synchronized(missing) { key in missing }
  fun put(key: String, bitmap: Bitmap?) {
    if (bitmap == null) synchronized(missing) { missing += key } else bitmaps.put(key, bitmap)
  }
}

/** 読み終えた結果。[bitmap]がnullなら、絵がない */
private class Loaded(val bitmap: Bitmap?)

/** 曲のアートワーク (埋め込み画像など)。ない場合は音符の仮表示。[video]なら、動画のフレームを使い、仮表示と角の印を動画のものにする */
@Composable
fun TrackArtwork(uri: String, size: Dp, modifier: Modifier = Modifier, cornerRadius: Dp = 6.dp, video: Boolean = false) {
  val context = LocalContext.current
  val px = with(LocalDensity.current) { size.roundToPx() }
  val key = "$uri@$px"
  // 読み込み中 (null) と、読み終えて絵がない ([Loaded.bitmap]がnull) を分ける。読み込み中に仮の絵 (音符) を出すと、本物の絵が出る前に一瞬ちらつく
  val loaded by produceState<Loaded?>(
    initialValue = ThumbnailCache.get(key)?.let(::Loaded) ?: if (ThumbnailCache.isMissing(key)) Loaded(null) else null,
    key,
  ) {
    // 同じ場所で絵が変わったとき (カバーを変えたなど) は、前の絵のまま止まらないよう、新しい絵を探し直す
    val cached = ThumbnailCache.get(key)
    if (cached != null || ThumbnailCache.isMissing(key)) {
      value = Loaded(cached)
      return@produceState
    }
    value = Loaded(withContext(Dispatchers.IO) {
      runCatching {
        // プレイリストのカバー (アプリ内のファイル) はそのまま縮めて読む。曲はMediaStoreの縮小画像を使う
        if (uri.startsWith("file://")) decodeFile(uri.removePrefix("file://"), px)
        // 外部のアプリから開いたファイル (MediaStoreにない) は、MediaStoreの縮小画像がないので、埋め込み画像を直接読む
        else if (uri.toUri().authority != MediaStore.AUTHORITY) decodeEmbedded(context, uri.toUri(), px)
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) context.contentResolver.loadThumbnail(uri.toUri(), Size(px, px), null)
        else if (video) decodeVideoFrame(context, uri.toUri(), px)
        else decodeEmbedded(context, uri.toUri(), px)
      }
        .getOrNull()
        .also { ThumbnailCache.put(key, it) }
    })
  }
  Box(
    modifier = modifier
      .size(size)
      .clip(RoundedCornerShape(cornerRadius))
      .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    contentAlignment = Alignment.Center,
  ) {
    val result = loaded
    val image = result?.bitmap
    if (image != null) {
      Image(
        bitmap = image.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
    } else if (result != null) {
      Icon(
        imageVector = if (video) KanadeIcons.Video else KanadeIcons.MusicNote,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(size * 0.45f),
      )
    }
    // 動画の印 (右下の小さな再生マーク)。小さい絵には付けない
    if (video && size >= 36.dp) {
      Box(
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(3.dp)
          .size(size * 0.34f)
          .clip(CircleShape)
          .background(Color.Black.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(KanadeIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.2f))
      }
    }
  }
}

/** ファイルの画像を、[px]より小さくならない範囲で縮めて読む */
private fun decodeFile(path: String, px: Int): android.graphics.Bitmap? {
  val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
  android.graphics.BitmapFactory.decodeFile(path, bounds)
  var sample = 1
  while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= px) sample *= 2
  return android.graphics.BitmapFactory.decodeFile(path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
}

/**
 * Android 9以前 (loadThumbnailがない) では、ファイルに埋め込まれた画像を直接読み出して縮める。
 * 埋め込み画像がない曲は null (音符の仮表示)
 */
private fun decodeEmbedded(context: android.content.Context, uri: android.net.Uri, px: Int): android.graphics.Bitmap? {
  val retriever = android.media.MediaMetadataRetriever()
  try {
    retriever.setDataSource(context, uri)
    val bytes = retriever.embeddedPicture ?: return null
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= px) sample *= 2
    return android.graphics.BitmapFactory.decodeByteArray(
      bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample },
    )
  } finally {
    retriever.release()
  }
}

/** Android 9以前 (loadThumbnailがない) で、動画の最初のほうのフレームを縮めて読む。読めなければ null */
private fun decodeVideoFrame(context: android.content.Context, uri: android.net.Uri, px: Int): android.graphics.Bitmap? {
  val retriever = android.media.MediaMetadataRetriever()
  try {
    retriever.setDataSource(context, uri)
    // 先頭は真っ黒なことが多いので、1秒の位置に近いフレームを使う
    val frame = retriever.getFrameAtTime(1_000_000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
    val scale = px.toFloat() / minOf(frame.width, frame.height)
    if (scale >= 1f) return frame
    return android.graphics.Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt().coerceAtLeast(1), (frame.height * scale).toInt().coerceAtLeast(1), true)
  } finally {
    retriever.release()
  }
}
