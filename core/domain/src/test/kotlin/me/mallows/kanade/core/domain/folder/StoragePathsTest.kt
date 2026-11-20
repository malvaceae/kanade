package me.mallows.kanade.core.domain.folder

import me.mallows.kanade.core.domain.folder.StoragePaths.Located
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoragePathsTest {

  private val internal = "/storage/emulated/0"
  private val sd = "/storage/1234-ABCD"

  @Test
  fun 外部ファイル用フォルダからストレージの根を取り出す() {
    assertEquals(internal, StoragePaths.rootOfFilesDir("/storage/emulated/0/Android/data/me.mallows.kanade/files"))
    assertEquals(sd, StoragePaths.rootOfFilesDir("/storage/1234-ABCD/Android/data/me.mallows.kanade/files"))
    assertNull(StoragePaths.rootOfFilesDir("/data/user/0/me.mallows.kanade/files"))
  }

  @Test
  fun ボリューム名は主ストレージがexternal_primaryでほかは根の最後の小文字() {
    assertEquals("external_primary", StoragePaths.volumeName(internal, primary = true))
    assertEquals("1234-abcd", StoragePaths.volumeName(sd, primary = false))
    assertEquals("1234-abcd", StoragePaths.volumeName("$sd/", primary = false))
  }

  @Test
  fun 絶対パスから根と相対パスを作る() {
    val roots = listOf(internal, sd)
    assertEquals(Located(internal, "Music/Artist/"), StoragePaths.relativePathOf(roots, "$internal/Music/Artist/曲.flac"))
    assertEquals(Located(sd, "Music/"), StoragePaths.relativePathOf(roots, "$sd/Music/曲.flac"))
  }

  @Test
  fun 根の直下の曲の相対パスは空() {
    assertEquals(Located(internal, ""), StoragePaths.relativePathOf(listOf(internal), "$internal/曲.mp3"))
  }

  @Test
  fun 根の大小文字は区別せず末尾の斜線があっても同じ() {
    assertEquals(Located("/storage/1234-ABCD", "Music/"), StoragePaths.relativePathOf(listOf("/storage/1234-ABCD/"), "/storage/1234-abcd/Music/曲.mp3"))
  }

  @Test
  fun どの根にも当たらないパスはnull() {
    assertNull(StoragePaths.relativePathOf(listOf(internal), "/storage/other/Music/曲.mp3"))
    // 根の名前の続きでしかないパスは当たらない
    assertNull(StoragePaths.relativePathOf(listOf("/storage/emulated/0"), "/storage/emulated/01/Music/曲.mp3"))
  }

  @Test
  fun 根が入れ子のときは長い方を使う() {
    assertEquals(Located("/storage/emulated/0/Nested", "Music/"), StoragePaths.relativePathOf(listOf(internal, "/storage/emulated/0/Nested"), "/storage/emulated/0/Nested/Music/曲.mp3"))
  }

  @Test
  fun LIKEの値はパーセントと下線と円記号を文字どおりに扱い末尾の斜線で止める() {
    assertEquals("/storage/emulated/0/Music/%", StoragePaths.likePrefix(internal, "Music"))
    assertEquals("/storage/emulated/0/100\\%\\_test/%", StoragePaths.likePrefix(internal, "100%_test/"))
    assertEquals("/storage/emulated/0/%", StoragePaths.likePrefix(internal, ""))
  }
}
