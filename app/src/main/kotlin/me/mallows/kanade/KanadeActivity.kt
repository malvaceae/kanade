package me.mallows.kanade

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.util.Rational
import androidx.compose.runtime.CompositionLocalProvider
import me.mallows.kanade.ui.player.LocalPictureInPicture
import me.mallows.kanade.ui.player.PictureInPictureHost
import android.os.Bundle
import me.mallows.kanade.playback.PlaybackService
import androidx.compose.runtime.mutableStateOf
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import me.mallows.kanade.playback.PlayerConnection
import me.mallows.kanade.ui.theme.KanadeTheme

@AndroidEntryPoint
class KanadeActivity : ComponentActivity() {

  @Inject lateinit var player: PlayerConnection

  /** 通知などから開かれ、プレイヤー画面を出す必要がある */
  private val openPlayer = mutableStateOf(false)

  /** 動画のPiP (小窓) の状態。再生画面とやりとりする */
  private val pip = PictureInPictureHost()

  /** ホームへ戻るとき、動画を映していれば、PiPの小窓に移る (Android 8.0以降) */
  override fun onUserLeaveHint() {
    super.onUserLeaveHint()
    if (!pip.eligible || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
    // 小窓の縦横比は、許される範囲 (約 0.42 ~ 2.39) に収める
    val ratio = pip.aspect.coerceIn(0.42f, 2.39f)
    val params = PictureInPictureParams.Builder().setAspectRatio(Rational((ratio * 1000).toInt(), 1000)).build()
    // 移れたら、システムの知らせ (onPictureInPictureModeChanged) を待たずに、すぐ小窓の状態にする
    // (待つと、小窓への移行中の1コマに、操作の表示が映る)
    if (runCatching { enterPictureInPictureMode(params) }.getOrDefault(false)) pip.active = true
  }

  override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
    super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    pip.active = isInPictureInPictureMode
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    if (intent.getBooleanExtra(PlaybackService.EXTRA_OPEN_PLAYER, false)) openPlayer.value = true
    openExternal(intent)
  }

  /**
   * ファイラーなどの「このアプリで開く」(ACTION_VIEW) で渡された音声・動画ファイルを再生する。
   * ライブラリには入れず、スキャン対象の設定も変えない。ライブラリ全体の読み取り権限がなくても、渡されたファイルは再生できる
   */
  private fun openExternal(intent: Intent) {
    if (intent.action != Intent.ACTION_VIEW) return
    val uri = intent.data ?: return
    player.connect()
    player.playExternal(uri, displayName(uri), intent.type)
    openPlayer.value = true
  }

  /** ファイルの表示名。取れなければURIの最後の部分 */
  private fun displayName(uri: Uri): String? = runCatching {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
      if (c.moveToFirst()) c.getString(0) else null
    }
  }.getOrNull() ?: uri.lastPathSegment

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (savedInstanceState == null && intent?.getBooleanExtra(PlaybackService.EXTRA_OPEN_PLAYER, false) == true) {
      openPlayer.value = true
    }
    // 画面の作り直し (回転など) では、開き直さない
    if (savedInstanceState == null) intent?.let(::openExternal)
    // ダークだけなので、ステータスバーなどのアイコンは、明るい色にする
    val style = SystemBarStyle.dark(Color.TRANSPARENT)
    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    setContent {
      KanadeTheme {
        CompositionLocalProvider(LocalPictureInPicture provides pip) {
          Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            MainNavigation(openPlayerRequest = openPlayer)
          }
        }
      }
    }
  }
}
