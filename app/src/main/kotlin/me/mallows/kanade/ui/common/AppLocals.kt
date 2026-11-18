package me.mallows.kanade.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.withTimeoutOrNull

/** 画面全体で共有するSnackbar */
val LocalSnackbarHost = compositionLocalOf { SnackbarHostState() }

/** 選択モードの操作バーを出している間はミニプレイヤーを隠す */
val LocalBottomBarActive = compositionLocalOf<MutableState<Boolean>> { mutableStateOf(false) }

/** メッセージを出す。シートを閉じた後も出せるよう、画面全体の処理として出す */
val LocalShowMessage = compositionLocalOf<(String) -> Unit> { {} }

/** 「元に戻す」付きのメッセージを出す時間 (ミリ秒)。標準の長さ (10秒) では長すぎるため短くする */
private const val UNDO_DURATION_MS = 5_000L

/** 「元に戻す」付きのメッセージを5秒出し、押されたら[onUndo]を呼ぶ */
suspend fun SnackbarHostState.showUndo(message: String, undoLabel: String, onUndo: () -> Unit) {
  currentSnackbarData?.dismiss()
  // 時間を決めずに出し、5秒たったら閉じる (待つのをやめると、出ているメッセージも閉じる)
  val result = withTimeoutOrNull(UNDO_DURATION_MS) {
    showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Indefinite)
  }
  if (result == SnackbarResult.ActionPerformed) onUndo()
}

/** メッセージだけを出す */
suspend fun SnackbarHostState.showMessage(message: String) {
  currentSnackbarData?.dismiss()
  showSnackbar(message, duration = SnackbarDuration.Short)
}
