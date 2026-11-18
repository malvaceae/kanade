package me.mallows.kanade.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import me.mallows.kanade.core.data.db.TrackEntity

/** 「次に再生」「キューに追加」で入れた曲の印 */
const val EXTRA_QUEUED = "me.mallows.kanade.QUEUED"

/** 曲をプレイヤーに渡す形に変換する。mediaIdにはMediaStoreのIDを使う */
fun TrackEntity.toMediaItem(queued: Boolean = false): MediaItem =
  MediaItem.Builder()
    .setMediaId(mediaStoreId.toString())
    .setUri(uri)
    .setMimeType(mime)
    .setMediaMetadata(
      MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setAlbumArtist(albumArtist)
        .setTrackNumber(trackNo)
        .setDiscNumber(discNo)
        .setDurationMs(durationMs)
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .apply { if (queued) setExtras(Bundle().apply { putBoolean(EXTRA_QUEUED, true) }) }
        .build()
    )
    .build()

/** IDだけを持つ要求用の項目。再生サービス側で[toMediaItem]に置き換える */
fun mediaItemRequest(mediaStoreId: Long, queued: Boolean = false): MediaItem =
  MediaItem.Builder()
    .setMediaId(mediaStoreId.toString())
    .apply {
      if (queued) {
        setRequestMetadata(
          MediaItem.RequestMetadata.Builder().setExtras(Bundle().apply { putBoolean(EXTRA_QUEUED, true) }).build()
        )
      }
    }
    .build()

/** 要求に「キューに入れた」印があるか */
val MediaItem.isQueuedRequest: Boolean get() = requestMetadata.extras?.getBoolean(EXTRA_QUEUED) == true

/** プレイヤー上の項目が「キューに入れた」曲か */
val MediaItem.isQueued: Boolean get() = mediaMetadata.extras?.getBoolean(EXTRA_QUEUED) == true
