package me.mallows.kanade

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import me.mallows.kanade.ui.library.LibraryScreen
import me.mallows.kanade.ui.permission.AudioPermissionGate

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Library)

  AudioPermissionGate {
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
            playingMediaStoreId = null,
            onPlay = { _, _ -> },
            bottomPadding = 16.dp,
          )
        }
      },
    )
  }
}
