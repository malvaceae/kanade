package me.mallows.kanade.ui.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import me.mallows.kanade.R
import me.mallows.kanade.core.common.AndroidVersion

/** OSバージョンごとに必要な権限 */
object AudioPermissions {

  /** 音楽ファイルの読み取り権限 */
  fun audio(version: AndroidVersion = AndroidVersion.current): String =
    if (version.usesReadMediaAudio) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

  /** 通知の権限 (Android 13以降)。拒否されても再生はできる */
  fun notification(version: AndroidVersion = AndroidVersion.current): String? =
    if (version.requiresNotificationPermission) Manifest.permission.POST_NOTIFICATIONS else null

  fun hasAudio(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, audio()) == PackageManager.PERMISSION_GRANTED
}

/** 音楽ファイルの読み取りが許可されていれば[content]を、されていなければ説明画面を表示する */
@Composable
fun AudioPermissionGate(content: @Composable () -> Unit) {
  val context = LocalContext.current
  var granted by remember { mutableStateOf(AudioPermissions.hasAudio(context)) }
  var asked by remember { mutableStateOf(false) }
  // 通知は音楽ファイルの許可の後に別に尋ねる (まとめて要求すると、通知の確認が先に出てしまう)
  val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
    granted = ok
    asked = true
    val notification = AudioPermissions.notification()
    if (ok && notification != null &&
      ContextCompat.checkSelfPermission(context, notification) != PackageManager.PERMISSION_GRANTED
    ) {
      notificationLauncher.launch(notification)
    }
  }
  // 設定画面で許可して戻ってきた場合に反映する
  LifecycleResumeEffect(Unit) {
    granted = AudioPermissions.hasAudio(context)
    onPauseOrDispose { }
  }

  if (granted) {
    content()
  } else {
    PermissionScreen(
      showSettings = asked,
      onRequest = { launcher.launch(AudioPermissions.audio()) },
      onOpenSettings = {
        context.startActivity(
          Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
      },
    )
  }
}

@Composable
private fun PermissionScreen(showSettings: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .safeDrawingPadding()
      .padding(32.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.Start,
  ) {
    Text(stringResource(R.string.permission_title), style = MaterialTheme.typography.headlineSmall)
    Text(
      stringResource(R.string.permission_body),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = onRequest) { Text(stringResource(R.string.permission_allow)) }
    if (showSettings) {
      TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.permission_open_settings)) }
    }
  }
}
