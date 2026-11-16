package me.mallows.kanade.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.mallows.kanade.R
import me.mallows.kanade.core.data.db.TrackEntity
import me.mallows.kanade.ui.common.TrackArtwork
import me.mallows.kanade.ui.common.formatDuration
import me.mallows.kanade.ui.icons.KanadeIcons

/** 全曲一覧 */
@Composable
fun LibraryScreen(
  playingMediaStoreId: Long?,
  onPlay: (tracks: List<TrackEntity>, index: Int) -> Unit,
  bottomPadding: Dp,
  modifier: Modifier = Modifier,
  viewModel: LibraryViewModel = hiltViewModel(),
) {
  val tracks by viewModel.tracks.collectAsStateWithLifecycle()
  val scanning by viewModel.scanning.collectAsStateWithLifecycle()
  LaunchedEffect(Unit) { viewModel.scanOnce() }

  Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
    Text(
      text = stringResource(R.string.library_all_tracks),
      style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
      modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
    )
    val list = tracks
    val summary = when {
      list == null || (list.isEmpty() && scanning) -> stringResource(R.string.library_scanning)
      else -> stringResource(R.string.library_summary, list.size, (list.sumOf { it.durationMs } / 60_000).toInt())
    }
    Text(
      text = summary,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 8.dp),
    )
    when {
      list == null -> Loading()
      list.isEmpty() && scanning -> Loading()
      list.isEmpty() -> Empty()
      else -> LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding),
      ) {
        itemsIndexed(list, key = { _, t -> t.id }) { index, track ->
          TrackRow(
            number = index + 1,
            track = track,
            playing = track.mediaStoreId == playingMediaStoreId,
            onClick = { onPlay(list, index) },
          )
        }
      }
    }
  }
}

@Composable
private fun TrackRow(number: Int, track: TrackEntity, playing: Boolean, onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(64.dp)
      .clickable(onClick = onClick)
      .padding(start = 12.dp, end = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
      if (playing) {
        Icon(KanadeIcons.Playing, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
      } else {
        Text(
          text = number.toString(),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    TrackArtwork(uri = track.uri, size = 44.dp)
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = track.title,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
        color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = "${track.artist ?: stringResource(R.string.unknown_artist)} · ${formatDuration(track.durationMs)}",
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun Loading() {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun Empty() {
  Column(
    modifier = Modifier.fillMaxSize().padding(32.dp),
    verticalArrangement = Arrangement.Center,
  ) {
    Text(stringResource(R.string.library_empty), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Text(
      stringResource(R.string.library_empty_hint),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
