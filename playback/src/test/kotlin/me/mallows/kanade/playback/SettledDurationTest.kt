package me.mallows.kanade.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class SettledDurationTest {
  @Test fun ライブラリの長さがあればそれを使う() = assertEquals(21_000L, settledDuration(1_857, 21_000))

  @Test fun ライブラリの長さがなければプレイヤーの長さを使う() = assertEquals(5_000L, settledDuration(5_000, null))

  @Test fun ライブラリの長さが0ならプレイヤーの長さを使う() = assertEquals(5_000L, settledDuration(5_000, 0))

  @Test fun どちらもなければ0() = assertEquals(0L, settledDuration(null, null))
}
