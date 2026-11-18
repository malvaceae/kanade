package me.mallows.kanade.ui.player

import androidx.compose.runtime.LaunchedEffect
import me.mallows.kanade.ui.common.NowPlayingArtwork
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
        .padding(horizontal = 32.dp, vertical = 16.dp),
      contentAlignment = Alignment.Center,
    ) {
      val size = minOf(maxWidth, maxHeight)
      SwipeableArtwork(
        mediaStoreId = state.mediaStoreId,
        hasNext = state.hasNext,
        hasPrevious = state.hasPrevious,
        onNext = onNext,
        onPrevious = onSwipePrevious,
        onRestart = onPrevious,
      ) {
        val id = state.mediaStoreId
        if (id != null) {
          NowPlayingArtwork(state.artworkData, audioUri(id), size = size, cornerRadius = 20.dp)
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
      Row {
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
  mediaStoreId: Long?,
  hasNext: Boolean,
  hasPrevious: Boolean,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  /** 前の曲がないときに右へ払ったら、曲の頭に戻す */
  onRestart: () -> Unit,
  content: @Composable () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val offset = remember { Animatable(0f) }
  val currentId by rememberUpdatedState(mediaStoreId)
  val canNext by rememberUpdatedState(hasNext)
  val canPrevious by rememberUpdatedState(hasPrevious)
  Box(
    modifier = Modifier
      .aspectRatio(1f)
      .graphicsLayer {
        translationX = offset.value
        alpha = 1f - (kotlin.math.abs(offset.value) / size.width).coerceIn(0f, 0.5f)
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
