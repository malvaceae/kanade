package me.mallows.kanade

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import me.mallows.kanade.ui.common.showMessage
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
import me.mallows.kanade.ui.common.LocalIsPlaying
import me.mallows.kanade.ui.common.LocalPlaySource
import me.mallows.kanade.ui.common.LocalShowMessage
import me.mallows.kanade.playback.PlaySource
import me.mallows.kanade.ui.common.LocalSnackbarHost
import me.mallows.kanade.ui.equalizer.EqualizerScreen
import me.mallows.kanade.ui.library.GroupDetailScreen
import me.mallows.kanade.ui.library.LibraryHomeScreen
import me.mallows.kanade.core.data.library.GroupKind
import me.mallows.kanade.ui.library.LibraryTab
import me.mallows.kanade.ui.library.CategoryScreen
import me.mallows.kanade.ui.about.LicensesScreen
import me.mallows.kanade.ui.permission.AudioPermissionGate
import me.mallows.kanade.ui.player.MiniPlayer
import me.mallows.kanade.ui.playlist.AddToPlaylistSheet
import me.mallows.kanade.ui.playlist.PlaylistDetailScreen
import me.mallows.kanade.ui.playlist.PlaylistsTab
import me.mallows.kanade.ui.queue.QueueScreen
import me.mallows.kanade.ui.search.SearchScreen
import me.mallows.kanade.ui.player.PlayerScreen
import me.mallows.kanade.ui.player.PlayerViewModel
import me.mallows.kanade.ui.selection.TrackActions
import me.mallows.kanade.ui.selection.navigationBarHeight

