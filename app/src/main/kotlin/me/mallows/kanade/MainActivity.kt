package me.mallows.kanade

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import me.mallows.kanade.core.data.settings.SettingsRepository
import me.mallows.kanade.core.data.settings.ThemeMode
import me.mallows.kanade.ui.theme.KanadeTheme
import me.mallows.kanade.ui.theme.isDarkTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  @Inject lateinit var settings: SettingsRepository

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      val themeMode by settings.themeMode.collectAsStateWithLifecycle(ThemeMode.DARK)
      val dark = isDarkTheme(themeMode)
      // テーマに合わせてステータスバーなどのアイコン色を切り替える
      DisposableEffect(dark) {
        val style = if (dark) {
          SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
          SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
      }
      KanadeTheme(darkTheme = dark) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation()
        }
      }
    }
  }
}
