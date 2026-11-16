package me.mallows.kanade.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.mallows.kanade.R
import me.mallows.kanade.playback.PlayerState
import me.mallows.kanade.ui.common.TrackArtwork
import me.mallows.kanade.ui.common.audioUri
import me.mallows.kanade.ui.icons.KanadeIcons

/** 画面下に常に出す小さなプレイヤー。タップでプレイヤー画面を開く */
@Composable
fun MiniPlayer(
  state: PlayerState,
  onOpen: () -> Unit,
  onTogglePlay: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val id = state.mediaStoreId ?: return
  val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp)
      .clip(RoundedCornerShape(16.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerHigh),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().height(64.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .clickable(onClick = onOpen)
          .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        TrackArtwork(uri = audioUri(id), size = 44.dp, cornerRadius = 8.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = state.title.orEmpty(),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = state.artist ?: stringResource(R.string.unknown_artist),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      IconButton(onClick = onTogglePlay, modifier = Modifier.padding(end = 6.dp).size(48.dp)) {
        Icon(
          imageVector = if (state.isPlaying) KanadeIcons.Pause else KanadeIcons.Play,
          contentDescription = stringResource(if (state.isPlaying) R.string.action_pause else R.string.action_play),
          modifier = Modifier.size(26.dp),
        )
      }
    }
    Box(Modifier.padding(horizontal = 12.dp)) {
      LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(2.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = Color.Transparent,
        drawStopIndicator = {},
        gapSize = 0.dp,
      )
    }
  }
}
