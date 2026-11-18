package me.mallows.kanade.ui.player

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import me.mallows.kanade.core.data.db.TrackEntity
import me.mallows.kanade.playback.CurrentLyrics
import me.mallows.kanade.playback.LyricsMonitor
import me.mallows.kanade.playback.PlaybackEvent
import me.mallows.kanade.playback.PlaybackEvents
import me.mallows.kanade.playback.PlayerConnection
import me.mallows.kanade.playback.PlaySource
import me.mallows.kanade.playback.QueueState
import me.mallows.kanade.playback.PlayerState

/** 再生操作。画面全体で1つを共有する */
@HiltViewModel
class PlayerViewModel @Inject constructor(
  private val connection: PlayerConnection,
  lyricsMonitor: LyricsMonitor,
  playbackEvents: PlaybackEvents,
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

  val source: StateFlow<PlaySource?> = connection.source

  /** 読めない曲を飛ばした、などの知らせ */
  val events: SharedFlow<PlaybackEvent> = playbackEvents.flow

  /**
   * [tracks]の[index]番目から再生する。スキャン対象外の曲 (プレイリストに残っている曲) は飛ばす。
   * 押した曲が対象外なら再生しない (画面が知らせる)
   */
  fun play(tracks: List<TrackEntity>, index: Int, source: PlaySource?) {
    // 指定の曲が対象外なら、その後の最初の再生できる曲から始める (先頭が対象外のプレイリストを「再生」したときなど)
    val first = (index until tracks.size).firstOrNull { !tracks[it].excluded } ?: return
    val playable = tracks.filterNot { it.excluded }
    val start = first - tracks.take(first).count { it.excluded }
    connection.playTracks(playable.map { it.mediaStoreId }, start, source = source)
  }

  fun shufflePlay(tracks: List<TrackEntity>, source: PlaySource?) {
    val playable = tracks.filterNot { it.excluded }
    if (playable.isEmpty()) return
    connection.playTracks(playable.map { it.mediaStoreId }, playable.indices.random(), shuffle = true, source = source)
  }

  fun playNext(tracks: List<TrackEntity>) = connection.playNext(tracks.filterNot { it.excluded }.map { it.mediaStoreId })
  fun addToQueue(tracks: List<TrackEntity>) = connection.addToQueue(tracks.filterNot { it.excluded }.map { it.mediaStoreId })
  fun togglePlayPause() = connection.togglePlayPause()
  fun next() = connection.next()
  fun previous() = connection.previous()
  fun previousTrack() = connection.previousTrack()
  fun seekTo(positionMs: Long) = connection.seekTo(positionMs)

  /** 今の再生位置 (シークバーを描くたびに読む) */
  fun currentPosition(): Long = connection.currentPosition()
  fun setSpeed(speed: Float) = connection.setSpeed(speed)
  fun toggleShuffle() = connection.toggleShuffle()
  fun cycleRepeat() = connection.cycleRepeat()
}
