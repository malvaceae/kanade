package me.mallows.kanade.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.mallows.kanade.R
import me.mallows.kanade.core.domain.lyrics.Lyrics
import me.mallows.kanade.ui.common.rememberFullSheetState

/** 歌詞。時刻付きなら今の行を強調して追いかけ、行を押すとその位置へ移る */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSheet(title: String?, lyrics: Lyrics, positionMs: Long, onSeek: (Long) -> Unit, onDismiss: () -> Unit) {
  val current = lyrics.indexAt(positionMs)
  val listState = rememberLazyListState()
  LaunchedEffect(current) {
    // 今の行が上から3行目あたりに来るようにする
    if (current >= 0) listState.animateScrollToItem((current - 2).coerceAtLeast(0))
  }
  val scheme = MaterialTheme.colorScheme
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberFullSheetState()) {
    Column(Modifier.fillMaxHeight(0.85f)) {
      Text(
        title ?: stringResource(R.string.lyrics_title),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
      )
      LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 48.dp)) {
        itemsIndexed(lyrics.lines) { i, line ->
          val active = i == current
          val time = line.timeMs
          Text(
            line.text.ifEmpty { "♪" },
            style = if (lyrics.synced) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = when {
              !lyrics.synced -> scheme.onSurface
              active -> scheme.primary
              else -> scheme.onSurfaceVariant
            },
            modifier = Modifier
              .fillMaxWidth()
              .then(if (time != null) Modifier.clickable { onSeek(time) } else Modifier)
              .padding(horizontal = 24.dp, vertical = if (lyrics.synced) 8.dp else 2.dp),
          )
        }
      }
    }
  }
}
