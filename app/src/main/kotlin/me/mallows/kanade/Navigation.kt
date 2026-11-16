package me.mallows.kanade

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import me.mallows.kanade.core.data.db.TrackEntity
import me.mallows.kanade.ui.common.LocalBottomBarActive
import me.mallows.kanade.ui.common.LocalSnackbarHost
import me.mallows.kanade.ui.library.GroupDetailScreen
import me.mallows.kanade.ui.library.LibraryHomeScreen
import me.mallows.kanade.ui.permission.AudioPermissionGate
import me.mallows.kanade.ui.player.MiniPlayer
import me.mallows.kanade.ui.playlist.AddToPlaylistSheet
import me.mallows.kanade.ui.playlist.PlaylistDetailScreen
import me.mallows.kanade.ui.playlist.PlaylistsTab
import me.mallows.kanade.ui.queue.QueueScreen
import me.mallows.kanade.ui.player.PlayerScreen
import me.mallows.kanade.ui.player.PlayerViewModel
import me.mallows.kanade.ui.selection.TrackActions

@Composable
fun MainNavigation(playerViewModel: PlayerViewModel = hiltViewModel()) {
  val backStack = rememberNavBackStack(Library)
  val player by playerViewModel.state.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  val bottomBarActive = remember { mutableStateOf(false) }
  /** プレイリストへの追加を待っている曲 */
  var pendingAdd by remember { mutableStateOf<List<TrackEntity>?>(null) }

  val actions = remember(playerViewModel) {
    object : TrackActions {
      override fun play(tracks: List<TrackEntity>, index: Int) = playerViewModel.play(tracks, index)
      override fun playNext(tracks: List<TrackEntity>) = playerViewModel.playNext(tracks)
      override fun addToQueue(tracks: List<TrackEntity>) = playerViewModel.addToQueue(tracks)
      override fun addToPlaylist(tracks: List<TrackEntity>) {
        pendingAdd = tracks
      }
    }
  }
  val top = backStack.lastOrNull()
  val showMini = player.hasMedia && top != Player && top != Queue && !bottomBarActive.value
  val bottomPadding = if (player.hasMedia) 96.dp else 16.dp

  CompositionLocalProvider(LocalSnackbarHost provides snackbar, LocalBottomBarActive provides bottomBarActive) {
    AudioPermissionGate {
      Box(Modifier.fillMaxSize()) {
        NavDisplay(
          backStack = backStack,
          onBack = { backStack.removeLastOrNull() },
          entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
          ),
          entryProvider = entryProvider {
            entry<Library> {
              LibraryHomeScreen(
                playingMediaStoreId = player.mediaStoreId,
                actions = actions,
                onOpenGroup = { kind, key, title -> backStack.add(GroupDetail(kind.name, key, title)) },
                onSearch = { },
                playlistsTab = { padding ->
                  PlaylistsTab(onOpen = { id, _ -> backStack.add(PlaylistDetail(id)) }, bottomPadding = padding)
                },
                bottomPadding = bottomPadding,
              )
            }
            entry<PlaylistDetail> { key ->
              PlaylistDetailScreen(
                playlistId = key.id,
                playingMediaStoreId = player.mediaStoreId,
                actions = actions,
                onBack = { backStack.removeLastOrNull() },
                bottomPadding = bottomPadding,
              )
            }
            entry<GroupDetail> { key ->
              GroupDetailScreen(
                key = key,
                playingMediaStoreId = player.mediaStoreId,
                actions = actions,
                onBack = { backStack.removeLastOrNull() },
                bottomPadding = bottomPadding,
              )
            }
            entry<Queue> {
              QueueScreen(onBack = { backStack.removeLastOrNull() })
            }
            entry<Player> {
              PlayerScreen(
                state = player,
                onClose = { backStack.removeLastOrNull() },
                onOpenQueue = { backStack.add(Queue) },
                onTogglePlay = playerViewModel::togglePlayPause,
                onNext = playerViewModel::next,
                onPrevious = playerViewModel::previous,
                onSeek = playerViewModel::seekTo,
                onToggleShuffle = playerViewModel::toggleShuffle,
                onCycleRepeat = playerViewModel::cycleRepeat,
              )
            }
          },
        )
        if (showMini) {
          MiniPlayer(
            state = player,
            onOpen = { backStack.add(Player) },
            onTogglePlay = playerViewModel::togglePlayPause,
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .navigationBarsPadding()
              .padding(bottom = 8.dp),
          )
        }
        pendingAdd?.let { tracks ->
          AddToPlaylistSheet(tracks = tracks, onDismiss = { pendingAdd = null })
        }
        SnackbarHost(
          hostState = snackbar,
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = if (showMini || bottomBarActive.value) 84.dp else 8.dp),
        )
      }
    }
  }
}
