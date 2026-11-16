package me.mallows.kanade.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 再生元の一覧の曲の集まり。一覧の「再生中」表示は、この一覧と同じときだけ出す */
val LocalPlayingContext = compositionLocalOf { me.mallows.kanade.playback.PlayingContext() }

/** いま音が出ているか (一時停止中はfalse)。一覧の「再生中の印」を動かすかどうかに使う */
val LocalIsPlaying = compositionLocalOf { false }

/** 再生中の印。音が出ている間は3本の棒が上下し、一時停止中は止まる */
@Composable
fun PlayingBars(color: Color, animating: Boolean, modifier: Modifier = Modifier, size: Dp = 18.dp) {
  val heights: List<State<Float>>? = if (animating) {
    val transition = rememberInfiniteTransition(label = "bars")
    listOf(520, 380, 640).map { ms ->
      transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(ms, easing = LinearEasing), RepeatMode.Reverse),
        label = "bar",
      )
    }
  } else {
    null
  }
  val still = listOf(0.55f, 1f, 0.75f)
  Canvas(modifier.size(size)) {
    val gap = this.size.width / 7f
    val barWidth = gap * 1.6f
    for (i in 0 until 3) {
      val h = (heights?.get(i)?.value ?: still[i]) * this.size.height * 0.85f
      val x = gap + i * (barWidth + gap * 0.7f)
      drawRoundRect(
        color = color,
        topLeft = Offset(x, this.size.height - h),
        size = Size(barWidth, h),
        cornerRadius = CornerRadius(barWidth / 2f),
      )
    }
  }
}
