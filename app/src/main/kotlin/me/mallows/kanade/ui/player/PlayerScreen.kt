package me.mallows.kanade.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlinx.coroutines.launch
import me.mallows.kanade.R
import me.mallows.kanade.core.domain.lyrics.Lyrics
import me.mallows.kanade.playback.PlayerConnection
import me.mallows.kanade.playback.PlayerState
import androidx.compose.runtime.produceState
import me.mallows.kanade.ui.common.TrackArtwork
import me.mallows.kanade.ui.common.audioUri
import me.mallows.kanade.ui.common.formatDuration
import me.mallows.kanade.ui.icons.KanadeIcons

/** 再生中の曲の画面。アートワークが主役 */
@Composable
fun PlayerScreen(
  state: PlayerState,
  onClose: () -> Unit,
  onOpenQueue: () -> Unit,
  onOpenEqualizer: () -> Unit,
  onTogglePlay: () -> Unit,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  /** アートワークを右へ払ったとき。「前へ」ボタンと違い、再生位置に関係なく前の曲へ移る */
  onSwipePrevious: () -> Unit,
  onSeek: (Long) -> Unit,
  onToggleShuffle: () -> Unit,
  onCycleRepeat: () -> Unit,
  sleep: PlayerConnection.Sleep,
  onSetSleep: (minutes: Int, endOfTrack: Boolean) -> Unit,
  onSetSpeed: (Float) -> Unit,
  /** 動画の映像を出す面の取り付けと取り外し */
  onAttachVideo: (SurfaceView) -> Unit,
  onDetachVideo: (SurfaceView) -> Unit,
  /** 全画面に入る・出るときの知らせ (全画面のあいだ、下のメニューを隠すため) */
  onFullscreenChange: (Boolean) -> Unit,
  /** 今の動画の最初の映像が出たか。出るまでは、前の動画のコマが見えないよう、映像をカバーで覆う */
  videoReady: Boolean,
  /** 今の動画の映像の縦横比。最初の映像が出るまでは0 (そのあいだは、前の縦横比のままにして、枠を跳ねさせない) */
  videoAspect: Float,
  /** 再生元の一覧の名前 (分からなければアルバム名を出す) */
  sourceTitle: String?,
  lyrics: Lyrics?,
) {
  val scheme = MaterialTheme.colorScheme
  var sheet by remember { mutableStateOf<String?>(null) }
  val target = rememberArtworkBackground(
    artworkData = state.artworkData,
    // アートワークは曲が変わった少し後に届くことがあるので、データが変わるたびに作り直す
    key = state.artworkData,
    fallback = scheme.background,
    base = scheme.background,
  )
  // 既定のばねの動きでは暗い色から明るい色へ急に変わって見えるので、時間をかけて移す
  val background by animateColorAsState(target, animationSpec = tween(700, easing = LinearOutSlowInEasing), label = "background")

  // 動画の全画面。自分で選んだとき、または横向きのとき (横向きの動画は、全画面で見るもの)
  var fullscreenChosen by rememberSaveable { mutableStateOf(false) }
  val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
  // 枠の縦横比。新しい動画の最初の映像が出るまで、前の縦横比のままにする
  var lastAspect by remember { mutableFloatStateOf(16f / 9f) }
  // 映像の面が、今の枠の縦横比の大きさに変わり終えたか。変わる前に覆いを外すと、新しい映像が、前の大きさの面に一瞬出る
  var surfaceAspect by remember { mutableFloatStateOf(0f) }
  LaunchedEffect(videoAspect) { if (videoAspect > 0f) lastAspect = videoAspect }
  val frameAspect = if (videoAspect > 0f) videoAspect else lastAspect
  val surfaceSettled = surfaceAspect > 0f && kotlin.math.abs(surfaceAspect - frameAspect) <= frameAspect * 0.02f
  val showVideo = videoReady && surfaceSettled
  val fullscreen = state.isVideo && (fullscreenChosen || landscape)
  DisposableEffect(fullscreen) {
    onFullscreenChange(fullscreen)
    onDispose { onFullscreenChange(false) }
  }
  if (fullscreen) {
    VideoFullscreen(
      state = state,
      videoReady = showVideo,
      videoAspect = frameAspect,
      onSurfaceAspect = { surfaceAspect = it },
      onNext = onNext,
      onPrevious = onPrevious,
      onAttach = onAttachVideo,
      onDetach = onDetachVideo,
      onTogglePlay = onTogglePlay,
      onSeek = onSeek,
      // 横向きでは全画面から戻せないので、画面ごと閉じる。縦向きなら、全画面をやめる
      onExit = { if (landscape) onClose() else fullscreenChosen = false },
    )
    return
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(background)
      .safeDrawingPadding(),
  ) {
    // 上部
    Row(
      modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onClose) {
        Icon(KanadeIcons.ChevronDown, contentDescription = stringResource(R.string.action_close), modifier = Modifier.size(26.dp))
      }
      Text(
        text = sourceTitle ?: state.album.orEmpty(),
        style = MaterialTheme.typography.bodyMedium,
        color = scheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      IconButton(onClick = onOpenQueue) {
        Icon(KanadeIcons.Queue, contentDescription = stringResource(R.string.action_open_queue))
      }
    }

    // アートワーク (左右にスワイプで曲送り)
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        // アートワークは左右に余白を空けるが、動画の映像は、画面の幅いっぱいに出す
        .padding(horizontal = if (state.isVideo) 0.dp else 32.dp, vertical = 16.dp),
      contentAlignment = Alignment.Center,
    ) {
      val size = minOf(maxWidth, maxHeight)
      SwipeableArtwork(
        mediaId = state.mediaId,
        hasNext = state.hasNext,
        hasPrevious = state.hasPrevious,
        onNext = onNext,
        onPrevious = onSwipePrevious,
        onRestart = onPrevious,
        // 動画は映像の縦横比の枠にする (縦長でも、はみ出さず、枠の中に収まる)
        aspect = if (state.isVideo) frameAspect else 1f,
        fade = !state.isVideo,
      ) {
        val uri = state.uri
        if (state.isVideo) {
          Box(Modifier.fillMaxSize()) {
            VideoSurface(
              aspect = frameAspect,
              onAttach = onAttachVideo,
              onDetach = onDetachVideo,
              onSurfaceAspect = { surfaceAspect = it },
              modifier = Modifier.fillMaxSize(),
            )
            // 最初の映像が出るまでは、前の動画の最後のコマが見えないよう、画面の背景で覆う
            if (!showVideo) Box(Modifier.fillMaxSize().background(background))
            // 全画面は、このボタンだけ (映像に触れただけでは、全画面にならない)
            IconButton(
              onClick = { fullscreenChosen = true },
              modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
            ) {
              Icon(
                KanadeIcons.Fullscreen,
                contentDescription = stringResource(R.string.action_fullscreen),
                tint = Color.White,
                modifier = Modifier.size(22.dp),
              )
            }
          }
        } else if (uri != null) {
          TrackArtwork(uri = uri, size = size, cornerRadius = 20.dp)
        } else {
          Box(Modifier.size(size).clip(MaterialTheme.shapes.large).background(scheme.surfaceContainerHigh))
        }
      }
    }

    // 曲名
    Column(modifier = Modifier.padding(horizontal = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        text = state.title.orEmpty(),
        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
        maxLines = 1,
        // 長い曲名は省かず、横に流して全体を読めるようにする
        modifier = Modifier.basicMarquee(initialDelayMillis = 2_000),
      )
      Text(
        text = state.artist ?: stringResource(R.string.unknown_artist),
        style = MaterialTheme.typography.bodyLarge,
        color = scheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.basicMarquee(initialDelayMillis = 2_000),
      )
    }

    // シークバー
    SeekBar(state = state, onSeek = onSeek, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))

    // 操作ボタン
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onToggleShuffle) {
        Icon(
          KanadeIcons.Shuffle,
          contentDescription = stringResource(R.string.action_shuffle),
          tint = if (state.shuffle) scheme.primary else scheme.onSurfaceVariant,
        )
      }
      IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
        Icon(KanadeIcons.Previous, contentDescription = stringResource(R.string.action_previous), modifier = Modifier.size(30.dp))
      }
      IconButton(
        onClick = onTogglePlay,
        modifier = Modifier.size(76.dp).clip(CircleShape).background(scheme.primary),
      ) {
        Icon(
          if (state.showPause) KanadeIcons.Pause else KanadeIcons.Play,
          contentDescription = stringResource(if (state.showPause) R.string.action_pause else R.string.action_play),
          tint = scheme.onPrimary,
          modifier = Modifier.size(32.dp),
        )
      }
      // リピートなしで最後の曲なら押せない (通知などのボタンと同じ)
      IconButton(onClick = onNext, enabled = state.hasNext, modifier = Modifier.size(56.dp)) {
        Icon(KanadeIcons.Next, contentDescription = stringResource(R.string.action_next), modifier = Modifier.size(30.dp))
      }
      IconButton(onClick = onCycleRepeat) {
        Icon(
          if (state.repeatMode == Player.REPEAT_MODE_ONE) KanadeIcons.RepeatOne else KanadeIcons.Repeat,
          contentDescription = stringResource(R.string.action_repeat),
          tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) scheme.onSurfaceVariant else scheme.primary,
        )
      }
    }

    // スリープタイマーと曲の情報
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      androidx.compose.material3.TextButton(onClick = { sheet = "sleep" }) {
        Icon(KanadeIcons.Timer, contentDescription = null, tint = if (sleep.active) scheme.primary else scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(
          sleepLabel(sleep),
          style = MaterialTheme.typography.bodySmall,
          color = if (sleep.active) scheme.primary else scheme.onSurfaceVariant,
          modifier = Modifier.padding(start = 6.dp),
        )
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
      if (lyrics != null) {
        androidx.compose.material3.TextButton(onClick = { sheet = "lyrics" }) {
          Text(
            stringResource(R.string.lyrics_title),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = scheme.onSurfaceVariant,
          )
        }
      }
      // 再生速度 (等速のときは目立たせない)
      val changed = state.speed != 1f
      androidx.compose.material3.TextButton(onClick = { sheet = "speed" }) {
        Text(
          speedLabel(state.speed),
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
          color = if (changed) scheme.primary else scheme.onSurfaceVariant,
        )
      }
      androidx.compose.material3.TextButton(onClick = onOpenEqualizer) {
        Text("EQ", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = scheme.onSurfaceVariant)
      }
      IconButton(onClick = { sheet = "info" }) {
        Icon(KanadeIcons.Info, contentDescription = stringResource(R.string.action_track_info), tint = scheme.onSurfaceVariant)
      }
      }
    }
    Spacer(Modifier.height(16.dp))
  }

  when (sheet) {
    "lyrics" -> lyrics?.let {
      LyricsSheet(title = state.title, lyrics = it, positionMs = state.positionMs, onSeek = onSeek, onDismiss = { sheet = null })
    }
    "speed" -> SpeedSheet(current = state.speed, onSet = { onSetSpeed(it); sheet = null }, onDismiss = { sheet = null })
    "sleep" -> SleepTimerSheet(
      active = sleep.active,
      onSet = { m, end -> onSetSleep(m, end); sheet = null },
      onDismiss = { sheet = null },
    )
    "info" -> TrackInfoDialog(state = state, onDismiss = { sheet = null })
  }
}

