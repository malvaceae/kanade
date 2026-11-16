package me.mallows.kanade.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import me.mallows.kanade.audio.DspChain
import me.mallows.kanade.playback.output.HiResAudioSink
import me.mallows.kanade.playback.output.KanadeOutputProvider
import me.mallows.kanade.playback.output.OutputMonitor
import me.mallows.kanade.playback.output.OutputRouter
import me.mallows.kanade.playback.output.OutputSettings
import android.os.Bundle
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
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
import me.mallows.kanade.core.data.db.PlayStatsDao
import me.mallows.kanade.core.data.db.PlaybackStateDao
import me.mallows.kanade.core.data.db.TrackDao

/**
 * 再生サービス。
 * Android 17のバックグラウンド音声制限に対応するため、再生の開始はMediaSession経由 (アプリの画面、通知、メディアボタン) に限る。
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

  @Inject lateinit var trackDao: TrackDao
  @Inject lateinit var playbackStateDao: PlaybackStateDao
  @Inject lateinit var playStatsDao: PlayStatsDao
  @Inject lateinit var dsp: DspChain
  @Inject lateinit var eqSettings: EqRepository
  @Inject lateinit var outputRouter: OutputRouter
  @Inject lateinit var outputMonitor: OutputMonitor
  @Inject lateinit var outputSettings: OutputSettings

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var session: MediaSession? = null
  private lateinit var persistence: PlaybackPersistence
  private lateinit var sleepTimer: SleepTimer

  override fun onCreate() {
    super.onCreate()
    outputRouter.start(Handler(Looper.getMainLooper()))
    val renderers = KanadeRenderersFactory()
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
    PlayCounter(playStatsDao, scope).attach(player)
    // 等速と等速以外を切り替えたら、出力の形式 (float / 16bit) を合わせるため開き直す
    player.addListener(object : Player.Listener {
      override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        scope.launch {
          if (renderers.sink?.needsReopen() == true) reopen(player)
        }
      }
    })

    // ビットパーフェクトの設定。出力の形式は曲を開くときに決まるため、切り替えたら開き直す
    scope.launch {
      var first = true
      outputSettings.bitPerfect.collect { enabled ->
        outputRouter.bitPerfectEnabled = enabled
        outputRouter.refreshDevice()
        if (!first) reopen(player)
        first = false
      }
    }
  }

  /** 再生位置を保ったまま、音声の出力を作り直す */
  private fun reopen(player: ExoPlayer) {
    if (player.mediaItemCount == 0 || player.playbackState == ExoPlayer.STATE_IDLE) return
    val index = player.currentMediaItemIndex
    val position = player.currentPosition
    player.stop()
    player.seekTo(index, position)
    player.prepare()
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
    outputRouter.stop()
    scope.cancel()
    super.onDestroy()
  }

  /**
   * 音声の出力部品を差し替える。
   * - EQは16bitに落とさずfloatで通す ([HiResAudioSink])
   * - ビットパーフェクトのときは、AudioTrackを音源と同じ整数PCMで開く ([KanadeOutputProvider])
   * - 音源のビット深度を出力部品に伝えるため、音声レンダラーで入力形式を受け取る
   */
  private inner class KanadeRenderersFactory : DefaultRenderersFactory(this@PlaybackService) {
    var sink: HiResAudioSink? = null
      private set

    override fun buildAudioSink(
      context: Context,
      enableFloatOutput: Boolean,
      enableAudioOutputPlaybackParams: Boolean,
    ): AudioSink {
      val provider = KanadeOutputProvider(AudioTrackAudioOutputProvider.Builder(context).build(), outputMonitor)
      val base = DefaultAudioSink.Builder(context)
        .setEnableFloatOutput(true)
        .setAudioOutputProvider(provider)
        .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
        .build()
      return HiResAudioSink(base, dsp, provider, outputRouter, outputMonitor).also { sink = it }
    }

    override fun buildAudioRenderers(
      context: Context,
      extensionRendererMode: Int,
      mediaCodecSelector: MediaCodecSelector,
      enableDecoderFallback: Boolean,
      audioSink: AudioSink,
      eventHandler: Handler,
      eventListener: AudioRendererEventListener,
      out: ArrayList<Renderer>,
    ) {
      val adapterFactory = getCodecAdapterFactory()
      out += object : MediaCodecAudioRenderer(
        context, adapterFactory, mediaCodecSelector, enableDecoderFallback, eventHandler, eventListener, audioSink,
      ) {
        override fun onInputFormatChanged(formatHolder: FormatHolder): DecoderReuseEvaluation? {
          sink?.sourceFormat = formatHolder.format
          return super.onInputFormatChanged(formatHolder)
        }
      }
    }
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

    /**
     * アプリが動いていないときに、システムのメディア操作やBluetoothの再生ボタンから再生を求められたら、
     * 前回の再生キューと位置から再開する。
     */
    override fun onPlaybackResumption(
      mediaSession: MediaSession,
      controller: MediaSession.ControllerInfo,
      isForPlayback: Boolean,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
      val saved = persistence.load() ?: throw UnsupportedOperationException("再開できる再生キューがない")
      mediaSession.player.shuffleModeEnabled = saved.shuffle
      mediaSession.player.repeatMode = saved.repeatMode
      MediaSession.MediaItemsWithStartPosition(saved.items, saved.index, saved.positionMs)
    }

    override fun onAddMediaItems(
      mediaSession: MediaSession,
      controller: MediaSession.ControllerInfo,
      mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> =
      scope.future { resolve(mediaItems).toMutableList() }
  }
}
