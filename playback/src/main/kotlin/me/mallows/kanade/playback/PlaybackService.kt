package me.mallows.kanade.playback

import android.content.Context
import android.content.Intent
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import me.mallows.kanade.audio.DspChain
import me.mallows.kanade.audio.EqAudioProcessor
import android.os.Bundle
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import me.mallows.kanade.core.data.db.PlaybackStateDao
import me.mallows.kanade.core.data.db.TrackDao

/**
 * 再生サービス。
 * Android 17のバックグラウンド音声制限に対応するため、再生の開始はMediaSession経由 (アプリの画面、通知、メディアボタン) に限る。
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

  @Inject lateinit var trackDao: TrackDao
  @Inject lateinit var playbackStateDao: PlaybackStateDao
  @Inject lateinit var dsp: DspChain
  @Inject lateinit var eqSettings: EqRepository

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var session: MediaSession? = null
  private lateinit var persistence: PlaybackPersistence
  private lateinit var sleepTimer: SleepTimer

  override fun onCreate() {
    super.onCreate()
    // イコライザーを再生処理に差し込む
    val renderers = object : DefaultRenderersFactory(this) {
      override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParams: Boolean,
      ): AudioSink = DefaultAudioSink.Builder(context)
        .setAudioProcessorChain(EqProcessorChain(EqAudioProcessor(dsp)))
        .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
        .build()
    }
    scope.launch { eqSettings.settings.collect(dsp::update) }

    val player = ExoPlayer.Builder(this, renderers)
      .setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(C.USAGE_MEDIA)
          .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
          .build(),
        /* handleAudioFocus = */ true,
      )
      .setHandleAudioBecomingNoisy(true)
      .setWakeMode(C.WAKE_MODE_LOCAL)
      .build()
    session = MediaSession.Builder(this, player)
      .setCallback(SessionCallback())
      .build()

    // 前回の再生キューと位置に戻し、以後の変化を保存する
    persistence = PlaybackPersistence(playbackStateDao, trackDao, scope)
    persistence.attach(player)
    scope.launch { persistence.restore(player) }
    sleepTimer = SleepTimer(player, scope)
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

  override fun onTaskRemoved(rootIntent: Intent?) {
    persistence.saveNow()
    val player = session?.player
    if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
      stopSelf()
    }
  }

  override fun onDestroy() {
    persistence.saveNow()
    session?.run {
      player.release()
      release()
    }
    session = null
    scope.cancel()
    super.onDestroy()
  }

  /** 画面から届いたIDだけの項目を、データベースの情報で再生できる項目に置き換える */
  private suspend fun resolve(items: List<MediaItem>): List<MediaItem> {
    val ids = items.mapNotNull { it.mediaId.toLongOrNull() }
    val tracks = trackDao.getByMediaStoreIds(ids).associateBy { it.mediaStoreId }
    return items.map { item -> tracks[item.mediaId.toLongOrNull()]?.toMediaItem(queued = item.isQueuedRequest) ?: item }
  }

  private inner class SessionCallback : MediaSession.Callback {
    override fun onConnect(
      session: MediaSession,
      controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
      val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
        .add(SleepCommand.command)
        .build()
      return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
        .setAvailableSessionCommands(commands)
        .build()
    }

    override fun onCustomCommand(
      session: MediaSession,
      controller: MediaSession.ControllerInfo,
      customCommand: SessionCommand,
      args: Bundle,
    ): ListenableFuture<SessionResult> {
      if (customCommand.customAction == SleepCommand.ACTION) {
        sleepTimer.set(args.getInt(SleepCommand.ARG_MINUTES), args.getBoolean(SleepCommand.ARG_END_OF_TRACK))
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
      }
      return super.onCustomCommand(session, controller, customCommand, args)
    }

    override fun onAddMediaItems(
      mediaSession: MediaSession,
      controller: MediaSession.ControllerInfo,
      mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> =
      scope.future { resolve(mediaItems).toMutableList() }
  }
}
