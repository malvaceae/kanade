package me.mallows.kanade.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class SettledDurationTest {
  @Test fun 近ければプレイヤーの長さを使う() = assertEquals(210_700L, settledDuration(210_700, 210_000))

  @Test fun 大きく外れていたらライブラリの長さを使う() = assertEquals(21_000L, settledDuration(1_857, 21_000))

  @Test fun プレイヤーが長さを知らなければライブラリの長さを使う() = assertEquals(21_000L, settledDuration(null, 21_000))

  @Test fun ライブラリの長さがなければプレイヤーの長さを使う() = assertEquals(5_000L, settledDuration(5_000, null))
}
