package me.mallows.kanade.playback

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.core.graphics.scale
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 今の項目が動画で絵 (アートワーク) がなければ、動画のフレームを絵として付ける。通知・ロック画面・Bluetoothの表示に使われる。
 * 動画は埋め込みの絵を持たないので、そのままだと、これらの表示が絵なしになる。
 * 付けるのは今の1件だけ (キュー全体の動画のフレームを先に取り出すと重い)。メタデータだけの入れ替えなので、再生は途切れない
 */
class VideoArtwork(
  private val context: Context,
  private val player: Player,
  private val scope: CoroutineScope,
) : Player.Listener {

  fun attach() {
    player.addListener(this)
    update()
  }

  override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = update()

  override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = update()

  private fun update() {
    val item = player.currentMediaItem ?: return
    val uri = item.localConfiguration?.uri ?: return
    if (item.localConfiguration?.mimeType?.startsWith("video/") != true) return
    if (item.mediaMetadata.artworkData != null || item.mediaMetadata.artworkUri != null) return
    val id = item.mediaId
    scope.launch {
      val bytes = withContext(Dispatchers.IO) { thumbnailBytes(context, uri) } ?: return@launch
      // 読んでいるあいだに別の項目へ移っていたら、何もしない
      val index = player.currentMediaItemIndex
      val current = player.currentMediaItem ?: return@launch
      if (current.mediaId != id || current.mediaMetadata.artworkData != null) return@launch
      val metadata = current.mediaMetadata.buildUpon()
        .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        .build()
      player.replaceMediaItem(index, current.buildUpon().setMediaMetadata(metadata).build())
    }
  }

  private companion object {
    /** 通知などの絵に十分な大きさ (長い辺) */
    const val SIZE = 512

    fun thumbnailBytes(context: Context, uri: Uri): ByteArray? {
      val bitmap = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.authority == MediaStore.AUTHORITY) {
          context.contentResolver.loadThumbnail(uri, Size(SIZE, SIZE), null)
        } else {
          frame(context, uri)
        }
      }.onFailure { Timber.d(it, "動画のサムネイルを読めませんでした") }.getOrNull() ?: return null
      return ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        out.toByteArray()
      }
    }

    /** loadThumbnailがない (Android 9以前) か、MediaStoreにない動画のために、1秒付近のフレームを取り出す */
    fun frame(context: Context, uri: Uri): Bitmap? {
      val retriever = MediaMetadataRetriever()
      try {
        retriever.setDataSource(context, uri)
        val frame = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
        val scale = SIZE.toFloat() / maxOf(frame.width, frame.height)
        if (scale >= 1f) return frame
        return frame.scale((frame.width * scale).toInt().coerceAtLeast(1), (frame.height * scale).toInt().coerceAtLeast(1))
      } finally {
        retriever.release()
      }
    }
  }
}
