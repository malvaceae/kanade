package me.mallows.kanade.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import me.mallows.kanade.core.data.settings.ThemeMode

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
)

private val LightColors = lightColorScheme(
  primary = LightAccent,
  onPrimary = LightOnAccent,
  primaryContainer = LightAccentContainer,
  onPrimaryContainer = LightAccent,
  secondary = LightAccent,
  onSecondary = LightOnAccent,
  background = LightBackground,
  onBackground = LightOnSurface,
  surface = LightBackground,
  onSurface = LightOnSurface,
  surfaceVariant = LightSurfaceContainerHigh,
  onSurfaceVariant = LightOnSurfaceVariant,
  surfaceContainerLowest = LightBackground,
  surfaceContainerLow = LightSurfaceContainer,
  surfaceContainer = LightSurfaceContainer,
  surfaceContainerHigh = LightSurfaceContainerHigh,
  surfaceContainerHighest = LightSurfaceContainerHighest,
  outline = LightOutline,
  outlineVariant = LightSurfaceContainerHighest,
  inverseSurface = LightOnSurface,
  inverseOnSurface = LightBackground,
)

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
  ThemeMode.SYSTEM -> isSystemInDarkTheme()
  ThemeMode.LIGHT -> false
  ThemeMode.DARK -> true
}

/** ダーク基調のテーマ。アクセントカラーは再生中のアートワークに合わせて後から変える予定 */
@Composable
fun KanadeTheme(
  darkTheme: Boolean = true,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = if (darkTheme) DarkColors else LightColors,
    typography = Typography,
    content = content,
  )
}
