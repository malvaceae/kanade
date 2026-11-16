@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package me.mallows.kanade.ui.library

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import me.mallows.kanade.playback.PlaySource
import androidx.compose.foundation.lazy.rememberLazyListState
import me.mallows.kanade.ui.common.initialOf
import me.mallows.kanade.ui.common.FastScroller
import me.mallows.kanade.ui.common.showMessage
import me.mallows.kanade.ui.common.LocalSnackbarHost
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalResources
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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

/** ライブラリのホームに並べる項目 */
enum class LibraryTab(@StringRes val label: Int, val icon: ImageVector) {
  TRACKS(R.string.tab_tracks, KanadeIcons.MusicNote),
  ALBUMS(R.string.tab_albums, KanadeIcons.Album),
  ARTISTS(R.string.tab_artists, KanadeIcons.Artist),
  FOLDERS(R.string.tab_folders, KanadeIcons.Folder),
  GENRES(R.string.tab_genres, KanadeIcons.Genre),
  PLAYLISTS(R.string.tab_playlists, KanadeIcons.ListView),
  QUEUE(R.string.queue_title, KanadeIcons.Queue),
  RECENTLY_ADDED(R.string.smart_recently_added, KanadeIcons.Plus),
  RECENTLY_PLAYED(R.string.smart_recently_played, KanadeIcons.Timer),
  MOST_PLAYED(R.string.smart_most_played, KanadeIcons.Repeat),
}

/** ライブラリのホーム。すべての曲・アルバム・アーティストなどの項目を並べ、押すとそれぞれの一覧を開く */
@Composable
fun LibraryHomeScreen(
  onOpen: (tab: LibraryTab, title: String) -> Unit,
  onSearch: () -> Unit,
  onLicenses: () -> Unit,
  bottomPadding: Dp,
  viewModel: LibraryViewModel = hiltViewModel(),
) {
  LaunchedEffect(Unit) { viewModel.scanOnce() }
  val tabSettings by viewModel.tabs.collectAsStateWithLifecycle()
  val visibleTabs = tabSettings.filter { it.visible }.map { it.tab }
  val tracks by viewModel.tracks.collectAsStateWithLifecycle()
  val albums by viewModel.albums.collectAsStateWithLifecycle()
  val artists by viewModel.groups(GroupKind.ARTIST).collectAsStateWithLifecycle()
  val folders by viewModel.groups(GroupKind.FOLDER).collectAsStateWithLifecycle()
  val genres by viewModel.groups(GroupKind.GENRE).collectAsStateWithLifecycle()
  var editingTabs by rememberSaveable { mutableStateOf(false) }
  var menuOpen by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val snackbar = LocalSnackbarHost.current
  val res = LocalResources.current
  val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
    if (uri != null) scope.launch {
      val ok = runCatching { viewModel.exportBackup(uri) }.isSuccess
      snackbar.showMessage(res.getString(if (ok) R.string.message_backup_exported else R.string.message_backup_failed))
    }
  }
  val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) scope.launch {
      runCatching { viewModel.restoreBackup(uri) }
        .onSuccess { r -> snackbar.showMessage(res.getString(R.string.message_backup_restored, r.playlists, r.presets, r.missingTracks)) }
        .onFailure { snackbar.showMessage(res.getString(R.string.message_backup_failed)) }
    }
  }

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
      Box {
        IconButton(onClick = { menuOpen = true }) { Icon(KanadeIcons.More, contentDescription = stringResource(R.string.action_more)) }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
          DropdownMenuItem(text = { Text(stringResource(R.string.tabs_title)) }, onClick = { menuOpen = false; editingTabs = true })
          DropdownMenuItem(
            text = { Text(stringResource(R.string.backup_export)) },
            onClick = { menuOpen = false; exportLauncher.launch("kanade-backup.json") },
          )
          DropdownMenuItem(
            text = { Text(stringResource(R.string.backup_restore)) },
            onClick = { menuOpen = false; restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
          )
          DropdownMenuItem(text = { Text(stringResource(R.string.licenses_title)) }, onClick = { menuOpen = false; onLicenses() })
        }
      }
    }
    LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding)) {
      items(visibleTabs, key = { it.name }) { t ->
        val count = when (t) {
          LibraryTab.TRACKS -> tracks?.size?.let { pluralStringResource(R.plurals.track_count, it, it) }
          LibraryTab.ALBUMS -> albums?.size?.let { pluralStringResource(R.plurals.item_count, it, it) }
          LibraryTab.ARTISTS -> artists?.size?.let { pluralStringResource(R.plurals.item_count, it, it) }
          LibraryTab.FOLDERS -> folders?.size?.let { pluralStringResource(R.plurals.item_count, it, it) }
          LibraryTab.GENRES -> genres?.size?.let { pluralStringResource(R.plurals.item_count, it, it) }
          else -> null
        }
        val title = stringResource(t.label)
        MenuRow(t.icon, title, count) { onOpen(t, title) }
      }
    }
  }
  if (editingTabs) {
    TabsSheet(tabs = tabSettings, onChange = viewModel::setTabs, onDismiss = { editingTabs = false })
  }
}

