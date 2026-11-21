package me.mallows.kanade.ui.common

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable

/**
 * 半開きの状態を使わず、閉じるか全開かだけのシートの状態。
 * 非推奨になった `rememberModalBottomSheetState(skipPartiallyExpanded = true)` の、公式の置き換え方どおり
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberFullSheetState(): SheetState = rememberBottomSheetState(
  initialValue = SheetValue.Hidden,
  enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
)
