package me.mallows.kanade

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import me.mallows.kanade.ui.library.LibraryScreen
import me.mallows.kanade.ui.permission.AudioPermissionGate
import me.mallows.kanade.ui.player.MiniPlayer
import me.mallows.kanade.ui.player.PlayerScreen
import me.mallows.kanade.ui.player.PlayerViewModel

@Composable
fun MainNavigation(playerViewModel: PlayerViewModel = hiltViewModel()) {
  val backStack = rememberNavBackStack(Library)
  val player by playerViewModel.state.collectAsStateWithLifecycle()

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
            LibraryScreen(
              playingMediaStoreId = player.mediaStoreId,
              onPlay = playerViewModel::play,
              bottomPadding = if (player.hasMedia) 96.dp else 16.dp,
            )
          }
          entry<Player> {
            PlayerScreen(
              state = player,
              onClose = { backStack.removeLastOrNull() },
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
      if (backStack.lastOrNull() != Player) {
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
    }
  }
}
