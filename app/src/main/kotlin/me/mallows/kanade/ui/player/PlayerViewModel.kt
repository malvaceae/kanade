package me.mallows.kanade.ui.player

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import me.mallows.kanade.core.data.db.TrackEntity
import me.mallows.kanade.playback.PlayerConnection
import me.mallows.kanade.playback.PlayerState

/** 再生操作。画面全体で1つを共有する */
@HiltViewModel
class PlayerViewModel @Inject constructor(
  private val connection: PlayerConnection,
) : ViewModel() {

  val state: StateFlow<PlayerState> = connection.state
  val sleep: StateFlow<PlayerConnection.Sleep> = connection.sleep

  fun setSleepTimer(minutes: Int, endOfTrack: Boolean) = connection.setSleepTimer(minutes, endOfTrack)

  init {
    connection.connect()
  }

  fun play(tracks: List<TrackEntity>, index: Int) = connection.playTracks(tracks.map { it.mediaStoreId }, index)
  fun playNext(tracks: List<TrackEntity>) = connection.playNext(tracks.map { it.mediaStoreId })
  fun addToQueue(tracks: List<TrackEntity>) = connection.addToQueue(tracks.map { it.mediaStoreId })
  fun togglePlayPause() = connection.togglePlayPause()
  fun next() = connection.next()
  fun previous() = connection.previous()
  fun seekTo(positionMs: Long) = connection.seekTo(positionMs)
  fun toggleShuffle() = connection.toggleShuffle()
  fun cycleRepeat() = connection.cycleRepeat()
}
