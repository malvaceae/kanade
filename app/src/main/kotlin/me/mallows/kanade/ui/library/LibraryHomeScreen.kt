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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import me.mallows.kanade.ui.common.SelectionCheck
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import me.mallows.kanade.ui.selection.navigationBarHeight
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.layout
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
import me.mallows.kanade.core.domain.folder.ScanFolder
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
  onLicenses: () -> Unit,
  onScanFolders: () -> Unit,
  bottomPadding: Dp,
  viewModel: LibraryViewModel = hiltViewModel(),
) {
  LaunchedEffect(Unit) { viewModel.scanIfFirstTime() }
  val tabSettings by viewModel.tabs.collectAsStateWithLifecycle()
  val visibleTabs = tabSettings.filter { it.visible }.map { it.tab }
  val tracks by viewModel.tracks.collectAsStateWithLifecycle()
  val albums by viewModel.albums.collectAsStateWithLifecycle()
  val artists by viewModel.groups(GroupKind.ARTIST).collectAsStateWithLifecycle()
  val folders by viewModel.groups(GroupKind.FOLDER).collectAsStateWithLifecycle()
  val genres by viewModel.groups(GroupKind.GENRE).collectAsStateWithLifecycle()
  var editingTabs by rememberSaveable { mutableStateOf(false) }
  var menuOpen by remember { mutableStateOf(false) }
  val scanning by viewModel.scanning.collectAsStateWithLifecycle()
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
      Box {
        IconButton(onClick = { menuOpen = true }) { Icon(KanadeIcons.More, contentDescription = stringResource(R.string.action_more)) }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
          DropdownMenuItem(text = { Text(stringResource(R.string.action_rescan)) }, onClick = { menuOpen = false; viewModel.rescan() }, enabled = !scanning)
          DropdownMenuItem(text = { Text(stringResource(R.string.scan_folders_title)) }, onClick = { menuOpen = false; onScanFolders() })
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

/** ホームの項目 (すべての曲・アルバム・アーティスト・フォルダ・ジャンル・プレイリスト) の一覧 */
@Composable
fun CategoryScreen(
  category: LibraryTab,
  playingMediaStoreId: Long?,
  actions: TrackActions,
  onOpenGroup: (kind: GroupKind, key: String?, title: String) -> Unit,
  onBack: () -> Unit,
  playlists: @Composable (bottomPadding: Dp) -> Unit,
  bottomPadding: Dp,
  /** 曲がないときの「フォルダを選ぶ」 */
  onScanFolders: () -> Unit,
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
    }
    Box(Modifier.weight(1f)) {
      when (category) {
        LibraryTab.TRACKS -> TracksTab(viewModel, playingMediaStoreId, actions, bottomPadding, onScanFolders)
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
private fun TracksTab(viewModel: LibraryViewModel, playingId: Long?, actions: TrackActions, bottomPadding: Dp, onScanFolders: () -> Unit) {
  val tracks by viewModel.tracks.collectAsStateWithLifecycle()
  val scanning by viewModel.scanning.collectAsStateWithLifecycle()
  val sort by viewModel.trackSort.collectAsStateWithLifecycle()
  var showSort by rememberSaveable { mutableStateOf(false) }
  val list = tracks
  val source = PlaySource("tracks", stringResource(R.string.tab_tracks))
  when {
    list == null || (list.isEmpty() && scanning) -> Loading()
    list.isEmpty() -> Empty(onScanFolders = onScanFolders, onRescan = viewModel::rescan)
    else -> SelectableTrackList(
      tracks = list,
      playingMediaStoreId = playingId,
      actions = actions,
      bottomPadding = bottomPadding,
      source = source,
      header = {
        // ほかの曲の一覧 (アルバム・プレイリストなど) と同じく、シャッフル再生と再生のボタンを置く
        ListHeader(stringResource(R.string.library_summary, list.size, (list.sumOf { it.durationMs } / 60_000).toInt())) {
          SortButton(sort != null) { showSort = true }
          FilledTonalIconButton(onClick = { actions.shufflePlay(list, source) }, shape = CircleShape) {
            Icon(KanadeIcons.Shuffle, contentDescription = stringResource(R.string.action_shuffle_play), modifier = Modifier.size(20.dp))
          }
          Button(onClick = { actions.play(list, 0, source) }, modifier = Modifier.padding(start = 8.dp, end = 12.dp)) {
            Icon(KanadeIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.action_play), modifier = Modifier.padding(start = 6.dp))
          }
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
  val albums by viewModel.albums.collectAsStateWithLifecycle()
  val sort by viewModel.albumSort.collectAsStateWithLifecycle()
  val grid by viewModel.albumGrid.collectAsStateWithLifecycle()
  var showSort by rememberSaveable { mutableStateOf(false) }
  // 長押しで選択を始める。選択中は押すたびに選択を切り替える
  var selectedKeys by rememberSaveable { mutableStateOf(setOf<String>()) }
  val list = albums ?: return Loading()
  val unknown = stringResource(R.string.unknown_album)
  val keyOf = { a: AlbumSummary -> a.albumId?.toString() ?: "" }
  val targetOf = { a: AlbumSummary -> GroupTarget(GroupKind.ALBUM, a.albumId?.toString(), a.album ?: unknown) }
  val selecting = selectedKeys.isNotEmpty()
  val toggle = { a: AlbumSummary -> val k = keyOf(a); selectedKeys = if (k in selectedKeys) selectedKeys - k else selectedKeys + k }
  val click = { a: AlbumSummary -> if (selecting) toggle(a) else onOpen(GroupKind.ALBUM, a.albumId?.toString(), a.album ?: unknown) }
  val padding = if (selecting) 88.dp + navigationBarHeight() else bottomPadding
  val header = @Composable { modifier: Modifier ->
    ListHeader(pluralStringResource(R.plurals.item_count, list.size, list.size), modifier) {
      IconButton(onClick = { viewModel.setAlbumGrid(!grid) }) {
        Icon(if (grid) KanadeIcons.ListView else KanadeIcons.Grid, contentDescription = stringResource(R.string.action_toggle_view))
      }
      SortButton(sort != null) { showSort = true }
    }
  }
  Box(Modifier.fillMaxSize()) {
    if (grid) {
      LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(start = GRID_SIDE_PADDING, end = GRID_SIDE_PADDING, bottom = padding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // 格子の左右の余白を見出しには付けず、ほかの画面と同じ位置にボタンを置く
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
          header(Modifier.layout { measurable, constraints ->
            val extra = (GRID_SIDE_PADDING * 2).roundToPx()
            val width = constraints.maxWidth + extra
            val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
            layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
          })
        }
        items(list, key = { it.albumId ?: -1L }) { album ->
          val isSelected = keyOf(album) in selectedKeys
          Column(Modifier.combinedClickable(onClick = { click(album) }, onLongClick = { toggle(album) })) {
            androidx.compose.foundation.layout.BoxWithConstraints {
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
            Text(album.album ?: unknown, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(album.artist ?: stringResource(R.string.unknown_artist), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }
    } else {
      LazyColumn(contentPadding = PaddingValues(bottom = padding)) {
        item { header(Modifier) }
        items(list, key = { it.albumId ?: -1L }) { album ->
          GroupRow(
            title = album.album ?: unknown,
            subtitle = "${album.artist ?: stringResource(R.string.unknown_artist)} · ${pluralStringResource(R.plurals.track_count, album.trackCount, album.trackCount)}",
            artUri = album.artUri,
            onClick = { click(album) },
            onLongClick = { toggle(album) },
            selected = if (selecting) keyOf(album) in selectedKeys else null,
          )
        }
      }
    }
    GroupSelectionBars(
      selected = list.filter { keyOf(it) in selectedKeys }.map(targetOf),
      allSelected = selectedKeys.size == list.size,
      onSelectAll = { selectedKeys = if (selectedKeys.size == list.size) emptySet() else list.map(keyOf).toSet() },
      onClear = { selectedKeys = emptySet() },
      sourceTitle = stringResource(R.string.tab_albums),
      keyOf = { "${it.kind}:${it.key}" },
      load = { viewModel.groupTracks(it.kind, it.key) },
      actions = actions,
    )
  }
  if (showSort) {
    SortSheet(AlbumSort.entries.toTypedArray(), sort, AlbumSort.TITLE,
      onSelect = { viewModel.setSort(SortCategory.ALBUMS, it) }, onDismiss = { showSort = false })
  }
}

@Composable
private fun GroupsTab(viewModel: LibraryViewModel, kind: GroupKind, onOpen: (GroupKind, String?, String) -> Unit, actions: TrackActions, bottomPadding: Dp) {
  val groups by viewModel.groups(kind).collectAsStateWithLifecycle()
  var selectedKeys by rememberSaveable(kind) { mutableStateOf(setOf<String>()) }
  val selecting = selectedKeys.isNotEmpty()
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
  LazyColumn(state = listState, contentPadding = PaddingValues(bottom = if (selecting) 88.dp + navigationBarHeight() else bottomPadding)) {
    item {
      ListHeader(pluralStringResource(R.plurals.item_count, list.size, list.size)) {
        SortButton(sort != null) { showSort = true }
      }
    }
    items(list, key = { it.name ?: "" }) { group ->
      val title = group.displayName(kind, viewModel.volumeLabels) ?: unknown
      val k = group.name ?: ""
      val toggle = { selectedKeys = if (k in selectedKeys) selectedKeys - k else selectedKeys + k }
      GroupRow(
        title = title,
        subtitle = groupSubtitle(kind, group),
        artUri = group.artUri,
        onClick = { if (selecting) toggle() else onOpen(kind, group.name, title) },
        onLongClick = toggle,
        selected = if (selecting) k in selectedKeys else null,
      )
    }
  }
  if (!selecting) {
    FastScroller(
      state = listState,
      label = { i -> initialOf(list.getOrNull(i - 1)?.let { it.displayName(kind, viewModel.volumeLabels) ?: unknown }) },
      modifier = Modifier.align(Alignment.TopEnd),
      bottomPadding = bottomPadding,
    )
  }
  GroupSelectionBars(
    selected = list.filter { (it.name ?: "") in selectedKeys }
      .map { GroupTarget(kind, it.name, it.displayName(kind, viewModel.volumeLabels) ?: unknown) },
    allSelected = selectedKeys.size == list.size,
    onSelectAll = { selectedKeys = if (selectedKeys.size == list.size) emptySet() else list.map { it.name ?: "" }.toSet() },
    onClear = { selectedKeys = emptySet() },
    sourceTitle = stringResource(
      when (kind) {
        GroupKind.ARTIST -> R.string.tab_artists
        GroupKind.GENRE -> R.string.tab_genres
        else -> R.string.tab_folders
      }
    ),
    keyOf = { "${it.kind}:${it.key}" },
      load = { viewModel.groupTracks(it.kind, it.key) },
    actions = actions,
  )
  }
  if (showSort) {
    SortSheet(GroupSort.entries.toTypedArray(), sort, GroupSort.NAME,
      onSelect = { viewModel.setSort(SortCategory.group(kind), it) }, onDismiss = { showSort = false })
  }
}

/**
 * フォルダは「Music/Album/」を「Music › Album」のように見せる。nameは「ボリューム名:フォルダ」。
 * 内部ストレージ以外 (SDカードなど) は、先頭にストレージの名前を付ける
 */
private fun GroupSummary.displayName(kind: GroupKind, volumeLabels: Map<String, String>): String? {
  if (kind != GroupKind.FOLDER) return name
  val key = name ?: return null
  val volume = key.substringBefore(':')
  val path = key.substringAfter(':').trimEnd('/').replace("/", " › ")
  val storage = volume.takeIf { it.isNotEmpty() && it != ScanFolder.PRIMARY }?.let { volumeLabels[it] ?: it }
  return listOfNotNull(storage, path.ifEmpty { null }).joinToString(" › ").ifEmpty { null }
}

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

/** ライブラリが空のとき。理由と、次にすること (フォルダを選ぶ・再スキャン) をボタンで出す */
@Composable
private fun Empty(onScanFolders: () -> Unit, onRescan: () -> Unit) {
  Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
    Text(stringResource(R.string.library_empty), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.library_empty_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(onClick = onScanFolders) { Text(stringResource(R.string.action_choose_folders)) }
      OutlinedButton(onClick = onRescan) { Text(stringResource(R.string.action_rescan)) }
    }
  }
}

/** アルバムの格子の左右の余白 */
private val GRID_SIDE_PADDING = 12.dp
