package me.mallows.kanade.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.mallows.kanade.R
import me.mallows.kanade.core.data.db.TrackEntity
import me.mallows.kanade.ui.icons.KanadeIcons

/** 選択モード中のチェック丸 */
@Composable
fun SelectionCheck(selected: Boolean, modifier: Modifier = Modifier) {
  val scheme = MaterialTheme.colorScheme
  Box(
    modifier = modifier
      .size(22.dp)
      .clip(CircleShape)
      .background(if (selected) scheme.primary else Color.Transparent)
      .border(2.dp, if (selected) scheme.primary else scheme.outline, CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    if (selected) Icon(KanadeIcons.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(14.dp))
  }
}

/** 曲の一覧の1行 (64dp) */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
  track: TrackEntity,
  number: Int?,
  playing: Boolean,
  selectionMode: Boolean,
  selected: Boolean,
  onClick: () -> Unit,
  onLongClick: (() -> Unit)?,
  modifier: Modifier = Modifier,
  trailing: (@Composable RowScope.() -> Unit)? = null,
) {
  val scheme = MaterialTheme.colorScheme
  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(64.dp)
      .background(if (selected) scheme.primaryContainer else Color.Transparent)
      .combinedClickable(onClick = onClick, onLongClick = onLongClick)
      .padding(start = 12.dp, end = if (trailing != null) 4.dp else 16.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
      when {
        selectionMode -> SelectionCheck(selected)
        playing -> PlayingBars(color = scheme.primary, animating = LocalIsPlaying.current)
        number != null -> Text(number.toString(), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
      }
    }
    // スキャン対象外の曲 (プレイリストに残っている曲) は薄く出す
    val dim = if (track.excluded) Modifier.alpha(0.45f) else Modifier
    TrackArtwork(uri = track.uri, size = 44.dp, modifier = dim)
    Column(modifier = Modifier.weight(1f).then(dim), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = track.title,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
        color = if (playing) scheme.primary else scheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = listOfNotNull(
          stringResource(R.string.excluded_label).takeIf { track.excluded },
          track.artist ?: stringResource(R.string.unknown_artist),
          formatDuration(track.durationMs),
        ).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
        color = scheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    trailing?.invoke(this)
  }
}

/** アルバム・アーティストなどの一覧の1行 */
@Composable
fun GroupRow(
  title: String,
  subtitle: String,
  artUri: String?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  onLongClick: (() -> Unit)? = null,
  /** 選択モードのときだけ、選んでいるかどうか (nullなら選択モードではない) */
  selected: Boolean? = null,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(72.dp)
      .then(if (selected == true) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
      .combinedClickableCompat(onClick, onLongClick)
      .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // 選択中は曲の一覧と同じ丸い印を左に出す
    if (selected != null) SelectionCheck(selected)
    if (artUri != null) TrackArtwork(uri = artUri, size = 52.dp, cornerRadius = 8.dp)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: (() -> Unit)?) =
  combinedClickable(onClick = onClick, onLongClick = onLongClick)

/** 小見出しの行 (件数とソートボタンなど) */
@Composable
fun ListHeader(text: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
  Row(
    modifier = modifier.fillMaxWidth().height(48.dp).padding(start = 16.dp, end = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
    actions()
  }
}
