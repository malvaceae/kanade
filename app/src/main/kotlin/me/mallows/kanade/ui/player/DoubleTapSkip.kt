package me.mallows.kanade.ui.player

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.mallows.kanade.R
import me.mallows.kanade.playback.PlayerState

/** ダブルタップで戻る・進む長さ */
internal const val DOUBLE_TAP_SKIP_MS = 10_000L

/** 続けて叩いたときに、前の目標から数える時間 (位置が目標に届くまでの間、古い位置から数えないため) */
private const val SKIP_SETTLE_MS = 1_000L

/** 「-10秒」「+10秒」の表示を消すまでの時間 */
private const val SKIP_LABEL_MS = 600L

/** ダブルタップで起こすこと */
internal sealed interface Skip {
  data class SeekTo(val positionMs: Long) : Skip

  /** 進んだ先が曲の終わりを越えるので、次の曲へ移る */
  data object Next : Skip
}

/**
 * [positionMs]から[direction] (-1 = 戻る、1 = 進む) へ10秒ずらす。
 * 戻るときは、曲の頭より前へ行かない。進むときは、曲の終わりを越えるなら次の曲へ移る (次の曲がなければ、曲の終わりへ)。
 * 長さが分からない間は、そのままずらす
 */
internal fun skipAction(positionMs: Long, durationMs: Long, direction: Int, hasNext: Boolean): Skip {
  val target = (positionMs + direction * DOUBLE_TAP_SKIP_MS).coerceAtLeast(0L)
  return when {
    durationMs <= 0L -> Skip.SeekTo(target)
    direction > 0 && target >= durationMs -> if (hasNext) Skip.Next else Skip.SeekTo(durationMs)
    else -> Skip.SeekTo(target)
  }
}

/**
 * ダブルタップで移る起点の位置。直前のダブルタップから[SKIP_SETTLE_MS]以内なら、その目標 ([lastTarget]) から数える
 * (プレイヤーの位置が目標に届く前に、続けて叩いても、前の移動を取り消さないため)。
 * 直前のダブルタップがなければ ([lastTarget]がnull)、今の再生位置から数える
 */
internal fun skipBase(positionMs: Long, lastTarget: Long?, lastAt: Long, now: Long): Long =
  if (lastTarget != null && now - lastAt < SKIP_SETTLE_MS) lastTarget else positionMs

/** 直前のダブルタップの目標と、その時刻 */
private class SkipMemo(var target: Long? = null, var at: Long = 0L)

/** ダブルタップの結果 ([skipAction]) を、シークや次の曲への移動にする。受け取るのは方向 (-1 = 戻る、1 = 進む) */
@Composable
internal fun rememberDoubleTapSkip(state: PlayerState, onSeek: (Long) -> Unit, onNext: () -> Unit): (Int) -> Unit {
  val position = rememberPlaybackPosition(state)
  val latestState by rememberUpdatedState(state)
  val latestSeek by rememberUpdatedState(onSeek)
  val latestNext by rememberUpdatedState(onNext)
  val memo = remember { SkipMemo() }
  return remember {
    { direction ->
      val now = SystemClock.elapsedRealtime()
      val base = skipBase(position.value, memo.target, memo.at, now)
      val s = latestState
      when (val action = skipAction(base, s.durationMs, direction, s.hasNext)) {
        is Skip.SeekTo -> {
          memo.target = action.positionMs
          memo.at = now
          latestSeek(action.positionMs)
        }
        Skip.Next -> {
          memo.target = null
          latestNext()
        }
      }
    }
  }
}

/**
 * 全体の左半分を2回タップすると戻り、右半分だと進む。そのとき、タップした側に「-10秒」「+10秒」を少しのあいだ出す。
 * [onTap]を渡せば、1回のタップも受ける (2回目のタップを待つので、そのぶん少し遅れる)
 */
@Composable
internal fun DoubleTapSkipArea(
  onSkip: (direction: Int) -> Unit,
  modifier: Modifier = Modifier,
  onTap: (() -> Unit)? = null,
  content: @Composable BoxScope.() -> Unit,
) {
  val currentOnSkip by rememberUpdatedState(onSkip)
  val currentOnTap by rememberUpdatedState(onTap)
  var label by remember { mutableStateOf<Int?>(null) }
  var hits by remember { mutableIntStateOf(0) }
  LaunchedEffect(hits) {
    if (hits > 0) {
      delay(SKIP_LABEL_MS)
      label = null
    }
  }
  Box(
    modifier
      .pointerInput(Unit) {
        detectTapGestures(
          onTap = { currentOnTap?.invoke() },
          onDoubleTap = { offset ->
            val direction = if (offset.x < size.width / 2f) -1 else 1
            currentOnSkip(direction)
            label = direction
            hits++
          },
        )
      }
      .then(if (onTap != null) Modifier.semantics { onClick { currentOnTap?.invoke(); true } } else Modifier),
  ) {
    content()
    AnimatedVisibility(
      visible = label == -1,
      enter = fadeIn(tween(80)),
      exit = fadeOut(tween(300)),
      modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp),
    ) { SkipLabel(stringResource(R.string.skip_back_label)) }
    AnimatedVisibility(
      visible = label == 1,
      enter = fadeIn(tween(80)),
      exit = fadeOut(tween(300)),
      modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp),
    ) { SkipLabel(stringResource(R.string.skip_forward_label)) }
  }
}

@Composable
private fun SkipLabel(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
    color = Color.White,
    modifier = Modifier
      .background(Color.Black.copy(alpha = 0.55f), CircleShape)
      .padding(horizontal = 16.dp, vertical = 10.dp),
  )
}
