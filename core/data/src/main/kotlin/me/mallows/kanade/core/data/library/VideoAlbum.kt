package me.mallows.kanade.core.data.library

/**
 * 動画のアルバム名。タグのない動画は、MediaStoreがアルバム名に置き場所のフォルダ名 ([folderName]) を入れる。
 * それはアルバムではないので、フォルダ名と同じなら「アルバムなし」にする (アルバムを推測しない)。
 * [album]は、「不明」の値を除いたもの。
 */
internal fun videoAlbum(album: String?, folderName: String?): String? {
  if (album.isNullOrBlank()) return null
  return album.takeUnless { it == folderName }
}
