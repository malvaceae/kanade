package me.mallows.kanade.ui.player

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import me.mallows.kanade.core.data.db.TrackEntity
import me.mallows.kanade.playback.CurrentLyrics
import me.mallows.kanade.playback.LyricsMonitor
import me.mallows.kanade.playback.PlayerConnection
import me.mallows.kanade.playback.QueueState
import me.mallows.kanade.playback.PlayerState

/** 再生操作。画面全体で1つを共有する */
@HiltViewModel
class PlayerViewModel @Inject constructor(
  private val connection: PlayerConnection,
  lyricsMonitor: LyricsMonitor,
) : ViewModel() {

  /** 再生中の曲の歌詞 */
  val lyrics: StateFlow<CurrentLyrics?> = lyricsMonitor.current


  val state: StateFlow<PlayerState> = connection.state
  val sleep: StateFlow<PlayerConnection.Sleep> = connection.sleep
  val queue: StateFlow<QueueState> = connection.queue

  fun setSleepTimer(minutes: Int, endOfTrack: Boolean) = connection.setSleepTimer(minutes, endOfTrack)

  init {
    connection.connect()
  }

  fun play(tracks: List<TrackEntity>, index: Int) = connection.playTracks(tracks.map { it.mediaStoreId }, index)
  fun shufflePlay(tracks: List<TrackEntity>) {
    if (tracks.isEmpty()) return
    connection.playTracks(tracks.map { it.mediaStoreId }, tracks.indices.random(), shuffle = true)
  }
  fun playNext(tracks: List<TrackEntity>) = connection.playNext(tracks.map { it.mediaStoreId })
  fun addToQueue(tracks: List<TrackEntity>) = connection.addToQueue(tracks.map { it.mediaStoreId })
  fun togglePlayPause() = connection.togglePlayPause()
  fun next() = connection.next()
  fun previous() = connection.previous()
  fun seekTo(positionMs: Long) = connection.seekTo(positionMs)
  fun setSpeed(speed: Float) = connection.setSpeed(speed)
  fun toggleShuffle() = connection.toggleShuffle()
  fun cycleRepeat() = connection.cycleRepeat()
}
