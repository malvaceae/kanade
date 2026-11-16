package me.mallows.kanade.ui.common

import android.content.ContentUris
import android.provider.MediaStore

/** MediaStoreのIDから曲のURIを作る (アートワークの読み込み用) */
fun audioUri(mediaStoreId: Long): String =
  ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaStoreId).toString()
