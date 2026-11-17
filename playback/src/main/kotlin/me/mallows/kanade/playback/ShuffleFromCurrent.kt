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
 * 今の曲を先頭にしたシャッフルの順にする。キューへの追加では作り直さない (今のランダムな順を保つ)
 */
@OptIn(UnstableApi::class)
fun ExoPlayer.keepShuffleFromCurrent() {
  addListener(object : Player.Listener {
    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
      if (shuffleModeEnabled) shuffleFromCurrent()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
      if (shuffleModeEnabled && reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) shuffleFromCurrent()
    }
  })
}
