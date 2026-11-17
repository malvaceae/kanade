package me.mallows.kanade.ui.equalizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** 回す範囲 (真上を0度として左右に135度ずつ) */
private const val SWEEP = 270f
private const val START = 135f // Canvasの角度 (右が0度、時計回り) で左下

/**
 * dBを設定するノブ。左右のドラッグで回し (右で増える)、ダブルタップで0dBに戻す。
 * 縦の動きは画面のスクロールに譲るので、スクロール中に指が乗っても値は変わらない。
 * 0dBから今の値までを弧で示し、中央に値を出す。値は0.5dB刻み。
 */
@Composable
fun DbKnob(
  label: String,
  sub: String,
  value: Float,
  min: Float,
  max: Float,
  onChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  val haptic = LocalHapticFeedback.current
  val current by rememberUpdatedState(value)
  val change by rememberUpdatedState(onChange)
  fun snap(v: Float) = ((v * 2).roundToInt() / 2f).coerceIn(min, max)
  Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
      Modifier
        .size(84.dp)
        .semantics {
          contentDescription = label
          stateDescription = formatDb(value)
          progressBarRangeInfo = ProgressBarRangeInfo(value, min..max, steps = ((max - min) * 2).toInt() - 1)
          setProgress { v -> change(snap(v)); true }
        }
        .pointerInput(min, max) {
          detectTapGestures(onDoubleTap = {
            if (current != 0f) {
              change(0f)
              haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            }
          })
        }
        .pointerInput(min, max) {
          // 指200dpで端から端まで回る
          val perPx = (max - min) / 200.dp.toPx()
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var raw = current
            var last = current
            var dragging = false
            while (true) {
              val event = awaitPointerEvent()
              val c = event.changes.firstOrNull { it.id == down.id } ?: break
              if (!c.pressed) break
              if (!dragging) {
                val total = c.position - down.position
                if (total.getDistance() <= viewConfiguration.touchSlop) continue
                // 縦の動きは画面のスクロールに譲る
                if (abs(total.y) > abs(total.x)) break
                dragging = true
              }
              if (dragging) {
                val dx = c.position.x - c.previousPosition.x
                raw = (raw + dx * perPx).coerceIn(min, max)
                val v = snap(raw)
                if (v != last) {
                  // 0dBを通るときだけ手応えを返す
                  if ((last < 0f) != (v < 0f) || v == 0f) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                  last = v
                  change(v)
                }
                c.consume()
              }
            }
          }
        },
      contentAlignment = Alignment.Center,
    ) {
      Canvas(Modifier.size(84.dp)) {
        val stroke = 6.dp.toPx()
        val r = size.minDimension / 2 - stroke / 2
        val topLeft = Offset(center.x - r, center.y - r)
        val arcSize = Size(r * 2, r * 2)
        fun angle(v: Float) = START + (v - min) / (max - min) * SWEEP
        drawArc(scheme.surfaceContainerHighest, START, SWEEP, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        val zero = angle(0f)
        val now = angle(value)
        if (abs(now - zero) > 0.5f) {
          drawArc(scheme.primary, minOf(zero, now), abs(now - zero), false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        val inner = r - stroke * 1.6f
        drawCircle(scheme.surfaceContainerHigh, inner)
        // つまみの向き
        val rad = Math.toRadians(now.toDouble())
        // 中央の数字に重ならないよう、縁の近くに短く引く
        val p1 = Offset(center.x + (inner * 0.8f) * cos(rad).toFloat(), center.y + (inner * 0.8f) * sin(rad).toFloat())
        val p2 = Offset(center.x + (inner * 0.95f) * cos(rad).toFloat(), center.y + (inner * 0.95f) * sin(rad).toFloat())
        drawLine(scheme.primary, p1, p2, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
      }
      Text(formatValue(value), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
    }
    Text(label, style = MaterialTheme.typography.bodyMedium)
    Text(sub, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
  }
}

/** ノブの中央に出す値 (単位なし) */
private fun formatValue(db: Float): String = when {
  db == 0f -> "0"
  else -> "%+.1f".format(db)
}
