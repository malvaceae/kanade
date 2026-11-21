package me.mallows.kanade.playback

import android.app.PendingIntent
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import timber.log.Timber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
  @Inject lateinit var lyricsMonitor: LyricsMonitor
  @Inject lateinit var videoFrameMonitor: VideoFrameMonitor
  @Inject lateinit var playbackEvents: PlaybackEvents

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var session: MediaSession? = null
  private lateinit var persistence: PlaybackPersistence
  private lateinit var sleepTimer: SleepTimer

  override fun onCreate() {
    super.onCreate()
    outputRouter.start(Handler(Looper.getMainLooper()))
    // デコーダーの初期化に失敗したら、次のデコーダーに切り替える
    val renderers = KanadeRenderersFactory()
    renderers.setEnableDecoderFallback(true)
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
    // 通知やロック画面の再生中の曲をタップしたら、アプリのプレイヤー画面を開く
    // 入り口は、タップされたときに決まるようにする (起動時に決めて持っておくと、アプリのアイコンを変えたあとは
    // 無効になった入り口を指してしまい、タップしても開かない)
    val open = Intent(Intent.ACTION_MAIN)
      .addCategory(Intent.CATEGORY_LAUNCHER)
      .setPackage(packageName)
      .putExtra(EXTRA_OPEN_PLAYER, true)
      .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    session = MediaSession.Builder(this, player)
      .setSessionActivity(
        PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
      )
      .setCallback(SessionCallback())
      .build()

    // 前回の再生キューと位置に戻し、以後の変化を保存する
    persistence = PlaybackPersistence(playbackStateDao, trackDao, scope)
    persistence.attach(player)
    scope.launch { persistence.restore(player) }
    sleepTimer = SleepTimer(player, scope)
    PlayCounter(playStatsDao, scope).attach(player)
    // 動画には埋め込みの絵がないので、通知・ロック画面用に、動画のフレームを絵として付ける
    VideoArtwork(this, player, scope).attach()
    // シャッフルは今の曲を先頭にした順にする (途中の曲からオンにしても全曲が流れるように)
    player.keepShuffleFromCurrent()
    // スキャン対象外の曲は、キューに残したまま再生の順番が来たら飛ばす
    ExcludedSkipper(player, trackDao.observeExcludedIds().map { it.toSet() }, scope)
    // ReplayGainの設定。切り替えたら今の曲にもすぐ反映する
    scope.launch {
      outputSettings.replayGain.collect { mode ->
        renderers.sink?.let { it.replayGainMode = mode; it.applyReplayGain() }
      }
    }
    // 今の曲の歌詞を、曲が変わるたびに画面へ渡す
    player.addListener(object : Player.Listener {
      override fun onTracksChanged(tracks: Tracks) {
        val format = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }?.getTrackFormat(0)
        lyricsMonitor.set(player.currentMediaItem?.mediaId, lyricsOf(format))
      }
    })
    // 動画の切り替えで、前の動画の最後のコマが残らないよう、新しい動画の最初のコマが出たかを画面へ知らせる。
    // 操作による切り替え (前へ・次へ・曲の選び直し) では、映像の面が、新しい動画の最初のコマが出るまで前のコマを残すので、
    // 「まだ」に戻し、最初のコマが出たら、その項目を「出た」とする (ログで確かめた: 操作の切り替えのあと、約0.5秒で出る)。
    // 自動で次へ進むときと1曲リピートは、映像が途切れず流れ続けて、最初のコマの知らせは来ない (前のコマも残らない) ので、すぐ「出た」とする
    player.addListener(object : Player.Listener {
      override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        when (reason) {
          Player.MEDIA_ITEM_TRANSITION_REASON_AUTO, Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT ->
            videoFrameMonitor.set(mediaItem?.mediaId)
          else -> videoFrameMonitor.set(null)
        }
      }

      override fun onRenderedFirstFrame() = videoFrameMonitor.set(player.currentMediaItem?.mediaId)
    })
    // 読めない曲 (再スキャンの前に端末から消えたファイル・壊れたファイル) は、止まったままにせずキューから外して次へ進む
    player.addListener(object : Player.Listener {
      override fun onPlayerError(error: PlaybackException) {
        val notFound = error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
          error.errorCode == PlaybackException.ERROR_CODE_IO_NO_PERMISSION
        val unreadable = notFound || error.errorCode in UNREADABLE_ERRORS
        Timber.w(error, "再生できない曲: %s", player.currentMediaItem?.mediaId)
        if (!unreadable) {
          // 音声の出力の失敗など、どの曲でも同じように失敗するエラーは、飛ばし続けないよう止めたままにし、理由だけ知らせる
          playbackEvents.emit(PlaybackEvent.ERROR)
          return
        }
        if (player.mediaItemCount == 0) return
        // 外部のアプリから開いたファイルは、「再スキャンしてください」と案内しても意味がないので、専用の知らせにする
        val external = player.currentMediaItem?.isExternal == true
        playbackEvents.emit(
          when {
            external -> PlaybackEvent.EXTERNAL_UNPLAYABLE
            notFound -> PlaybackEvent.FILE_NOT_FOUND
            else -> PlaybackEvent.UNPLAYABLE
          },
        )
        val wasLast = player.currentMediaItemIndex == player.mediaItemCount - 1
        player.removeMediaItem(player.currentMediaItemIndex)
        if (player.mediaItemCount > 0 && !wasLast) {
          player.prepare()
          player.play()
        }
      }
    })
    // 等速と等速以外を切り替えたら、出力の形式 (float / 16bit) を合わせるため開き直す
    player.addListener(object : Player.Listener {
      override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        scope.launch {
          outputSettings.setSpeed(playbackParameters.speed)
          if (renderers.sink?.needsReopen() == true) reopen(player)
        }
      }
    })
    // 前回の再生速度に戻す (曲を開く前に決めておけば、開き直しは要らない)
    scope.launch {
      val speed = outputSettings.speed.first()
      if (speed != 1f) player.setPlaybackSpeed(speed)
    }

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

  companion object {
    /** ファイルを読めなかったときのエラー (飛ばして次の曲へ進む) */
    private val UNREADABLE_ERRORS = setOf(
      PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
      PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
      PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
      PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
      PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
      PlaybackException.ERROR_CODE_DECODING_FAILED,
      PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
      PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    )

    /** 起動の意図に付ける印。付いていればプレイヤー画面を開く */
    const val EXTRA_OPEN_PLAYER = "me.mallows.kanade.OPEN_PLAYER"
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
          sink?.onSourceFormat(formatHolder.format)
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
      // どの接続元にも同じ操作を許す (Media3の既定は、信頼されていないアプリの操作を絞る)
      return MediaSession.ConnectionResult.AcceptedResultBuilder()
        .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
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
