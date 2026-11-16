package me.mallows.kanade.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 長い一覧の右端に出す、ドラッグできるつまみ。スクロール中だけ現れ、つかむと一気に移動できる。
 * ドラッグ中は [label] (一覧の項目の番号 → 頭文字など) を吹き出しで出す。
 * 項目が [minItems] 未満なら何も出さない。[modifier] には親の中での位置 (右端・高さいっぱい) を渡す。
 */
@Composable
fun FastScroller(
  state: LazyListState,
  label: (index: Int) -> String?,
  modifier: Modifier = Modifier,
  minItems: Int = 40,
  bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
  val total = state.layoutInfo.totalItemsCount
  if (total < minItems) return
  val scope = rememberCoroutineScope()
  var dragging by remember { mutableStateOf(false) }
  var dragFraction by remember { mutableFloatStateOf(0f) }
  var visible by remember { mutableStateOf(false) }
  val scrolling = state.isScrollInProgress
  LaunchedEffect(scrolling, dragging) {
    if (scrolling || dragging) visible = true else { delay(1_500); visible = false }
  }
  // 今の位置 (0 ~ 1)。ドラッグ中は指の位置をそのまま使う
  val fraction by remember {
    derivedStateOf {
      val info = state.layoutInfo
      val visibleCount = info.visibleItemsInfo.size.coerceAtLeast(1)
      val max = (info.totalItemsCount - visibleCount).coerceAtLeast(1)
      (state.firstVisibleItemIndex.toFloat() / max).coerceIn(0f, 1f)
    }
  }
  val position = if (dragging) dragFraction else fraction
  val density = LocalDensity.current
  val thumbHeight = 48.dp

  AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
    BoxWithConstraints(Modifier.fillMaxHeight().padding(bottom = bottomPadding).width(72.dp)) {
      val track = with(density) { (maxHeight - thumbHeight).toPx() }.coerceAtLeast(1f)
      val y = (position * track).roundToInt()
      Box(
        Modifier
          .fillMaxHeight()
          .width(32.dp)
          .align(Alignment.TopEnd)
          .pointerInput(total) {
            fun jump(offsetY: Float) {
              dragFraction = ((offsetY - thumbHeight.toPx() / 2) / track).coerceIn(0f, 1f)
              val target = (dragFraction * (total - 1)).roundToInt()
              scope.launch { state.scrollToItem(target) }
            }
            detectVerticalDragGestures(
              onDragStart = { dragging = true; jump(it.y) },
              onDragEnd = { dragging = false },
              onDragCancel = { dragging = false },
              onVerticalDrag = { change, _ -> change.consume(); jump(change.position.y) },
            )
          },
      )
      Row(
        Modifier.align(Alignment.TopEnd).offset { IntOffset(0, y) },
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (dragging) {
          label(state.firstVisibleItemIndex)?.let { text ->
            Box(
              Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center,
            ) {
              Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
          }
        }
        Box(
          Modifier
            .padding(start = 8.dp, end = 4.dp)
            .width(if (dragging) 8.dp else 6.dp)
            .height(thumbHeight)
            .clip(RoundedCornerShape(4.dp))
            .background(if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)),
        )
      }
    }
  }
}

/** 頭文字 (英字は大文字、数字や記号は「#」) */
fun initialOf(text: String?): String? {
  val c = text?.trim()?.firstOrNull() ?: return null
  return when {
    c.isLetter() -> c.uppercaseChar().toString()
    else -> "#"
  }
}
