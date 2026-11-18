package me.mallows.kanade.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import me.mallows.kanade.playback.PlaySource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import me.mallows.kanade.R
import me.mallows.kanade.core.data.db.TrackEntity
import me.mallows.kanade.core.data.library.LibraryRepository
import me.mallows.kanade.ui.common.ListHeader
import me.mallows.kanade.ui.icons.KanadeIcons
import me.mallows.kanade.ui.selection.SelectableTrackList
import me.mallows.kanade.ui.selection.TrackActions

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
  private val savedState: SavedStateHandle,
  library: LibraryRepository,
) : ViewModel() {

  val query: StateFlow<String> = savedState.getStateFlow("query", "")

  /** 入力が止まって0.25秒後に検索する。空のときは何も出さない */
  val results: StateFlow<List<TrackEntity>> = query
    .debounce(250)
    .flatMapLatest { q -> if (q.isBlank()) flowOf(emptyList()) else library.search(q.trim()) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  fun setQuery(value: String) {
    savedState["query"] = value
  }
}

/** タイトル・アーティスト・アルバムを横断して探す */
@Composable
fun SearchScreen(
  playingMediaStoreId: Long?,
  actions: TrackActions,
  onBack: () -> Unit,
  bottomPadding: Dp,
  viewModel: SearchViewModel = hiltViewModel(),
) {
  val query by viewModel.query.collectAsStateWithLifecycle()
  val results by viewModel.results.collectAsStateWithLifecycle()
  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) { focus.requestFocus() }

  Column(Modifier.fillMaxSize().statusBarsPadding()) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) { Icon(KanadeIcons.Back, contentDescription = stringResource(R.string.action_back)) }
      TextField(
        value = query,
        onValueChange = viewModel::setQuery,
        // 案内は1行に収める (狭い画面では末尾を省く)
        placeholder = { Text(stringResource(R.string.search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        modifier = Modifier.weight(1f).focusRequester(focus),
        colors = TextFieldDefaults.colors(
          focusedContainerColor = Color.Transparent,
          unfocusedContainerColor = Color.Transparent,
        ),
        trailingIcon = {
          if (query.isNotEmpty()) {
            IconButton(onClick = { viewModel.setQuery("") }) {
              Icon(KanadeIcons.Close, contentDescription = stringResource(R.string.action_clear))
            }
          }
        },
      )
    }
    when {
      query.isBlank() -> Unit
      results.isEmpty() -> Text(
        stringResource(R.string.search_no_results),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp),
      )
      else -> SelectableTrackList(
        tracks = results,
        playingMediaStoreId = playingMediaStoreId,
        actions = actions,
        // キーボードが出ている間は、最後の曲までキーボードの上にスクロールできるようにする
        bottomPadding = maxOf(bottomPadding, WindowInsets.ime.asPaddingValues().calculateBottomPadding() + 16.dp),
        source = PlaySource("search:$query", stringResource(R.string.action_search)),
        header = { ListHeader(stringResource(R.string.library_summary, results.size, (results.sumOf { it.durationMs } / 60_000).toInt())) },
      )
    }
  }
}
