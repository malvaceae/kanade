package me.mallows.kanade.ui.permission

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.mallows.kanade.R
import me.mallows.kanade.core.common.AndroidVersion
import me.mallows.kanade.ui.library.LibraryViewModel
import me.mallows.kanade.ui.player.MiniPlayer
import me.mallows.kanade.ui.player.PlayerViewModel

/**
 * OSバージョンごとに必要な権限。
 * READ_MEDIA_* と POST_NOTIFICATIONS はAPI 33で入った定数だが、値はコンパイル時に埋め込まれ、使う前に[AndroidVersion]で判定している
 */
@SuppressLint("InlinedApi")
object AudioPermissions {

  /** 音楽ファイルの読み取り権限 */
  fun audio(version: AndroidVersion = AndroidVersion.current): String =
    if (version.usesReadMediaAudio) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

  /** 最初にまとめて要求する権限。動画 (Android 13以降) と通知の権限は、拒否されても音楽は再生できる */
  fun request(version: AndroidVersion = AndroidVersion.current): Array<String> = buildList {
    add(audio(version))
    if (version.usesReadMediaAudio) add(Manifest.permission.READ_MEDIA_VIDEO)
    if (version.requiresNotificationPermission) add(Manifest.permission.POST_NOTIFICATIONS)
  }.toTypedArray()

  fun hasAudio(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, audio()) == PackageManager.PERMISSION_GRANTED

  /** 動画の読み取り権限が必要で (Android 13以降)、まだ許可されていないか */
  fun needsVideo(context: Context, version: AndroidVersion = AndroidVersion.current): Boolean =
    version.usesReadMediaAudio &&
      ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED
}

/** 動画の権限を、音楽の権限だけ許可していた人 (更新前から使っている人) に、一度だけ尋ねたか */
private const val VIDEO_ASKED_PREFS = "permission"
private const val VIDEO_ASKED_KEY = "video_asked"

/**
 * 音楽の権限だけ許可済みの人に、動画の権限を一度だけ尋ねる (系統の確認画面が出る)。拒否されても、音楽は今までどおり使える。
 * 許可されたら、動画を読み込むために再スキャンする。尋ねるのは一度だけで、あとは何も出さない
 * READ_MEDIA_VIDEOはAPI 33の定数だが、値は埋め込まれ、[AudioPermissions.needsVideo]がAndroid 13以降でだけ真を返す
 */
@SuppressLint("InlinedApi")
@Composable
private fun AskVideoPermissionOnce(onGranted: () -> Unit) {
  val context = LocalContext.current
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) onGranted() }
  LaunchedEffect(Unit) {
    val prefs = context.getSharedPreferences(VIDEO_ASKED_PREFS, Context.MODE_PRIVATE)
    if (AudioPermissions.needsVideo(context) && !prefs.getBoolean(VIDEO_ASKED_KEY, false)) {
      prefs.edit { putBoolean(VIDEO_ASKED_KEY, true) }
      launcher.launch(Manifest.permission.READ_MEDIA_VIDEO)
    }
  }
}

/** 音楽ファイルの読み取りが許可されていれば[content]を、されていなければ説明画面を表示する */
@Composable
fun AudioPermissionGate(content: @Composable () -> Unit) {
  val context = LocalContext.current
  var granted by remember { mutableStateOf(AudioPermissions.hasAudio(context)) }
  // 確認画面がもう出ない (拒否を重ねた)。このとき「許可する」を押しても何も起きないので、設定画面へ案内する
  var blocked by remember { mutableStateOf(false) }
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
    // 最初の確認で動画も尋ねたら、許可でも拒否でも、尋ねたことにする (拒否したのに、ライブラリを開いたあとでもう一度尋ねない)。
    // 音楽の許可が先に反映されて画面が移ると、そこで動画の確認が出てしまうので、先に記録する
    if (Manifest.permission.READ_MEDIA_VIDEO in result) {
      context.getSharedPreferences(VIDEO_ASKED_PREFS, Context.MODE_PRIVATE).edit { putBoolean(VIDEO_ASKED_KEY, true) }
    }
    granted = AudioPermissions.hasAudio(context)
    // 許可を求めた直後に許可されていなくて、理由の説明も出せない (shouldShowRequestPermissionRationale が false) なら、もう確認画面は出ない
    blocked = !granted && context.findActivity()?.let { !ActivityCompat.shouldShowRequestPermissionRationale(it, AudioPermissions.audio()) } == true
  }
  // 設定画面で許可して戻ってきた場合に反映する
  LifecycleResumeEffect(Unit) {
    granted = AudioPermissions.hasAudio(context)
    onPauseOrDispose { }
  }

  if (granted) {
    val library: LibraryViewModel = hiltViewModel()
    AskVideoPermissionOnce(onGranted = library::rescan)
    content()
  } else {
    // 外部のアプリから開いた音声ファイルは、ライブラリの権限がなくても再生できる。再生中なら、その曲を見て止められるよう、下に簡易プレイヤーを出す
    val player: PlayerViewModel = hiltViewModel()
    val state by player.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
      PermissionScreen(
        blocked = blocked,
        onRequest = { launcher.launch(AudioPermissions.request()) },
        onOpenSettings = {
          context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          )
        },
      )
      if (state.isExternal) {
        MiniPlayer(
          state = state,
          onOpen = {},
          onTogglePlay = player::togglePlayPause,
          modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp),
        )
      }
    }
  }
}

@Composable
private fun PermissionScreen(blocked: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
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
      // Android 13以降は通知の許可も一緒に尋ねる (通知の確認が先に出る)
      stringResource(
        if (AndroidVersion.current.requiresNotificationPermission) R.string.permission_body_with_notification
        else R.string.permission_body,
      ),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // 確認画面がもう出ないときは、「許可する」を押しても何も起きない。設定を開くことが唯一の手段なので、そちらだけを出す
    if (blocked) {
      Button(onClick = onOpenSettings) { Text(stringResource(R.string.permission_open_settings)) }
    } else {
      Button(onClick = onRequest) { Text(stringResource(R.string.permission_allow)) }
    }
  }
}

private fun Context.findActivity(): Activity? = generateSequence(this) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
