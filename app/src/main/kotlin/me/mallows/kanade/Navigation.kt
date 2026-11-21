package me.mallows.kanade

import android.content.res.Configuration
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
import me.mallows.kanade.ui.selection.SELECTION_BAR_MS
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.ui.res.stringResource
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import me.mallows.kanade.ui.player.LocalPlaybackPosition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.unit.Dp
import me.mallows.kanade.ui.common.NavItem
import me.mallows.kanade.ui.common.AppNavBarHeight
import me.mallows.kanade.ui.common.AppNavBar
import me.mallows.kanade.ui.common.showMessage
import me.mallows.kanade.playback.PlaybackEvent
import androidx.compose.ui.platform.LocalResources
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
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
import me.mallows.kanade.ui.common.LocalShowUndo
import me.mallows.kanade.ui.common.showUndo
import me.mallows.kanade.playback.PlaySource
import me.mallows.kanade.ui.common.LocalSnackbarHost
import me.mallows.kanade.ui.equalizer.EqualizerScreen
import me.mallows.kanade.ui.library.GroupDetailScreen
import me.mallows.kanade.ui.library.ArtistDetailScreen
import androidx.navigation3.runtime.NavKey
import me.mallows.kanade.ui.library.LibraryHomeScreen
import me.mallows.kanade.core.data.library.GroupKind
import me.mallows.kanade.ui.library.LibraryTab
import me.mallows.kanade.ui.library.CategoryScreen
import me.mallows.kanade.ui.about.LicensesScreen
import me.mallows.kanade.ui.library.LibraryViewModel
import me.mallows.kanade.ui.library.ScanFoldersScreen
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MainNavigation(
  openPlayerRequest: MutableState<Boolean> = remember { mutableStateOf(false) },
  playerViewModel: PlayerViewModel = hiltViewModel(),
  libraryViewModel: LibraryViewModel = hiltViewModel(),
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
  val videoFrame by playerViewModel.videoFrame.collectAsStateWithLifecycle()
  val playSource by playerViewModel.source.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  /** 画面をまたいで続く処理 (シートを閉じた後のメッセージなど) */
  val appScope = rememberCoroutineScope()
  val undoLabel = stringResource(R.string.action_undo)
  val resources = LocalResources.current
  // スキャンが終わったら、読み込んだ曲の数を知らせる (どの画面から始めたスキャンでも)
  LaunchedEffect(Unit) {
    libraryViewModel.scanResults.collect { r ->
      snackbar.showMessage(resources.getQuantityString(R.plurals.scan_result, r.included, r.included))
    }
  }
  // 再生サービスからの知らせ (読めない曲を飛ばした、など)
  LaunchedEffect(Unit) {
    playerViewModel.events.collect { event ->
      val text = when (event) {
        PlaybackEvent.FILE_NOT_FOUND -> R.string.message_file_not_found
        PlaybackEvent.UNPLAYABLE -> R.string.message_unplayable
        PlaybackEvent.EXTERNAL_UNPLAYABLE -> R.string.message_external_unplayable
        PlaybackEvent.ERROR -> R.string.message_playback_error
      }
      snackbar.showMessage(resources.getString(text))
    }
  }
  val bottomBarActive = remember { mutableStateOf(false) }
  // 動画の全画面のあいだは、下のメニューを出さない
  var videoFullscreen by remember { mutableStateOf(false) }
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
  // 再生キューが空になったら (読めない曲を飛ばして最後の曲もなくなった、など)、プレイヤーの画面を閉じる。
  // プレイヤーから開いたキューの画面も一緒に閉じる (ライブラリから開いたキューは、空でも見られるよう残す)
  // (起動の直後は、つながっても曲がまだ入っていないことがある。曲が入ったことが一度もないうちは閉じない。
  // 外部のアプリから開いたファイルでプレイヤーを出したとき、曲が入る前に閉じてしまうのを防ぐ)
  var hadMedia by remember { mutableStateOf(false) }
  LaunchedEffect(player.hasMedia) { if (player.hasMedia) hadMedia = true }
  LaunchedEffect(player.connected, player.hasMedia) {
    if (player.connected && !player.hasMedia && hadMedia && Player in backStack) {
      while (backStack.isNotEmpty() && backStack.last() != Player) backStack.removeLastOrNull()
      backStack.removeLastOrNull()
    }
  }
  // 画面下の固定のメニュー。フォルダの選択 (下に保存・キャンセルがある) では出さない。
  // 選択中は選択の操作バーがその場所を使う
  // 横向きの動画は、プレイヤーを開いた最初の描画から全画面にする (全画面に入る知らせは、最初の描画のあとに届く。
  // それまで下のメニューの余白が入ると、映像の面が、画面の切り替えの最中に大きさを変え、前の大きさのまま残ることがある)
  val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
  val inVideoFullscreen = videoFullscreen || (top == Player && player.isVideo && landscape)
  val showNavBar = top != ScanFolders && !bottomBarActive.value && !inVideoFullscreen
  val navBar = if (showNavBar) AppNavBarHeight else 0.dp
  // 簡易プレイヤーは一覧の画面に出す。プレイヤー・EQ・ライセンス・フォルダの選択の画面には出さない
  val showMini = player.hasMedia && top != Player && top != Equalizer && top != Licenses && top != ScanFolders && !bottomBarActive.value
  // 一覧の下の余白: 固定のメニュー、簡易プレイヤー (高さ64・下8)、ナビゲーションバーの分に、曲どうしの間隔と同じくらいの隙間 (12) を足す
  val bottomPadding = AppNavBarHeight + (if (player.hasMedia) 84.dp else 24.dp) + navigationBarHeight()
  /** プレイヤー画面を開く。プレイヤーから開いた画面 (キューなど) では、重ねずにそのプレイヤーへ戻る */
  fun openPlayerScreen() {
    if (Player in backStack) {
      while (backStack.lastOrNull() != Player) backStack.removeLastOrNull()
    } else {
      backStack.add(Player)
    }
  }
  /**
   * 下のメニューから移ったときは、進む・戻るの向きのある動きではなくフェードで切り替える
   * (メニューは場所の切り替えなので、戻る履歴にある画面へ戻るときに左から入ってくるのは不自然なため)
   */
  val menuMove = remember { MenuMove() }
  // 下のメニューで移ったら、切り替えが終わる頃に印を消す (その後の普通の移動は左右に動かす)
  LaunchedEffect(backStack.lastOrNull()) {
    if (menuMove.pending) {
      kotlinx.coroutines.delay(TRANSITION_MS * 2L)
      menuMove.pending = false
    }
  }
  /** 固定のメニューの行き先へ移る。すでに戻る履歴にあれば、そこまで戻る (同じ画面を重ねない) */
  fun navigate(item: NavItem) {
    val key: NavKey = when (item) {
      NavItem.LIBRARY -> Library
      NavItem.EQUALIZER -> Equalizer
      NavItem.SEARCH -> Search
      NavItem.QUEUE -> Queue
    }
    if (backStack.lastOrNull() == key) return
    menuMove.pending = true
    if (key in backStack) {
      while (backStack.lastOrNull() != key) backStack.removeLastOrNull()
    } else {
      backStack.add(key)
    }
  }

  CompositionLocalProvider(
    LocalSnackbarHost provides snackbar,
    LocalBottomBarActive provides bottomBarActive,
    // 曲の一覧の「再生中」の印も、読み込み中に止まって見えないようボタンと同じ決め方にする
    LocalIsPlaying provides player.showPause,
    LocalPlaySource provides playSource,
    LocalPlaybackPosition provides playerViewModel::currentPosition,
    LocalShowMessage provides { message: String -> appScope.launch { snackbar.showMessage(message) } },
    LocalShowUndo provides { message: String, onUndo: suspend () -> Unit, onExpire: suspend () -> Unit ->
      appScope.launch {
        var undone = false
        snackbar.showUndo(message, undoLabel) { undone = true }
        if (undone) onUndo() else onExpire()
      }
    },
  ) {
    AudioPermissionGate {
      Box(Modifier.fillMaxSize()) {
        // 簡易プレイヤーと下のメニューは、キーボードが出たらその下に隠れるだけにする (Androidの普通の動き)。
        // 検索を開くとすぐキーボードが出るので、直前の画面に簡易プレイヤーがなかったとき (EQ・ライセンス・プレイヤー) は、
        // キーボードが1度出終わるまで簡易プレイヤーを出さない (一瞬だけ現れてから隠れるのを避ける)。
        // 出始めではキーボードがまだ簡易プレイヤーの位置まで届いていないので、出終わるのを待つ。
        // 直前の画面に出ていたときは、そのまま残してキーボードの下に隠す
        val density = LocalDensity.current
        val imeTarget = WindowInsets.imeAnimationTarget.getBottom(density)
        val imeNow = WindowInsets.ime.getBottom(density)
        val imeShownFully = imeTarget > 0 && imeNow >= imeTarget
        var searchImeShown by remember { mutableStateOf(false) }
        LaunchedEffect(top, imeShownFully) {
          searchImeShown = top == Search && (searchImeShown || imeShownFully)
        }
        /** 検索以外の直前の画面で、簡易プレイヤーを出していたか */
        var miniShownBefore by remember { mutableStateOf(false) }
        SideEffect { if (top != Search) miniShownBefore = showMini }
        val searchOpening = top == Search && !miniShownBefore && !searchImeShown
        // 簡易プレイヤーは各画面の中に置き、画面どうしの共有要素にする。
        // 前後の両方の画面にあればその場に留まり、片方にしかなければその画面と一緒に動く (横に動く切り替えなら横に、フェードならフェード)
        SharedTransitionLayout {
          val miniPlayer: @Composable BoxScope.() -> Unit = {
            if (player.hasMedia) {
              // 選択中は下の操作バーがその場所を使うので隠す
              AnimatedVisibility(
                visible = !bottomBarActive.value,
                enter = fadeIn(tween(210, delayMillis = 90)),
                exit = fadeOut(tween(90)),
                modifier = Modifier
                  .align(Alignment.BottomCenter)
                  .navigationBarsPadding()
                  .padding(bottom = AppNavBarHeight + 8.dp)
                  .graphicsLayer { alpha = if (searchOpening) 0f else 1f },
              ) {
                MiniPlayer(
                  state = player,
                  onOpen = ::openPlayerScreen,
                  onTogglePlay = playerViewModel::togglePlayPause,
                  modifier = Modifier.sharedElement(
                    rememberSharedContentState(MINI_PLAYER_KEY),
                    animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                  ),
                )
              }
            }
          }
          NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(
              rememberSaveableStateHolderNavEntryDecorator(),
              rememberViewModelStoreNavEntryDecorator(),
            ),
            // 進むときは右から重ね、戻るとき (戻るスワイプの途中も) は上の画面を右へずらして下の画面を見せる
            transitionSpec = { if (menuMove.pending) fadeTransition() else pushTransition() },
            popTransitionSpec = { if (menuMove.pending) fadeTransition() else popTransition() },
            predictivePopTransitionSpec = { popTransition() },
            entryProvider = entryProvider {
              entry<Library> {
                Page(mini = miniPlayer) {
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
                    onLicenses = { backStack.add(Licenses) },
                    onScanFolders = { backStack.add(ScanFolders) },
                    bottomPadding = bottomPadding,
                  )
                }
              }
              entry<ScanFolders> {
                Page { ScanFoldersScreen(onBack = { backStack.removeLastOrNull() }) }
              }
              entry<Licenses> {
                Page(bottomInset = navBar) { LicensesScreen(onBack = { backStack.removeLastOrNull() }) }
              }
              entry<Category> { key ->
                Page(mini = miniPlayer) {
                  CategoryScreen(
                    category = LibraryTab.valueOf(key.name),
                    playingMediaStoreId = player.mediaStoreId,
                    actions = actions,
                    onOpenGroup = { kind, k, title -> backStack.add(groupKey(kind, k, title)) },
                    onBack = { backStack.removeLastOrNull() },
                    playlists = { padding ->
                      PlaylistsTab(onOpen = { id, _ -> backStack.add(PlaylistDetail(id)) }, actions = actions, bottomPadding = padding)
                    },
                    bottomPadding = bottomPadding,
                    onScanFolders = { backStack.add(ScanFolders) },
                  )
                }
              }
              entry<PlaylistDetail> { key ->
                Page(mini = miniPlayer) {
                  PlaylistDetailScreen(
                    playlistId = key.id,
                    playingMediaStoreId = player.mediaStoreId,
                    actions = actions,
                    onBack = { backStack.removeLastOrNull() },
                    bottomPadding = bottomPadding,
                  )
                }
              }
              entry<ArtistDetail> { key ->
                Page(mini = miniPlayer) {
                  ArtistDetailScreen(
                    key = key,
                    actions = actions,
                    // ここからは曲の一覧を開く (アーティストの「すべての曲」も)
                    onOpenGroup = { kind, k, title -> backStack.add(GroupDetail(kind.name, k, title)) },
                    onBack = { backStack.removeLastOrNull() },
                    bottomPadding = bottomPadding,
                  )
                }
              }
              entry<GroupDetail> { key ->
                Page(mini = miniPlayer) {
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
                Page(mini = miniPlayer) {
                  SearchScreen(
                    playingMediaStoreId = player.mediaStoreId,
                    actions = actions,
                    onBack = { backStack.removeLastOrNull() },
                    bottomPadding = bottomPadding,
                  )
                }
              }
              entry<Equalizer> {
                Page(bottomInset = navBar) {
                  EqualizerScreen(onBack = { backStack.removeLastOrNull() })
                }
              }
              entry<Queue> {
                Page(mini = miniPlayer) {
                  QueueScreen(onBack = { backStack.removeLastOrNull() }, onOpenPlayer = ::openPlayerScreen, bottomPadding = bottomPadding)
                }
              }
              entry<Player> {
                Page(bottomInset = navBar) {
                  PlayerScreen(
                    state = player,
                    sourceTitle = playSource?.title,
                    onClose = { backStack.removeLastOrNull() },
                    onOpenQueue = { backStack.add(Queue) },
                    onOpenSource = {
                      // 一覧が戻る履歴にあるかどうかで、左右の向きが変わってしまうので、下のメニューと同じくフェードで移る
                      menuMove.pending = true
                      val destination = sourceDestination(playSource)
                      if (destination == null) {
                        // 一覧を開けない再生元 (選択した曲・検索・外から開いた曲) は、再生キューを開く
                        backStack.add(Queue)
                      } else {
                        // プレイヤーを閉じて一覧を開く。すでにその一覧から来ていたら、閉じるだけ
                        backStack.removeLastOrNull()
                        if (backStack.lastOrNull() != destination) backStack.add(destination)
                      }
                    },
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
                    onAttachVideo = playerViewModel::attachVideo,
                    onDetachVideo = playerViewModel::detachVideo,
                    onFullscreenChange = { videoFullscreen = it },
                    videoReady = videoFrame.mediaId != null && videoFrame.mediaId == player.mediaId,
                    // 今の動画の最初の映像が出るまでは0 (枠は前の縦横比のまま)
                    videoAspect = if (videoFrame.mediaId != null && videoFrame.mediaId == player.mediaId) videoFrame.aspect else 0f,
                    // 別の曲の歌詞が残っていても出さない
                    lyrics = lyrics?.takeIf { it.mediaId == player.mediaStoreId?.toString() }?.lyrics,
                  )
                }
              }
            },
          )
        }
        // 選択の操作バーと入れ替わるときは、下から滑らせて出し入れする (画面を移るときは今までどおりすぐ切り替える)
        if (top != ScanFolders && !inVideoFullscreen) AnimatedVisibility(
          visible = !bottomBarActive.value,
          modifier = Modifier.align(Alignment.BottomCenter),
          enter = slideInVertically(tween(SELECTION_BAR_MS)) { it } + fadeIn(tween(SELECTION_BAR_MS)),
          exit = slideOutVertically(tween(SELECTION_BAR_MS)) { it } + fadeOut(tween(SELECTION_BAR_MS)),
        ) {
          AppNavBar(
            selected = when (top) {
              Equalizer -> NavItem.EQUALIZER
              Search -> NavItem.SEARCH
              Queue -> NavItem.QUEUE
              // プレイヤーはメニューのどれにも当たらないので、どれも選ばない
              Player -> null
              else -> NavItem.LIBRARY
            },
            onSelect = ::navigate,
          )
        }
        pendingAdd?.let { tracks ->
          AddToPlaylistSheet(
            tracks = tracks,
            onDismiss = { pendingAdd = null },
            onMessage = { message -> appScope.launch { snackbar.showMessage(message) } },
          )
        }
        // スキャン中は、どの画面でも上端に進み具合を出す
        val scanning by libraryViewModel.scanning.collectAsStateWithLifecycle()
        if (scanning) {
          LinearProgressIndicator(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().height(4.dp))
        }
        SnackbarHost(
          hostState = snackbar,
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            // 簡易プレイヤーのある画面とない画面で知らせの位置が動かないよう、再生中の曲があれば常に簡易プレイヤーの上に出す
            .padding(bottom = navBar + if (player.hasMedia || bottomBarActive.value) 84.dp else 8.dp),
        )
      }
    }
  }
}

