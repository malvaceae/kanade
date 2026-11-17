package me.mallows.kanade.core.domain.folder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanFoldersTest {

  private val music = ScanFolder(ScanFolder.PRIMARY, "Music/")

  @Test
  fun LIKEの特別な文字を逃がす() {
    assertEquals("""Music/100\%\_test/""", escapeLike("Music/100%_test/"))
    assertEquals("""a\\b""", escapeLike("""a\b"""))
    assertEquals("""Music/100\%\_test/%""", likePrefix("Music/100%_test"))
  }

  @Test
  fun 末尾の区切りで似た名前のフォルダーを取り違えない() {
    assertTrue(music.contains(ScanFolder.PRIMARY, "Music/"))
    assertTrue(music.contains(ScanFolder.PRIMARY, "Music/Artist/Album/"))
    assertFalse(music.contains(ScanFolder.PRIMARY, "MusicBackup/"))
    assertFalse(music.contains(ScanFolder.PRIMARY, "Download/Music/"))
    assertEquals("Music/%", likePrefix("Music"))
  }

  @Test
  fun 英字の大小とストレージを見て含まれるかを決める() {
    assertTrue(music.contains("EXTERNAL_PRIMARY", "music/a/"))
    assertFalse(music.contains("1234-abcd", "Music/"))
  }

  @Test
  fun 親に含まれる子と重なりを除く() {
    val sd = ScanFolder("1234-abcd", "Music/")
    val result = normalize(
      listOf(
        ScanFolder(ScanFolder.PRIMARY, "Music/Artist/"),
        music,
        ScanFolder(ScanFolder.PRIMARY, "music/"),
        sd,
        ScanFolder("1234-abcd", "Music/Live/"),
        ScanFolder(ScanFolder.PRIMARY, "MusicBackup/"),
      ),
    )
    assertEquals(listOf(sd, music, ScanFolder(ScanFolder.PRIMARY, "MusicBackup/")), result)
  }

  @Test
  fun 保存用の文字列と行き来できる() {
    val f = ScanFolder("1234-abcd", "Music/100%_test/")
    assertEquals(f, ScanFolder.decode(f.encode()))
    assertNull(ScanFolder.decode("壊れた値"))
    assertEquals("Music/", ScanFolder.dir("/Music"))
    assertEquals("", ScanFolder.dir("/"))
  }

  @Test
  fun 根の場所を決め打ちせずに絶対パスからフォルダーを求める() {
    val roots = listOfNotNull(
      StorageRoot.fromAppDir("/storage/emulated/0/Android/data/me.mallows.kanade/files", primary = true),
      StorageRoot.fromAppDir("/storage/1234-ABCD/Android/data/me.mallows.kanade/files", primary = false),
      StorageRoot.fromAppDir("/mnt/media_rw/ABCD-0000/Android/data/me.mallows.kanade/files", primary = false),
    )
    assertEquals(ScanFolder.PRIMARY to "Music/Artist/", locate("/storage/emulated/0/Music/Artist/a.flac", roots))
    assertEquals("1234-abcd" to "Music/", locate("/storage/1234-ABCD/Music/b.mp3", roots))
    assertEquals("abcd-0000" to "", locate("/mnt/media_rw/ABCD-0000/c.mp3", roots))
    // 根の名前の途中で切れる別の場所は当てない
    assertNull(locate("/storage/emulated/01/Music/a.flac", roots))
    assertNull(StorageRoot.fromAppDir("/data/user/0/me.mallows.kanade/files", primary = true))
  }

  @Test
  fun 対象外と消えた曲を分ける() {
    val plan = classify(stored = setOf(1, 2, 3, 4), included = setOf(1, 5), existing = setOf(1, 2, 3, 5))
    assertEquals(setOf(2L, 3L), plan.excluded)
    assertEquals(setOf(4L), plan.deleted)
    // 選び直すと対象に戻る (対象外にも消すにも入らない)
    val again = classify(stored = setOf(1, 2, 3), included = setOf(1, 2, 3), existing = setOf(1, 2, 3))
    assertEquals(emptySet<Long>(), again.excluded)
    assertEquals(emptySet<Long>(), again.deleted)
  }

  @Test
  fun 標準のフォルダー選択の文書IDからフォルダーを作る() {
    val ext = ScanFolder.EXTERNAL_STORAGE_AUTHORITY
    assertEquals(ScanFolder(ScanFolder.PRIMARY, "Music/Artist/"), ScanFolder.fromTree(ext, "primary:Music/Artist"))
    assertEquals(ScanFolder("78e7-171e", "Music/"), ScanFolder.fromTree(ext, "78E7-171E:Music"))
    assertEquals(ScanFolder(ScanFolder.PRIMARY, ""), ScanFolder.fromTree(ext, "primary:"))
    // 外部ストレージ以外の提供元 (「ダウンロード」の一覧など) は扱わない
    assertNull(ScanFolder.fromTree("com.android.providers.downloads.documents", "msf:123"))
    assertNull(ScanFolder.fromTree(ext, "downloads"))
  }
}