@Composable
private fun sleepLabel(sleep: PlayerConnection.Sleep): String {
  val until = sleep.until
  return when {
    sleep.endOfTrack -> stringResource(R.string.sleep_end_of_track)
    until != null -> {
      // 1分ごとに残り時間を更新する
      val now by produceState(System.currentTimeMillis(), until) {
        while (true) {
          value = System.currentTimeMillis()
          kotlinx.coroutines.delay(15_000)
        }
      }
      val minutes = ((until - now) / 60_000 + 1).coerceAtLeast(1)
      stringResource(R.string.sleep_remaining, minutes.toInt())
    }
    else -> stringResource(R.string.sleep_title)
  }
}

/** 左右のスワイプで曲を切り替えるアートワーク */
@Composable
private fun SwipeableArtwork(
  mediaId: String?,
  hasNext: Boolean,
  hasPrevious: Boolean,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  /** 前の曲がないときに右へ払ったら、曲の頭に戻す */
  onRestart: () -> Unit,
  /** 枠の縦横比 (横 / 縦)。アートワークは正方形 (1)、動画は映像の縦横比 */
  aspect: Float = 1f,
  /**
   * 動いている間、端を半透明にするか。動画では、しない: 映像の面 (SurfaceView) はウィンドウの背面にあり、
   * 半透明の部品の下では透けて見えるので、前の動画のコマを覆いで隠せなくなる
   */
  fade: Boolean = true,
  content: @Composable () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val offset = remember { Animatable(0f) }
  val currentId by rememberUpdatedState(mediaId)
  val canNext by rememberUpdatedState(hasNext)
  val canPrevious by rememberUpdatedState(hasPrevious)
  Box(
    modifier = Modifier
      .aspectRatio(aspect)
      .graphicsLayer {
        translationX = offset.value
        alpha = if (fade) 1f - (kotlin.math.abs(offset.value) / size.width).coerceIn(0f, 0.5f) else 1f
      }
      .pointerInput(Unit) {
        val width = size.width.toFloat()
        // ゆっくり動かしたときは幅の20%で切り替える。素早く払ったときは短い距離でも切り替える (一般的なスワイプと同じ)
        val threshold = width * 0.2f
        val flingVelocity = 600.dp.toPx()
        val flingDistance = 24.dp.toPx()
        val tracker = VelocityTracker()
        // アートワークは指と一緒に動くので、中での位置ではなく動かした量の合計で速さを測る
        var dragged = 0f
        /** 今のアートワークを [direction] の側へ抜き、曲を切り替えて、反対側から新しいアートワークを入れる */
        suspend fun slide(direction: Float, change: () -> Unit) {
          val before = currentId
          offset.animateTo(direction * width, tween(160, easing = FastOutLinearInEasing))
          change()
          // 新しい曲の情報が届くまで少し待つ (届く前に入れると、古いアートワークが戻ってくる)
          withTimeoutOrNull(500) { snapshotFlow { currentId }.first { it != before } }
          offset.snapTo(-direction * width)
          offset.animateTo(0f, tween(240, easing = LinearOutSlowInEasing))
        }
        detectHorizontalDragGestures(
          onDragStart = { tracker.resetTracking(); dragged = 0f },
          onDragEnd = {
            val value = offset.value
            val velocity = tracker.calculateVelocity().x
            val toNext = value < -threshold || (value < -flingDistance && velocity < -flingVelocity)
            val toPrevious = value > threshold || (value > flingDistance && velocity > flingVelocity)
            scope.launch {
              when {
                toNext && canNext -> slide(-1f, onNext)
                toPrevious && canPrevious -> slide(1f, onPrevious)
                else -> {
                  // 前の曲がないときに右へ払うと、ボタンと同じく曲の頭に戻す (アートワークは動かさない)
                  if (toPrevious) onRestart()
                  offset.animateTo(0f, spring())
                }
              }
            }
          },
          onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
        ) { change, dragAmount ->
          change.consume()
          dragged += dragAmount
          tracker.addPosition(change.uptimeMillis, Offset(dragged, 0f))
          scope.launch { offset.snapTo(offset.value + dragAmount) }
        }
      },
  ) {
    content()
  }
}