private const val TRANSITION_MS = 300

private fun pushTransition(): ContentTransform =
  slideInHorizontally(tween(TRANSITION_MS)) { it } togetherWith slideOutHorizontally(tween(TRANSITION_MS)) { -it / 4 }

/** 下のメニューで移るとき。前の画面を先に薄くしてから、次の画面を浮かび上がらせる (Material の fade through) */
private fun fadeTransition(): ContentTransform =
  fadeIn(tween(210, delayMillis = 90)) togetherWith fadeOut(tween(90))

/**
 * 下のメニューから移ったことを、その画面の切り替えが終わるまで覚えておく。
 * 切り替えの動きは1回の移動で何度も問い合わせられるので、読んだだけでは消さない
 */
private class MenuMove {
  var pending = false
}

/** 戻るとき。下になる画面を奥に置き、上の画面だけが右へ抜けていくようにする */
private fun popTransition(): ContentTransform =
  (slideInHorizontally(tween(TRANSITION_MS)) { -it / 4 } togetherWith slideOutHorizontally(tween(TRANSITION_MS)) { it })
    .apply { targetContentZIndex = -1f }

/**
 * 1画面分の背景。画面の切り替え中に、下の画面が透けて見えないようにする。
 * [bottomInset]は画面下の固定のメニューの高さ。中身をその上までにする (中身のナビゲーションバーの余白はメニューが受け持つ)
 */
