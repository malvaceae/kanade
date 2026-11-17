package me.mallows.kanade.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * 再生の順番が来たスキャン対象外の曲を飛ばす。
 * 対象外の曲は再生キューから外さずに残す (フォルダを選び直せば、そのまま元どおり再生できるように)。
 * 進んだ向き (次へ・前へ) に、再生できる曲まで飛ばす。その向きに再生できる曲がなければ元の曲に戻し、
 * 曲の終わりで進んだときだけ止める。
 * 再生中の曲が対象外になったときは、止めずに最後まで流す。
 */
class ExcludedSkipper(
  private val player: Player,
  excludedIds: Flow<Set<Long>>,
  scope: CoroutineScope,
) : Player.Listener {
  private var excluded: Set<Long> = emptySet()
  /** 前の曲の位置。進んだ向きを知るのに使う */
  private var lastIndex = C.INDEX_UNSET

  init {
    scope.launch { excludedIds.collect { excluded = it } }
    player.addListener(this)
  }

  private fun isExcluded(index: Int): Boolean =
    player.getMediaItemAt(index).mediaId.toLongOrNull()?.let { it in excluded } == true

  override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
    val now = player.currentMediaItemIndex
    val from = lastIndex
    lastIndex = now
    if (now == C.INDEX_UNSET || player.mediaItemCount == 0 || !isExcluded(now)) return

    val timeline = player.currentTimeline
    val shuffle = player.shuffleModeEnabled
    // 1曲リピートは、曲を移るときはリピートなしと同じに扱う (Media3の「次へ」「前へ」と同じ)
    val repeat = if (player.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else player.repeatMode
    val backward = from != C.INDEX_UNSET && from < player.mediaItemCount &&
      timeline.getPreviousWindowIndex(from, repeat, shuffle) == now
    var i = now
    // 全曲リピートでは一周すると今の曲に戻るので、そこで探すのをやめる
    for (step in 0 until player.mediaItemCount) {
      i = if (backward) timeline.getPreviousWindowIndex(i, repeat, shuffle) else timeline.getNextWindowIndex(i, repeat, shuffle)
      if (i == C.INDEX_UNSET || i == now) break
      if (!isExcluded(i)) {
        lastIndex = i
        player.seekTo(i, 0L)
        return
      }
    }
    // その向きに再生できる曲がない。元の曲に戻し、曲の終わりで進んだのなら止める (次へ・前へを押したのなら再生を続ける)
    if (from != C.INDEX_UNSET && from < player.mediaItemCount && from != now && !isExcluded(from)) {
      lastIndex = from
      player.seekTo(from, 0L)
    }
    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || isExcluded(player.currentMediaItemIndex)) player.pause()
  }
}