@Composable
fun MainNavigation(
  openPlayerRequest: MutableState<Boolean> = remember { mutableStateOf(false) },
  playerViewModel: PlayerViewModel = hiltViewModel(),
) {
  val backStack = rememberNavBackStack(Library)
  // 通知などから開かれたら、プレイヤー画面を出す
  LaunchedEffect(openPlayerRequest.value) {
    if (openPlayerRequest.value) {
      if (backStack.lastOrNull() != Player) backStack.add(Player)
      openPlayerRequest.value = false
    }
  }
  val player by playerViewModel.state.collectAsStateWithLifecycle()
  val sleep by playerViewModel.sleep.collectAsStateWithLifecycle()
  val lyrics by playerViewModel.lyrics.collectAsStateWithLifecycle()
  val playSource by playerViewModel.source.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  /** 画面をまたいで続く処理 (シートを閉じた後のメッセージなど) */
  val appScope = rememberCoroutineScope()
  val bottomBarActive = remember { mutableStateOf(false) }
  /** プレイリストへの追加を待っている曲 */
  var pendingAdd by remember { mutableStateOf<List<TrackEntity>?>(null) }

  val actions = remember(playerViewModel) {
    object : TrackActions {
      override fun play(tracks: List<TrackEntity>, index: Int, source: PlaySource?) = playerViewModel.play(tracks, index, source)
      override fun shufflePlay(tracks: List<TrackEntity>, source: PlaySource?) = playerViewModel.shufflePlay(tracks, source)
      override fun openPlayer() {
        if (backStack.lastOrNull() != Player) backStack.add(Player)
      }
      override fun playNext(tracks: List<TrackEntity>) = playerViewModel.playNext(tracks)
      override fun addToQueue(tracks: List<TrackEntity>) = playerViewModel.addToQueue(tracks)
      override fun addToPlaylist(tracks: List<TrackEntity>) {
        pendingAdd = tracks
      }
    }
  }
  val top = backStack.lastOrNull()
  // 簡易プレイヤーは一覧の画面に出す。プレイヤー・EQ・ライセンスの画面には出さない
  val showMini = player.hasMedia && top != Player && top != Equalizer && top != Licenses && !bottomBarActive.value
  // 一覧の下の余白: ミニプレイヤー (高さ64・下8) とナビゲーションバーの分に、曲どうしの間隔と同じくらいの隙間 (12) を足す
  val bottomPadding = (if (player.hasMedia) 84.dp else 24.dp) + navigationBarHeight()

  CompositionLocalProvider(
    LocalSnackbarHost provides snackbar,
    LocalBottomBarActive provides bottomBarActive,
    LocalIsPlaying provides player.isPlaying,
    LocalPlaySource provides playSource,
    LocalShowMessage provides { message: String -> appScope.launch { snackbar.showMessage(message) }; Unit },
  ) {
    AudioPermissionGate {
      Box(Modifier.fillMaxSize()) {
        NavDisplay(
          backStack = backStack,
          onBack = { backStack.removeLastOrNull() },
          entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
          ),
          // 進むときは右から重ね、戻るとき (戻るスワイプの途中も) は上の画面を右へずらして下の画面を見せる
          transitionSpec = { pushTransition() },
          popTransitionSpec = { popTransition() },
          predictivePopTransitionSpec = { popTransition() },
          entryProvider = entryProvider {
            entry<Library> {
              Page {
                LibraryHomeScreen(
                  onOpen = { tab, title ->
                    when (tab) {
                      LibraryTab.QUEUE -> backStack.add(Queue)
                      LibraryTab.RECENTLY_ADDED -> backStack.add(GroupDetail(GroupKind.RECENTLY_ADDED.name, null, title))
                      LibraryTab.RECENTLY_PLAYED -> backStack.add(GroupDetail(GroupKind.RECENTLY_PLAYED.name, null, title))
                      LibraryTab.MOST_PLAYED -> backStack.add(GroupDetail(GroupKind.MOST_PLAYED.name, null, title))
                      else -> backStack.add(Category(tab.name))
                    }
                  },
                  onSearch = { backStack.add(Search) },
                  onLicenses = { backStack.add(Licenses) },
                  bottomPadding = bottomPadding,
                )
              }
            }
            entry<Licenses> {
              Page { LicensesScreen(onBack = { backStack.removeLastOrNull() }) }
            }
            entry<Category> { key ->
              Page {
                CategoryScreen(
                  category = LibraryTab.valueOf(key.name),
                  playingMediaStoreId = player.mediaStoreId,
                  actions = actions,
                  onOpenGroup = { kind, k, title -> backStack.add(GroupDetail(kind.name, k, title)) },
                  onBack = { backStack.removeLastOrNull() },
                  onSearch = { backStack.add(Search) },
                  playlists = { padding ->
                    PlaylistsTab(onOpen = { id, _ -> backStack.add(PlaylistDetail(id)) }, actions = actions, bottomPadding = padding)
                  },
                  bottomPadding = bottomPadding,
                )
              }
            }
            entry<PlaylistDetail> { key ->
              Page {
                PlaylistDetailScreen(
                  playlistId = key.id,
                  playingMediaStoreId = player.mediaStoreId,
                  actions = actions,
                  onBack = { backStack.removeLastOrNull() },
                  bottomPadding = bottomPadding,
                )
              }
            }
            entry<GroupDetail> { key ->
              Page {
                GroupDetailScreen(
                  key = key,
                  playingMediaStoreId = player.mediaStoreId,
                  actions = actions,
                  onBack = { backStack.removeLastOrNull() },
                  bottomPadding = bottomPadding,
                )
              }
            }
            entry<Search> {
              Page {
                SearchScreen(
                  playingMediaStoreId = player.mediaStoreId,
                  actions = actions,
                  onBack = { backStack.removeLastOrNull() },
                  bottomPadding = bottomPadding,
                )
              }
            }
            entry<Equalizer> {
              Page {
                EqualizerScreen(onBack = { backStack.removeLastOrNull() })
              }
            }
            entry<Queue> {
              Page {
                QueueScreen(onBack = { backStack.removeLastOrNull() }, bottomPadding = bottomPadding)
              }
            }
            entry<Player> {
              Page {
                PlayerScreen(
                  state = player,
                  sourceTitle = playSource?.title,
                  onClose = { backStack.removeLastOrNull() },
                  onOpenQueue = { backStack.add(Queue) },
                  onOpenEqualizer = { backStack.add(Equalizer) },
                  onTogglePlay = playerViewModel::togglePlayPause,
                  onNext = playerViewModel::next,
                  onPrevious = playerViewModel::previous,
                  onSwipePrevious = playerViewModel::previousTrack,
                  onSeek = playerViewModel::seekTo,
                  onToggleShuffle = playerViewModel::toggleShuffle,
                  onCycleRepeat = playerViewModel::cycleRepeat,
                  sleep = sleep,
                  onSetSleep = playerViewModel::setSleepTimer,
                  onSetSpeed = playerViewModel::setSpeed,
                  // 別の曲の歌詞が残っていても出さない
                  lyrics = lyrics?.takeIf { it.mediaId == player.mediaStoreId?.toString() }?.lyrics,
                )
              }
            }
          },
        )
        if (showMini) {
          MiniPlayer(
            state = player,
            onOpen = {
              // プレイヤーから開いた画面 (キューなど) では、重ねずにそのプレイヤーへ戻る
              if (Player in backStack) {
                while (backStack.lastOrNull() != Player) backStack.removeLastOrNull()
              } else {
                backStack.add(Player)
              }
            },
            onTogglePlay = playerViewModel::togglePlayPause,
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .navigationBarsPadding()
              .padding(bottom = 8.dp),
          )
        }
        pendingAdd?.let { tracks ->
          AddToPlaylistSheet(
            tracks = tracks,
            onDismiss = { pendingAdd = null },
            onMessage = { message -> appScope.launch { snackbar.showMessage(message) } },
          )
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

private const val TRANSITION_MS = 300

private fun pushTransition(): ContentTransform =
  slideInHorizontally(tween(TRANSITION_MS)) { it } togetherWith slideOutHorizontally(tween(TRANSITION_MS)) { -it / 4 }

/** 戻るとき。下になる画面を奥に置き、上の画面だけが右へ抜けていくようにする */
private fun popTransition(): ContentTransform =
  (slideInHorizontally(tween(TRANSITION_MS)) { -it / 4 } togetherWith slideOutHorizontally(tween(TRANSITION_MS)) { it })
    .apply { targetContentZIndex = -1f }

/** 1画面分の背景。画面の切り替え中に、下の画面が透けて見えないようにする */
@Composable
private fun Page(content: @Composable () -> Unit) {
  Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
}
