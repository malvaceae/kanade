package me.mallows.kanade.playback

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import kotlin.random.Random

/**
 * シャッフルの順を、今の曲を先頭にして作り直す。
 * Media3は、シャッフルをオンにしても今の曲を先頭にせず、元から決まっているランダムな順をそのまま使う。
 * 今の曲が順の途中にあると、それより前に来る曲はリピートなしでは流れないため、作り直す。
 */
@OptIn(UnstableApi::class)
fun ExoPlayer.shuffleFromCurrent() {
  val count = mediaItemCount
  if (count <= 1) return
  val current = currentMediaItemIndex
  val rest = (0 until count).filter { it != current }.shuffled()
  setShuffleOrder(ShuffleOrder.DefaultShuffleOrder((listOf(current) + rest).toIntArray(), Random.nextLong()))
}

/**
 * シャッフルをオンにしたときと、シャッフル中に曲の並びを入れ替えたとき (一覧から再生を始めたときなど) に、
 * 今の曲を先頭にしたシャッフルの順にする。キューへの追加では作り直さない (今のランダムな順を保つ)。
 * 全曲リピート中は、順の最後の曲が始まったときに、その曲を先頭にして順を作り直す。
 * 次の周の順は、最初の曲から最後の曲までランダムになる (一周して戻ってから作り直すと、戻った先の曲が毎周同じになるため)
 */
@OptIn(UnstableApi::class)
fun ExoPlayer.keepShuffleFromCurrent() {
  addListener(object : Player.Listener {
    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
      if (shuffleModeEnabled) shuffleFromCurrent()
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
      reshuffleAtLast()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
      if (!shuffleModeEnabled) return
      if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) shuffleFromCurrent() else reshuffleAtLast()
    }

    /** 全曲リピートのシャッフル中に、順の最後の曲になったら、その曲を先頭にして次の周の順を作り直す */
    fun reshuffleAtLast() {
      if (!shuffleModeEnabled || repeatMode != Player.REPEAT_MODE_ALL || mediaItemCount <= 2) return
      val timeline = currentTimeline
      if (!timeline.isEmpty && currentMediaItemIndex == timeline.getLastWindowIndex(true)) shuffleFromCurrent()
    }
  })
}
