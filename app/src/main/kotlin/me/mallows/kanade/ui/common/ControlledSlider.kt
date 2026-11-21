package me.mallows.kanade.ui.common

import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * 値を呼び出し側が持つスライダー。非推奨になった `Slider(value, onValueChange, ...)` と同じ動きにする。
 * 公式の置き換え例の `rememberSliderState(value)` は、渡した値を初期値にしか使わず、あとで外から値が変わっても追わない。
 * 再生位置のように外から値が変わるスライダーには使えないので、旧版と同じく、組み直すたびに値を状態へ入れる
 */
@Composable
fun ControlledSlider(
  value: Float,
  onValueChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
  valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
  onValueChangeFinished: (() -> Unit)? = null,
  colors: SliderColors = SliderDefaults.colors(),
) {
  val state = remember(valueRange) { SliderState(value = value, trackRange = valueRange) }
  state.value = value
  Slider(
    state = state,
    onValueChange = onValueChange,
    modifier = modifier,
    onValueChangeFinished = onValueChangeFinished,
    colors = colors,
  )
}
