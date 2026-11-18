package me.mallows.kanade.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import me.mallows.kanade.ui.selection.dragToSelect
import androidx.compose.runtime.getValue
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import me.mallows.kanade.ArtistDetail
import me.mallows.kanade.R
import me.mallows.kanade.core.data.db.AlbumSummary
import me.mallows.kanade.core.data.db.TrackEntity
import me.mallows.kanade.core.data.library.GroupKind
import me.mallows.kanade.core.data.library.LibraryRepository
import me.mallows.kanade.playback.PlaySource
import me.mallows.kanade.ui.common.GroupRow
import me.mallows.kanade.ui.icons.KanadeIcons
import me.mallows.kanade.ui.selection.TrackActions
import me.mallows.kanade.ui.selection.navigationBarHeight

@HiltViewModel(assistedFactory = ArtistDetailViewModel.Factory::class)
class ArtistDetailViewModel @AssistedInject constructor(
  @Assisted val key: ArtistDetail,
  library: LibraryRepository,
) : ViewModel() {

  val albums: StateFlow<List<AlbumSummary>?> =
    library.artistAlbums(key.name).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  /** アーティストの全曲 (アルバムの順)。再生・シャッフルに使う */
  val tracks: StateFlow<List<TrackEntity>?> =
    library.groupTracks(GroupKind.ARTIST, key.name).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  @AssistedFactory
  interface Factory {
    fun create(key: ArtistDetail): ArtistDetailViewModel
  }
}

/** アーティストのアルバムの一覧。上の「再生」「シャッフル」でアーティストの全曲を流す */
@Composable
fun ArtistDetailScreen(
  key: ArtistDetail,
  actions: TrackActions,
  onOpenGroup: (GroupKind, String?, String) -> Unit,
  onBack: () -> Unit,
  bottomPadding: Dp,
  viewModel: ArtistDetailViewModel = hiltViewModel<ArtistDetailViewModel, ArtistDetailViewModel.Factory>(
    key = key.toString(),
    creationCallback = { it.create(key) },
  ),
  library: LibraryViewModel = hiltViewModel(),
) {
  val albums by viewModel.albums.collectAsStateWithLifecycle()
  val tracks by viewModel.tracks.collectAsStateWithLifecycle()
  var selectedKeys by rememberSaveable { mutableStateOf(setOf<String>()) }
  /** 再生ボタンの行の高さ (px)。選択中の上のバーをこの高さにする */
  var headerHeight by remember { mutableIntStateOf(0) }
  val selecting = selectedKeys.isNotEmpty()
  val unknown = stringResource(R.string.unknown_album)
  Column(Modifier.fillMaxSize().statusBarsPadding()) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) { Icon(KanadeIcons.Back, contentDescription = stringResource(R.string.action_back)) }
      Text(
        key.title,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f).padding(start = 4.dp, end = 12.dp),
      )
    }
    val list = albums ?: return@Column Loading()
    val all = tracks.orEmpty()
    val source = PlaySource("group:${GroupKind.ARTIST.name}:${key.name}", key.title)
    Box(Modifier.weight(1f)) {
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    LazyColumn(
      state = listState,
      contentPadding = PaddingValues(bottom = if (selecting) 88.dp + navigationBarHeight() else bottomPadding),
      // 長押ししたまま指を動かすと、通ったアルバムまでをまとめて選ぶ (曲の一覧と同じ)
      modifier = Modifier.dragToSelect(
        state = listState,
        keys = list.map { it.albumId?.toString() ?: "" },
        currentSelection = { selectedKeys },
        onStart = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
        onChange = { selectedKeys = it },
      ),
    ) {
      item {
        Row(Modifier.fillMaxWidth().onSizeChanged { headerHeight = it.height }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(
            "${pluralStringResource(R.plurals.album_count, list.size, list.size)} · ${pluralStringResource(R.plurals.track_count, all.size, all.size)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
          )
          FilledTonalIconButton(onClick = { actions.shufflePlay(all, source) }, enabled = all.isNotEmpty(), shape = CircleShape) {
            Icon(KanadeIcons.Shuffle, contentDescription = stringResource(R.string.action_shuffle_play), modifier = Modifier.size(20.dp))
          }
          Button(onClick = { actions.play(all, 0, source) }, enabled = all.isNotEmpty(), modifier = Modifier.padding(start = 8.dp)) {
            Icon(KanadeIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.action_play), modifier = Modifier.padding(start = 6.dp))
          }
        }
      }
      items(list, key = { it.albumId?.toString() ?: "" }) { album ->
        val title = album.album ?: unknown
        val sub = listOfNotNull(album.year?.toString(), pluralStringResource(R.plurals.track_count, album.trackCount, album.trackCount))
        val k = album.albumId?.toString() ?: ""
        val toggle = { selectedKeys = if (k in selectedKeys) selectedKeys - k else selectedKeys + k }
        GroupRow(
          title = title,
          subtitle = sub.joinToString(" · "),
          artUri = album.artUri,
          onClick = { if (selecting) toggle() else onOpenGroup(GroupKind.ALBUM, album.albumId?.toString(), title) },
          selected = if (selecting) k in selectedKeys else null,
        )
      }
    }
    GroupSelectionBars(
      selected = list.filter { (it.albumId?.toString() ?: "") in selectedKeys }
        .map { GroupTarget(GroupKind.ALBUM, it.albumId?.toString(), it.album ?: unknown) },
      allSelected = selectedKeys.size == list.size,
      onSelectAll = { selectedKeys = if (selectedKeys.size == list.size) emptySet() else list.map { it.albumId?.toString() ?: "" }.toSet() },
      onClear = { selectedKeys = emptySet() },
      sourceTitle = key.title,
      headerHeight = with(density) { headerHeight.toDp() }.coerceAtLeast(48.dp),
      keyOf = { "${it.kind}:${it.key}" },
      load = { library.groupTracks(it.kind, it.key) },
      actions = actions,
    )
    }
  }
}
