package me.mallows.kanade.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * この領域への操作 (タップ・ドラッグ) を受けて、背後の画面に届かせない。
 * 色を塗った領域の下に、別の画面の操作 (一覧のタップやスクロールなど) が残らないようにする。
 * 中のボタンなどは、先に操作を受けるので、そのまま押せる。色の付いた余白 (ナビゲーションバーの分など) を含めるため、
 * 余白の指定 (`navigationBarsPadding`) や背景より前に付ける
 */
fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
  awaitPointerEventScope {
    while (true) awaitPointerEvent().changes.forEach { it.consume() }
  }
}
