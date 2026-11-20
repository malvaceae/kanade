package me.mallows.kanade.core.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoAlbumTest {

  @Test
  fun アルバム名がフォルダ名と同じなら_アルバムなし() {
    assertNull(videoAlbum("Movies", "Movies"))
  }

  @Test
  fun タグのアルバム名は_フォルダ名と違えばそのまま使う() {
    assertEquals("Live 2024", videoAlbum("Live 2024", "Movies"))
  }

  @Test
  fun フォルダ名が分からなければ_アルバム名をそのまま使う() {
    assertEquals("Live 2024", videoAlbum("Live 2024", null))
  }

  @Test
  fun アルバム名が空なら_アルバムなし() {
    assertNull(videoAlbum(null, "Movies"))
    assertNull(videoAlbum("  ", "Movies"))
  }

  @Test
  fun 大文字小文字が違えば_別のアルバムとして扱う() {
    // フォルダ名の自動入力は同じ文字列なので、違う綴りはタグの値とみなす
    assertEquals("movies", videoAlbum("movies", "Movies"))
  }
}
