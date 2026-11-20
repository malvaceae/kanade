package me.mallows.kanade.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 画面とアクティビティの間で、PiP (小窓で映像を出し続ける) の状態をやりとりする。
 * 動画を映している再生画面が出ていて、再生中のあいだだけ、ホームへ戻るときにPiPへ移る
 */
class PictureInPictureHost {
  /** PiPの小窓で表示中か */
  var active by mutableStateOf(false)

  /** PiPへ移ってよいか (映像を出している再生画面があり、再生中) */
  var eligible = false

  /** 映像の縦横比 (横 / 縦)。小窓の形に使う */
  var aspect = 16f / 9f
}

val LocalPictureInPicture = staticCompositionLocalOf { PictureInPictureHost() }
