package me.mallows.kanade.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * この領域への操作 (タップ・ドラッグ) を、背後の画面に届かせない。
 * 色を塗った領域の下に、別の画面の操作 (一覧のタップやスクロールなど) が残らないようにする。
 * 操作そのものは消費しない。消費すると、中のボタンが、押している間に動いた指を「他に取られた」と見て、タップを取り消してしまう。
 * 中を触れるようにしたまま背後を塞ぐのは、操作を受ける入力があると、その下の兄弟には操作が渡らない、というComposeの決まり (sharePointerInputWithSiblings) による。
 * 色の付いた余白 (ナビゲーションバーの分など) を含めるため、余白の指定 (`navigationBarsPadding`) や背景より前に付ける
 */
fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {}
