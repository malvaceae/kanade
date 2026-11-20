package me.mallows.kanade.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
  primary = DarkAccent,
  onPrimary = DarkOnAccent,
  primaryContainer = DarkAccentContainer,
  onPrimaryContainer = DarkAccent,
  secondary = DarkAccent,
  onSecondary = DarkOnAccent,
  background = DarkBackground,
  onBackground = DarkOnSurface,
  surface = DarkBackground,
  onSurface = DarkOnSurface,
  surfaceVariant = DarkSurfaceContainerHigh,
  onSurfaceVariant = DarkOnSurfaceVariant,
  surfaceContainerLowest = DarkBackground,
  surfaceContainerLow = DarkSurfaceContainer,
  surfaceContainer = DarkSurfaceContainer,
  surfaceContainerHigh = DarkSurfaceContainerHigh,
  surfaceContainerHighest = DarkSurfaceContainerHighest,
  outline = DarkOutline,
  outlineVariant = DarkSurfaceContainerHighest,
  inverseSurface = DarkOnSurface,
  inverseOnSurface = DarkBackground,
  inversePrimary = InverseAccent,
)

/** ダーク基調のテーマ (ダークだけ)。アクセントカラーは再生中のアートワークに合わせて後から変える予定 */
@Composable
fun KanadeTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = DarkColors,
    typography = Typography,
    content = content,
  )
}
