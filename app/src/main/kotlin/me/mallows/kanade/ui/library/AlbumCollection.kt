package me.mallows.kanade.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.mallows.kanade.R
import me.mallows.kanade.core.data.db.AlbumSummary
import me.mallows.kanade.core.data.db.TrackEntity
import me.mallows.kanade.core.data.library.GroupKind
import me.mallows.kanade.ui.common.GroupRow
import me.mallows.kanade.ui.common.SelectionCheck
import me.mallows.kanade.ui.common.TrackArtwork
import me.mallows.kanade.ui.selection.TrackActions
import me.mallows.kanade.ui.selection.dragToSelect
import me.mallows.kanade.ui.selection.navigationBarHeight

/** アルバムの格子の左右の余白 */
val GRID_SIDE_PADDING = 12.dp

/**
 * アルバムの一覧 (格子か一覧)。ライブラリのアルバムとアーティストの中のアルバムで同じ見せ方にする。
 * 長押しで選択を始め、そのまま指を動かすと範囲で選べる (格子も)
 */
@Composable
fun AlbumCollection(
  albums: List<AlbumSummary>,
  /** 格子で並べるか (ライブラリのアルバムと同じ設定) */
  grid: Boolean,
  /** 各アルバムの2行目 (ライブラリではアーティスト、アーティストの画面では発売年)。nullなら出さない */
  secondLine: @Composable (AlbumSummary) -> String?,
  /** 一番上の行。格子のときは左右の余白を付けずに画面の幅で出す */
  header: @Composable (Modifier) -> Unit,
  onOpen: (albumId: String?, title: String) -> Unit,
  /** まとめて再生したときの再生元の名前 */
  sourceTitle: String,
  load: suspend (GroupTarget) -> List<TrackEntity>,
  actions: TrackActions,
  bottomPadding: Dp,
) {
  // 長押しで選択を始める。選択中は押すたびに選択を切り替える
  var selectedKeys by rememberSaveable { mutableStateOf(setOf<String>()) }
  /** 一番上の行の高さ (px)。選択中の上のバーをこの高さにして、ぴったり重ねる */
  var headerHeight by remember { mutableIntStateOf(0) }
  val density = LocalDensity.current
  val unknown = stringResource(R.string.unknown_album)
  val keyOf = { a: AlbumSummary -> a.albumId?.toString() ?: "" }
  val titleOf = { a: AlbumSummary -> a.album ?: unknown }
  val selecting = selectedKeys.isNotEmpty()
  val toggle = { a: AlbumSummary -> val k = keyOf(a); selectedKeys = if (k in selectedKeys) selectedKeys - k else selectedKeys + k }
  val click = { a: AlbumSummary -> if (selecting) toggle(a) else onOpen(a.albumId?.toString(), titleOf(a)) }
  val padding = if (selecting) 88.dp + navigationBarHeight() else bottomPadding
  // 長押ししたまま指を動かすと、通ったアルバムまでをまとめて選ぶ (曲の一覧と同じ)
  val haptic = LocalHapticFeedback.current
  val keys = albums.map(keyOf)
  val gridState = rememberLazyGridState()
  val listState = rememberLazyListState()
  val measuredHeader = Modifier.onSizeChanged { headerHeight = it.height }
  Box(Modifier.fillMaxSize()) {
    if (grid) {
      LazyVerticalGrid(
        state = gridState,
        modifier = Modifier.dragToSelect(
          state = gridState,
          keys = keys,
          currentSelection = { selectedKeys },
          onStart = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
          onChange = { selectedKeys = it },
        ),
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(start = GRID_SIDE_PADDING, end = GRID_SIDE_PADDING, bottom = padding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // 格子の左右の余白を見出しには付けず、ほかの画面と同じ位置にボタンを置く
        item(span = { GridItemSpan(maxLineSpan) }) {
          header(
            Modifier
              .layout { measurable, constraints ->
                val extra = (GRID_SIDE_PADDING * 2).roundToPx()
                val width = constraints.maxWidth + extra
                val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
              }
              .then(measuredHeader),
          )
        }
        gridItems(albums, key = keyOf) { album ->
          val isSelected = keyOf(album) in selectedKeys
          Column(Modifier.clickable { click(album) }) {
            BoxWithConstraints {
              TrackArtwork(uri = album.artUri, size = maxWidth, cornerRadius = 10.dp)
              if (selecting) {
                // 選んだアルバムは薄く覆い、右上に印を出す
                if (isSelected) {
                  Box(Modifier.matchParentSize().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)))
                }
                SelectionCheck(
                  isSelected,
                  Modifier.align(Alignment.TopEnd).padding(8.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape),
                )
              }
            }
            Spacer(Modifier.height(6.dp))
            Text(titleOf(album), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            secondLine(album)?.let {
              Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }
        }
      }
    } else {
      LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = padding),
        modifier = Modifier.dragToSelect(
          state = listState,
          keys = keys,
          currentSelection = { selectedKeys },
          onStart = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
          onChange = { selectedKeys = it },
        ),
      ) {
        item { header(measuredHeader) }
        items(albums, key = keyOf) { album ->
          GroupRow(
            title = titleOf(album),
            subtitle = listOfNotNull(
              secondLine(album),
              pluralStringResource(R.plurals.track_count, album.trackCount, album.trackCount),
            ).joinToString(" · "),
            artUri = album.artUri,
            onClick = { click(album) },
            selected = if (selecting) keyOf(album) in selectedKeys else null,
          )
        }
      }
    }
    GroupSelectionBars(
      selected = albums.filter { keyOf(it) in selectedKeys }.map { GroupTarget(GroupKind.ALBUM, it.albumId?.toString(), titleOf(it)) },
      allSelected = selectedKeys.size == albums.size,
      onSelectAll = { selectedKeys = if (selectedKeys.size == albums.size) emptySet() else keys.toSet() },
      onClear = { selectedKeys = emptySet() },
      sourceTitle = sourceTitle,
      headerHeight = with(density) { headerHeight.toDp() }.coerceAtLeast(48.dp),
      keyOf = { "${it.kind}:${it.key}" },
      load = load,
      actions = actions,
    )
  }
}