@Composable
private fun SeekBar(state: PlayerState, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
  var dragging by remember { mutableStateOf<Float?>(null) }
  /**
   * 指を離して移った先。プレイヤーの位置がそこに届くまでは、つまみをここに置いておく
   * (離した直後は、プレイヤーの位置がまだ移る前の値のことがあり、つまみが一瞬戻ってから飛ぶのを避ける)
   */
  var seekTarget by remember { mutableStateOf<Long?>(null) }
  val duration = state.durationMs.coerceAtLeast(1)
  val position by rememberPlaybackPosition(state)
  val target = seekTarget
  if (target != null) {
    // 届いたら (または一定時間たったら) プレイヤーの位置に戻す
    LaunchedEffect(target) {
      withTimeoutOrNull(SEEK_SETTLE_MS) { snapshotFlow { position }.first { kotlin.math.abs(it - target) < SEEK_REACHED_MS } }
      seekTarget = null
    }
  }
  val shownPosition = target ?: position
  // 長さが分からない間は、バーを空にしておく
  val fraction = dragging ?: if (state.durationMs > 0) (shownPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
  Column(modifier = modifier) {
    Slider(
      value = fraction,
      onValueChange = { dragging = it },
      onValueChangeFinished = {
        dragging?.let {
          val to = (it * duration).toLong()
          seekTarget = to
          onSeek(to)
        }
        dragging = null
      },
      colors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.onSurface,
        activeTrackColor = MaterialTheme.colorScheme.onSurface,
        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
      ),
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
      val shown = dragging?.let { (it * duration).toLong() } ?: shownPosition
      Text(formatDuration(shown), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(formatDuration(state.durationMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

/** シークの後、プレイヤーの位置が移った先に届くのを待つ最長の時間 */
private const val SEEK_SETTLE_MS = 1_500L

/** 移った先とのずれがこれより小さければ、届いたとみなす */
private const val SEEK_REACHED_MS = 1_000L

/** 動画の映像。縦横比を保って、いっぱいに広げる。画面から外れる (または背景へ回る) と、映像の出力は止まる */
@Composable
private fun VideoSurface(
  aspect: Float,
  onAttach: (SurfaceView) -> Unit,
  onDetach: (SurfaceView) -> Unit,
  /** 映像の面の大きさが変わった (変わり終わった) とき、その縦横比 (横 / 縦) を知らせる */
  onSurfaceAspect: (Float) -> Unit = {},
  modifier: Modifier = Modifier.fillMaxWidth().aspectRatio(if (aspect > 0f) aspect else 16f / 9f),
) {
  var view by remember { mutableStateOf<SurfaceView?>(null) }
  val currentOnSurfaceAspect by rememberUpdatedState(onSurfaceAspect)
  DisposableEffect(view) {
    val v = view
    if (v != null) onAttach(v)
    onDispose { if (v != null) onDetach(v) }
  }
  AndroidView(
    factory = { context ->
      SurfaceView(context).apply {
        holder.addCallback(object : SurfaceHolder.Callback {
          override fun surfaceCreated(holder: SurfaceHolder) = Unit

          override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            if (height > 0) currentOnSurfaceAspect(width.toFloat() / height)
          }

          override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
        })
      }
    },
    update = { if (view !== it) view = it },
    // 縦横比が分かるまでは、16:9の黒い面にしておく
    modifier = modifier,
  )
}

/** 動画の全画面。黒い背景に映像を最大に出す。画面をタップすると操作を出し入れする (再生中は3秒で隠す) */
@Composable
private fun VideoFullscreen(
  state: PlayerState,
  videoReady: Boolean,
  videoAspect: Float,
  onSurfaceAspect: (Float) -> Unit,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  onAttach: (SurfaceView) -> Unit,
  onDetach: (SurfaceView) -> Unit,
  onTogglePlay: () -> Unit,
  onSeek: (Long) -> Unit,
  onExit: () -> Unit,
) {
  // 全画面に入ったときは、操作を出しておく (自動で隠れる)
  var controls by remember { mutableStateOf(true) }
  val view = LocalView.current
  // 全画面のあいだは、ステータスバーとナビゲーションバーを隠す (端から払うと一時的に出る)
  DisposableEffect(view) {
    val window = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()?.window
    val insets = window?.let { WindowCompat.getInsetsController(it, view) }
    insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    insets?.hide(WindowInsetsCompat.Type.systemBars())
    onDispose { insets?.show(WindowInsetsCompat.Type.systemBars()) }
  }
  BackHandler(onBack = onExit)
  LaunchedEffect(controls, state.showPause) {
    if (controls && state.showPause) {
      delay(CONTROLS_HIDE_MS)
      controls = false
    }
  }
  Box(
    Modifier
      .fillMaxSize()
      .background(Color.Black)
      .clickable(interactionSource = null, indication = null) { controls = !controls },
  ) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      val aspect = videoAspect
      val fitWidth = maxWidth / maxHeight <= aspect
      VideoSurface(
        aspect = aspect,
        onAttach = onAttach,
        onDetach = onDetach,
        onSurfaceAspect = onSurfaceAspect,
        modifier = if (fitWidth) Modifier.fillMaxWidth().aspectRatio(aspect) else Modifier.fillMaxHeight().aspectRatio(aspect),
      )
      // 最初の映像が出るまでは、前の動画の最後のコマが見えないよう、黒で覆う
      if (!videoReady) Box(Modifier.fillMaxSize().background(Color.Black))
    }
    AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut()) {
      Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).safeDrawingPadding()) {
        IconButton(onClick = onExit, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
          Icon(KanadeIcons.Close, contentDescription = stringResource(R.string.action_close), tint = Color.White)
        }
        Text(
          text = state.title.orEmpty(),
          style = MaterialTheme.typography.titleMedium,
          color = Color.White,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.align(Alignment.TopStart).padding(start = 56.dp, top = 14.dp, end = 16.dp),
        )
        Row(
          modifier = Modifier.align(Alignment.Center),
          horizontalArrangement = Arrangement.spacedBy(24.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))) {
            Icon(KanadeIcons.Previous, contentDescription = stringResource(R.string.action_previous), tint = Color.White, modifier = Modifier.size(26.dp))
          }
          IconButton(
            onClick = onTogglePlay,
            modifier = Modifier.size(72.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
          ) {
            Icon(
              if (state.showPause) KanadeIcons.Pause else KanadeIcons.Play,
              contentDescription = stringResource(if (state.showPause) R.string.action_pause else R.string.action_play),
              tint = Color.White,
              modifier = Modifier.size(34.dp),
            )
          }
          IconButton(
            onClick = onNext,
            enabled = state.hasNext,
            modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
          ) {
            Icon(
              KanadeIcons.Next,
              contentDescription = stringResource(R.string.action_next),
              tint = Color.White.copy(alpha = if (state.hasNext) 1f else 0.4f),
              modifier = Modifier.size(26.dp),
            )
          }
        }
        SeekBar(state = state, onSeek = onSeek, modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 8.dp))
      }
    }
  }
}

private const val CONTROLS_HIDE_MS = 3_000L
