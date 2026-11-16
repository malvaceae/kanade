package me.mallows.kanade.ui.library

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.mallows.kanade.R
import me.mallows.kanade.core.data.db.AlbumSummary
import me.mallows.kanade.core.data.db.GroupSummary
import me.mallows.kanade.core.data.library.GroupKind
import me.mallows.kanade.ui.common.GroupRow
import me.mallows.kanade.ui.common.ListHeader
import me.mallows.kanade.ui.common.TrackArtwork
import me.mallows.kanade.ui.icons.KanadeIcons
import me.mallows.kanade.ui.selection.SelectableTrackList
import me.mallows.kanade.ui.selection.TrackActions

/** ライブラリのタブ */
enum class LibraryTab(@StringRes val label: Int) {
  TRACKS(R.string.tab_tracks),
  ALBUMS(R.string.tab_albums),
  ARTISTS(R.string.tab_artists),
  FOLDERS(R.string.tab_folders),
  GENRES(R.string.tab_genres),
  PLAYLISTS(R.string.tab_playlists),
}

/** 曲・アルバム・アーティストなどをタブで切り替える画面 */
@Composable
fun LibraryHomeScreen(
  playingMediaStoreId: Long?,
  actions: TrackActions,
  onOpenGroup: (kind: GroupKind, key: String?, title: String) -> Unit,
  onSearch: () -> Unit,
  playlistsTab: @Composable (bottomPadding: Dp) -> Unit,
  bottomPadding: Dp,
  viewModel: LibraryViewModel = hiltViewModel(),
) {
  LaunchedEffect(Unit) { viewModel.scanOnce() }
  var tab by rememberSaveable { mutableIntStateOf(0) }

  Column(Modifier.fillMaxSize().statusBarsPadding()) {
    Row(
      modifier = Modifier.fillMaxWidth().height(56.dp).padding(start = 16.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        stringResource(R.string.library_title),
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
        modifier = Modifier.weight(1f),
      )
      IconButton(onClick = onSearch) { Icon(KanadeIcons.Search, contentDescription = stringResource(R.string.action_search)) }
    }
    PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp, divider = {}) {
      LibraryTab.entries.forEachIndexed { i, t ->
        Tab(
          selected = tab == i,
          onClick = { tab = i },
          text = { Text(stringResource(t.label)) },
          selectedContentColor = MaterialTheme.colorScheme.primary,
          unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Box(Modifier.weight(1f)) {
      when (LibraryTab.entries[tab]) {
        LibraryTab.TRACKS -> TracksTab(viewModel, playingMediaStoreId, actions, bottomPadding)
        LibraryTab.ALBUMS -> AlbumsTab(viewModel, onOpenGroup, bottomPadding)
        LibraryTab.ARTISTS -> GroupsTab(viewModel, GroupKind.ARTIST, onOpenGroup, bottomPadding)
        LibraryTab.FOLDERS -> GroupsTab(viewModel, GroupKind.FOLDER, onOpenGroup, bottomPadding)
        LibraryTab.GENRES -> GroupsTab(viewModel, GroupKind.GENRE, onOpenGroup, bottomPadding)
        LibraryTab.PLAYLISTS -> playlistsTab(bottomPadding)
      }
    }
  }
}

@Composable
private fun TracksTab(viewModel: LibraryViewModel, playingId: Long?, actions: TrackActions, bottomPadding: Dp) {
  val tracks by viewModel.tracks.collectAsStateWithLifecycle()
  val scanning by viewModel.scanning.collectAsStateWithLifecycle()
  val sort by viewModel.trackSort.collectAsStateWithLifecycle()
  var showSort by rememberSaveable { mutableStateOf(false) }
  val list = tracks
  when {
    list == null || (list.isEmpty() && scanning) -> Loading()
    list.isEmpty() -> Empty()
    else -> SelectableTrackList(
      tracks = list,
      playingMediaStoreId = playingId,
      actions = actions,
      bottomPadding = bottomPadding,
      header = {
        ListHeader(stringResource(R.string.library_summary, list.size, (list.sumOf { it.durationMs } / 60_000).toInt())) {
          SortButton(sort != null) { showSort = true }
        }
      },
    )
  }
  if (showSort) {
    SortSheet(TrackSort.entries.toTypedArray(), sort, TrackSort.TITLE,
      onSelect = { viewModel.setSort(SortCategory.TRACKS, it) }, onDismiss = { showSort = false })
  }
}

@Composable
private fun AlbumsTab(viewModel: LibraryViewModel, onOpen: (GroupKind, String?, String) -> Unit, bottomPadding: Dp) {
  val albums by viewModel.albums.collectAsStateWithLifecycle()
  val sort by viewModel.albumSort.collectAsStateWithLifecycle()
  val grid by viewModel.albumGrid.collectAsStateWithLifecycle()
  var showSort by rememberSaveable { mutableStateOf(false) }
  val list = albums ?: return Loading()
  val unknown = stringResource(R.string.unknown_album)
  val open = { a: AlbumSummary -> onOpen(GroupKind.ALBUM, a.albumId?.toString(), a.album ?: unknown) }
  val header = @Composable {
    ListHeader(pluralStringResource(R.plurals.album_count, list.size, list.size)) {
      IconButton(onClick = { viewModel.setAlbumGrid(!grid) }) {
        Icon(if (grid) KanadeIcons.ListView else KanadeIcons.Grid, contentDescription = stringResource(R.string.action_toggle_view))
      }
      SortButton(sort != null) { showSort = true }
    }
  }
  if (grid) {
    LazyVerticalGrid(
      columns = GridCells.Adaptive(150.dp),
      contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPadding),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { header() }
      items(list, key = { it.albumId ?: -1L }) { album ->
        Column(Modifier.clickable { open(album) }) {
          androidx.compose.foundation.layout.BoxWithConstraints {
            TrackArtwork(uri = album.artUri, size = maxWidth, cornerRadius = 10.dp)
          }
          Spacer(Modifier.height(6.dp))
          Text(album.album ?: unknown, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(album.artist ?: stringResource(R.string.unknown_artist), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  } else {
    LazyColumn(contentPadding = PaddingValues(bottom = bottomPadding)) {
      item { header() }
      items(list, key = { it.albumId ?: -1L }) { album ->
        GroupRow(
          title = album.album ?: unknown,
          subtitle = "${album.artist ?: stringResource(R.string.unknown_artist)} · ${pluralStringResource(R.plurals.track_count, album.trackCount, album.trackCount)}",
          artUri = album.artUri,
          onClick = { open(album) },
        )
      }
    }
  }
  if (showSort) {
    SortSheet(AlbumSort.entries.toTypedArray(), sort, AlbumSort.TITLE,
      onSelect = { viewModel.setSort(SortCategory.ALBUMS, it) }, onDismiss = { showSort = false })
  }
}

@Composable
private fun GroupsTab(viewModel: LibraryViewModel, kind: GroupKind, onOpen: (GroupKind, String?, String) -> Unit, bottomPadding: Dp) {
  val groups by viewModel.groups(kind).collectAsStateWithLifecycle()
  val sort by viewModel.groupSort(kind).collectAsStateWithLifecycle()
  var showSort by rememberSaveable(kind) { mutableStateOf(false) }
  val list = groups ?: return Loading()
  val unknown = stringResource(
    when (kind) {
      GroupKind.ARTIST -> R.string.unknown_artist
      GroupKind.GENRE -> R.string.unknown_genre
      else -> R.string.unknown_folder
    }
  )
  LazyColumn(contentPadding = PaddingValues(bottom = bottomPadding)) {
    item {
      ListHeader(pluralStringResource(R.plurals.item_count, list.size, list.size)) {
        SortButton(sort != null) { showSort = true }
      }
    }
    items(list, key = { it.name ?: "" }) { group ->
      val title = group.displayName(kind) ?: unknown
      GroupRow(
        title = title,
        subtitle = groupSubtitle(kind, group),
        artUri = group.artUri,
        onClick = { onOpen(kind, group.name, title) },
      )
    }
  }
  if (showSort) {
    SortSheet(GroupSort.entries.toTypedArray(), sort, GroupSort.NAME,
      onSelect = { viewModel.setSort(SortCategory.group(kind), it) }, onDismiss = { showSort = false })
  }
}

/** フォルダーは「Music/Album/」を「Music › Album」のように見せる */
private fun GroupSummary.displayName(kind: GroupKind): String? =
  if (kind == GroupKind.FOLDER) name?.trimEnd('/')?.replace("/", " › ") else name

@Composable
private fun groupSubtitle(kind: GroupKind, group: GroupSummary): String {
  val tracks = pluralStringResource(R.plurals.track_count, group.trackCount, group.trackCount)
  return if (kind == GroupKind.ARTIST) {
    "${pluralStringResource(R.plurals.album_count, group.albumCount, group.albumCount)} · $tracks"
  } else {
    "$tracks · ${group.durationMs / 60_000}${stringResource(R.string.minutes_suffix)}"
  }
}

@Composable
fun SortButton(active: Boolean, onClick: () -> Unit) {
  IconButton(onClick = onClick) {
    Icon(
      KanadeIcons.Sort,
      contentDescription = stringResource(R.string.action_sort),
      tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Composable
fun Loading() {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun Empty() {
  Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
    Text(stringResource(R.string.library_empty), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.library_empty_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