/** ホームの1項目 */
@Composable
private fun MenuRow(icon: ImageVector, title: String, count: String?, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().height(64.dp).clickable(onClick = onClick).padding(horizontal = 20.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    Text(
      title,
      style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
      modifier = Modifier.weight(1f).padding(start = 20.dp),
    )
    if (count != null) {
      Text(count, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

/** ホームの項目 (すべての曲・アルバム・アーティスト・フォルダー・ジャンル・プレイリスト) の一覧 */
@Composable
fun CategoryScreen(
  category: LibraryTab,
  playingMediaStoreId: Long?,
  actions: TrackActions,
  onOpenGroup: (kind: GroupKind, key: String?, title: String) -> Unit,
  onBack: () -> Unit,
  onSearch: () -> Unit,
  playlists: @Composable (bottomPadding: Dp) -> Unit,
  bottomPadding: Dp,
  viewModel: LibraryViewModel = hiltViewModel(),
) {
  Column(Modifier.fillMaxSize().statusBarsPadding()) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) { Icon(KanadeIcons.Back, contentDescription = stringResource(R.string.action_back)) }
      Text(
        stringResource(category.label),
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.weight(1f).padding(start = 4.dp),
      )
      IconButton(onClick = onSearch) { Icon(KanadeIcons.Search, contentDescription = stringResource(R.string.action_search)) }
    }
    Box(Modifier.weight(1f)) {
      when (category) {
        LibraryTab.TRACKS -> TracksTab(viewModel, playingMediaStoreId, actions, bottomPadding)
        LibraryTab.ALBUMS -> AlbumsTab(viewModel, onOpenGroup, actions, bottomPadding)
        LibraryTab.ARTISTS -> GroupsTab(viewModel, GroupKind.ARTIST, onOpenGroup, actions, bottomPadding)
        LibraryTab.FOLDERS -> GroupsTab(viewModel, GroupKind.FOLDER, onOpenGroup, actions, bottomPadding)
        LibraryTab.GENRES -> GroupsTab(viewModel, GroupKind.GENRE, onOpenGroup, actions, bottomPadding)
        LibraryTab.PLAYLISTS -> playlists(bottomPadding)
        // キューと自動のリストは、それぞれの画面で開く
        else -> Unit
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
      source = PlaySource("tracks", stringResource(R.string.tab_tracks)),
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
private fun AlbumsTab(viewModel: LibraryViewModel, onOpen: (GroupKind, String?, String) -> Unit, actions: TrackActions, bottomPadding: Dp) {
  var menuFor by remember { mutableStateOf<GroupTarget?>(null) }
  menuFor?.let { GroupMenuSheet(it, viewModel, actions) { menuFor = null } }
  val albums by viewModel.albums.collectAsStateWithLifecycle()
  val sort by viewModel.albumSort.collectAsStateWithLifecycle()
  val grid by viewModel.albumGrid.collectAsStateWithLifecycle()
  var showSort by rememberSaveable { mutableStateOf(false) }
  val list = albums ?: return Loading()
  val unknown = stringResource(R.string.unknown_album)
  val open = { a: AlbumSummary -> onOpen(GroupKind.ALBUM, a.albumId?.toString(), a.album ?: unknown) }
  val menu = { a: AlbumSummary -> menuFor = GroupTarget(GroupKind.ALBUM, a.albumId?.toString(), a.album ?: unknown) }
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
        Column(Modifier.combinedClickable(onClick = { open(album) }, onLongClick = { menu(album) })) {
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
          onLongClick = { menu(album) },
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
private fun GroupsTab(viewModel: LibraryViewModel, kind: GroupKind, onOpen: (GroupKind, String?, String) -> Unit, actions: TrackActions, bottomPadding: Dp) {
  var menuFor by remember { mutableStateOf<GroupTarget?>(null) }
  menuFor?.let { GroupMenuSheet(it, viewModel, actions) { menuFor = null } }
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
  val listState = rememberLazyListState()
  Box(Modifier.fillMaxSize()) {
  LazyColumn(state = listState, contentPadding = PaddingValues(bottom = bottomPadding)) {
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
        onLongClick = { menuFor = GroupTarget(kind, group.name, title) },
      )
    }
  }
  FastScroller(
    state = listState,
    label = { i -> initialOf(list.getOrNull(i - 1)?.let { it.displayName(kind) ?: unknown }) },
    modifier = Modifier.align(Alignment.TopEnd),
    bottomPadding = bottomPadding,
  )
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
