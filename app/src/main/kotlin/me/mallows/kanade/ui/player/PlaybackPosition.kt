package me.mallows.kanade.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import me.mallows.kanade.playback.PlayerState

/**
 * ある項目 (引数は[PlayerState.mediaId]) の、今の再生位置を読む関数。画面全体で1つの再生を表すので、上の画面から渡す。
 * 曲が切り替わる途中などで、その項目の位置が読めないときはnull
 */
val LocalPlaybackPosition = compositionLocalOf<(String?) -> Long?> { { null } }

/**
 * なめらかに進む再生位置。再生中は描画のたびに、サービスの実際のプレイヤーの位置を読む。
 * 項目が切り替わる途中で、読めない (プレイヤーの項目が、画面の状態の項目と違う) ときは、状態と同時に届いた位置を使う
 */
@Composable
fun rememberPlaybackPosition(state: PlayerState): State<Long> {
  val read = LocalPlaybackPosition.current
  val latest = rememberUpdatedState(state)
  // 再生中は、描画のたびに進めて、位置を読み直す合図にする
  val frame = remember { mutableLongStateOf(0L) }
  LaunchedEffect(state.isPlaying) {
    if (state.isPlaying) while (true) withFrameMillis { frame.longValue = it }
  }
  return remember {
    derivedStateOf {
      frame.longValue
      val s = latest.value
      read(s.mediaId) ?: s.positionMs
    }
  }
}
