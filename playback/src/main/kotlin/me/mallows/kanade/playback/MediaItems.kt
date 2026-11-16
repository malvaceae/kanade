package me.mallows.kanade.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import me.mallows.kanade.core.data.db.TrackEntity

/** 曲をプレイヤーに渡す形に変換する。mediaIdにはMediaStoreのIDを使う */
fun TrackEntity.toMediaItem(): MediaItem =
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
        .build()
    )
    .build()

/** IDだけを持つ要求用の項目。再生サービス側で[toMediaItem]に置き換える */
fun mediaItemRequest(mediaStoreId: Long): MediaItem =
  MediaItem.Builder().setMediaId(mediaStoreId.toString()).build()
