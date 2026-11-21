package me.mallows.kanade

import me.mallows.kanade.playback.PlaySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceDestinationTest {
  @Test fun すべての曲() = assertEquals(Category("TRACKS"), sourceDestination(PlaySource("tracks", "すべての曲")))

  @Test fun プレイリスト() = assertEquals(PlaylistDetail(7), sourceDestination(PlaySource("playlist:7", "お気に入り")))

  @Test fun アルバム() = assertEquals(GroupDetail("ALBUM", "Oh,Oh!", "Oh,Oh!"), sourceDestination(PlaySource("group:ALBUM:Oh,Oh!", "Oh,Oh!")))

  @Test fun キーに区切りの文字が入っていても壊れない() =
    assertEquals(GroupDetail("FOLDER", "Music/a:b", "a:b"), sourceDestination(PlaySource("group:FOLDER:Music/a:b", "a:b")))

  @Test fun アーティストはアルバムの一覧を開く() =
    assertEquals(ArtistDetail("Galileo Galilei", "Galileo Galilei"), sourceDestination(PlaySource("group:ARTIST:Galileo Galilei", "Galileo Galilei")))

  @Test fun キーが無い一覧() = assertEquals(GroupDetail("RECENTLY_ADDED", null, "最近追加した曲"), sourceDestination(PlaySource("group:RECENTLY_ADDED:null", "最近追加した曲")))

  @Test fun 一覧を開けない再生元はnull() {
    assertNull(sourceDestination(PlaySource("selection:1,2", "選択")))
    assertNull(sourceDestination(PlaySource("search:abc", "検索")))
    assertNull(sourceDestination(null))
  }

  @Test fun 壊れたキーはnull() {
    assertNull(sourceDestination(PlaySource("group:NOPE:x", "t")))
    assertNull(sourceDestination(PlaySource("group:ALBUM", "t")))
    assertNull(sourceDestination(PlaySource("playlist:abc", "t")))
  }
}