@Composable
private fun Page(
  bottomInset: Dp = 0.dp,
  /** この画面に出す簡易プレイヤー (出さない画面はnull) */
  mini: (@Composable BoxScope.() -> Unit)? = null,
  content: @Composable () -> Unit,
) {
  Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    // 余白の有無で組み立て方を変えない (変えると、中の画面の状態が捨てられる。動画の全画面に入ると余白が0になる)
    Box(
      Modifier.fillMaxSize()
        .padding(bottom = bottomInset)
        .then(if (bottomInset > 0.dp) Modifier.navigationBarsPadding() else Modifier),
    ) { content() }
    mini?.invoke(this)
  }
}

/** 簡易プレイヤーの共有要素のキー */
private const val MINI_PLAYER_KEY = "mini_player"

/** アルバムなどを開く画面。アーティストはアルバムの一覧を挟む */
private fun groupKey(kind: GroupKind, key: String?, title: String): NavKey =
  if (kind == GroupKind.ARTIST) ArtistDetail(key, title) else GroupDetail(kind.name, key, title)

/**
 * 再生元の曲の一覧を開く画面。一覧を開けない再生元 (選択した曲・検索・外から開いた曲) はnull。
 * キーの形は、曲の一覧を作る側 (PlaySource) が決めている: "tracks"、"playlist:ID"、"group:種類:キー"。
 * キーが無い一覧 (最近追加した曲など) は、キーの部分が"null"になっている
 */
internal fun sourceDestination(source: PlaySource?): NavKey? {
  val key = source?.key ?: return null
  return when {
    key == "tracks" -> Category(LibraryTab.TRACKS.name)
    key.startsWith("playlist:") -> key.removePrefix("playlist:").toLongOrNull()?.let(::PlaylistDetail)
    key.startsWith("group:") -> {
      val parts = key.split(':', limit = 3)
      val kind = GroupKind.entries.firstOrNull { it.name == parts.getOrNull(1) }
      if (parts.size == 3 && kind != null) groupKey(kind, parts[2].takeIf { it != "null" }, source.title.orEmpty()) else null
    }
    else -> null
  }
}
