package me.mallows.kanade.ui.common

import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import android.graphics.Bitmap
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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

/** 曲のアートワーク (埋め込み画像など)。ない場合は音符の仮表示 */
@Composable
fun TrackArtwork(uri: String, size: Dp, modifier: Modifier = Modifier, cornerRadius: Dp = 6.dp) {
  val context = LocalContext.current
  val px = with(LocalDensity.current) { size.roundToPx() }
  val key = "$uri@$px"
  val bitmap by produceState(initialValue = ThumbnailCache.get(key), key) {
    if (value != null || ThumbnailCache.isMissing(key)) return@produceState
    value = withContext(Dispatchers.IO) {
      runCatching {
        // プレイリストのカバー (アプリ内のファイル) はそのまま縮めて読む。曲はMediaStoreの縮小画像を使う
        if (uri.startsWith("file://")) decodeFile(uri.removePrefix("file://"), px)
        else context.contentResolver.loadThumbnail(uri.toUri(), Size(px, px), null)
      }
        .getOrNull()
        .also { ThumbnailCache.put(key, it) }
    }
  }
  Box(
    modifier = modifier
      .size(size)
      .clip(RoundedCornerShape(cornerRadius))
      .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    contentAlignment = Alignment.Center,
  ) {
    val image = bitmap
    if (image != null) {
      Image(
        bitmap = image.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
    } else {
      Icon(
        imageVector = KanadeIcons.MusicNote,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(size * 0.45f),
      )
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
 * 再生中の曲の絵。再生情報に絵 (曲に埋め込まれた絵、またはプレイリストのカバー) があればそれを出し、
 * なければ端末の縮小画像を出す。プレイヤー画面と簡易プレイヤーで同じ絵になるよう、どちらもこれを使う。
 * 曲が変わっても、新しい絵を読み終わるまでは前の絵を出しておく (一瞬「絵なし」になるのを避ける)
 */
@Composable
fun NowPlayingArtwork(data: ByteArray?, fallbackUri: String, size: Dp, cornerRadius: Dp) {
  val context = LocalContext.current
  val px = with(LocalDensity.current) { size.roundToPx() }
  val key = "$fallbackUri@$px"
  var shown by remember { mutableStateOf(data?.let { null } ?: ThumbnailCache.get(key)) }
  LaunchedEffect(data, key) {
    shown = withContext(Dispatchers.IO) {
      data?.let { decodeBytes(it, px) }
        ?: ThumbnailCache.get(key)
        ?: if (ThumbnailCache.isMissing(key)) null
        else runCatching { context.contentResolver.loadThumbnail(fallbackUri.toUri(), Size(px, px), null) }
          .getOrNull()
          .also { ThumbnailCache.put(key, it) }
    }
  }
  Box(
    modifier = Modifier
      .size(size)
      .clip(RoundedCornerShape(cornerRadius))
      .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    contentAlignment = Alignment.Center,
  ) {
    val image = shown
    if (image != null) {
      Image(bitmap = image.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    } else {
      Icon(
        imageVector = KanadeIcons.MusicNote,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(size * 0.4f),
      )
    }
  }
}

/** 画像のデータを、[px]より小さくならない範囲で縮めて読む */
private fun decodeBytes(bytes: ByteArray, px: Int): android.graphics.Bitmap? {
  val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
  android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
  var sample = 1
  while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= px) sample *= 2
  return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
}
