package me.mallows.kanade.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import me.mallows.kanade.GroupDetail
import me.mallows.kanade.R
import me.mallows.kanade.core.data.db.TrackEntity
import me.mallows.kanade.core.data.library.GroupKind
import me.mallows.kanade.core.data.library.LibraryRepository
import me.mallows.kanade.ui.icons.KanadeIcons
import me.mallows.kanade.ui.selection.SelectableTrackList
import me.mallows.kanade.ui.selection.TrackActions

@HiltViewModel(assistedFactory = GroupDetailViewModel.Factory::class)
class GroupDetailViewModel @AssistedInject constructor(
  @Assisted val key: GroupDetail,
  library: LibraryRepository,
) : ViewModel() {

  val tracks: StateFlow<List<TrackEntity>?> =
    library.groupTracks(GroupKind.valueOf(key.kind), key.key)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  @AssistedFactory
  interface Factory {
    fun create(key: GroupDetail): GroupDetailViewModel
  }
}

/** アルバム・アーティスト・フォルダー・ジャンルの中の曲 */
@Composable
fun GroupDetailScreen(
  key: GroupDetail,
  playingMediaStoreId: Long?,
  actions: TrackActions,
  onBack: () -> Unit,
  bottomPadding: Dp,
  viewModel: GroupDetailViewModel = hiltViewModel<GroupDetailViewModel, GroupDetailViewModel.Factory>(
    key = key.toString(),
    creationCallback = { it.create(key) },
  ),
) {
  val tracks by viewModel.tracks.collectAsStateWithLifecycle()
  Column(Modifier.fillMaxSize().statusBarsPadding()) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) { Icon(KanadeIcons.Back, contentDescription = stringResource(R.string.action_back)) }
    }
    val list = tracks ?: return@Column Loading()
    SelectableTrackList(
      tracks = list,
      playingMediaStoreId = playingMediaStoreId,
      actions = actions,
      bottomPadding = bottomPadding,
      numbered = key.kind == GroupKind.ALBUM.name,
      header = {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            key.title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
              "${pluralStringResource(R.plurals.track_count, list.size, list.size)} · ${list.sumOf { it.durationMs } / 60_000}${stringResource(R.string.minutes_suffix)}",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.weight(1f),
            )
            FilledTonalIconButton(onClick = { actions.shufflePlay(list) }, shape = CircleShape) {
              Icon(KanadeIcons.Shuffle, contentDescription = stringResource(R.string.action_shuffle_play), modifier = Modifier.size(20.dp))
            }
            Button(onClick = { actions.play(list, 0) }, modifier = Modifier.padding(start = 8.dp)) {
              Icon(KanadeIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
              Text(stringResource(R.string.action_play), modifier = Modifier.padding(start = 6.dp))
            }
          }
        }
      },
    )
  }
}
