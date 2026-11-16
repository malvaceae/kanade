package me.mallows.kanade.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf

/** 画面全体で共有するSnackbar */
val LocalSnackbarHost = compositionLocalOf { SnackbarHostState() }

/** 選択モードの操作バーを出している間はミニプレイヤーを隠す */
val LocalBottomBarActive = compositionLocalOf<MutableState<Boolean>> { mutableStateOf(false) }

/** 「元に戻す」付きのメッセージを出し、押されたら[onUndo]を呼ぶ */
suspend fun SnackbarHostState.showUndo(message: String, undoLabel: String, onUndo: () -> Unit) {
  currentSnackbarData?.dismiss()
  val result = showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Long)
  if (result == SnackbarResult.ActionPerformed) onUndo()
}

/** メッセージだけを出す */
suspend fun SnackbarHostState.showMessage(message: String) {
  currentSnackbarData?.dismiss()
  showSnackbar(message, duration = SnackbarDuration.Short)
}
