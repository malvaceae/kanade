package me.mallows.kanade.ui.about

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.mallows.kanade.R
import me.mallows.kanade.ui.common.rememberFullSheetState

/**
 * アプリのアイコン。アイコンごとにマニフェストの入り口 (activity-alias) があり、選んだものだけを有効にする。
 * Gの入り口は前からの名前 (.MainActivity) で、何も選んでいないときはこれが有効 (マニフェストの既定)
 */
enum class AppIcon(private val alias: String, @param:StringRes val label: Int, @param:DrawableRes val art: Int) {
  HEADPHONES("me.mallows.kanade.MainActivity", R.string.app_icon_headphones, R.mipmap.ic_launcher_g_art),
  PAIR("me.mallows.kanade.LauncherE", R.string.app_icon_pair, R.mipmap.ic_launcher_e_art),
  STAFF("me.mallows.kanade.LauncherH", R.string.app_icon_staff, R.mipmap.ic_launcher_h_art);

  private fun component(context: Context) = ComponentName(context, alias)

  private fun enabled(context: Context): Boolean =
    when (context.packageManager.getComponentEnabledSetting(component(context))) {
      PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
      PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> this == HEADPHONES
      else -> false
    }

  companion object {
    /** 閉じてから開き直すまでの時間 */
    private const val REOPEN_DELAY_MS = 600L

    /** 今のアイコン */
    fun current(context: Context): AppIcon = entries.firstOrNull { it.enabled(context) } ?: HEADPHONES

    /**
     * アイコンを変える。入り口が1つもない瞬間を作らないよう、先に新しい入り口を有効にしてから、ほかを無効にする。
     * 今の画面を開いた入り口を無効にすると、Androidがその画面を閉じるので、いったん閉じてから新しい入り口で開き直す (再生は続く)。
     * すぐに開くと、Androidが閉じる処理に巻き込まれて新しい画面も閉じられるため、少し待ってから開く
     */
    fun apply(context: Context, icon: AppIcon) {
      val pm = context.packageManager
      pm.setComponentEnabledSetting(icon.component(context), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
      entries.filter { it != icon }.forEach {
        pm.setComponentEnabledSetting(it.component(context), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
      }
      val app = context.applicationContext
      val reopen = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(icon.component(app))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ app.startActivity(reopen) }, REOPEN_DELAY_MS)
    }
  }
}

/** アプリのアイコンを選ぶシート。選ぶと確かめてから変える */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppIconSheet(onChanged: () -> Unit, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val current = remember { AppIcon.current(context) }
  var confirming by remember { mutableStateOf<AppIcon?>(null) }
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberFullSheetState()) {
    Column(Modifier.navigationBarsPadding().padding(bottom = 24.dp)) {
      Text(
        stringResource(R.string.app_icon_title),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
      )
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
      ) {
        AppIcon.entries.forEach { icon ->
          val selected = icon == current
          Column(
            modifier = Modifier
              .clip(MaterialTheme.shapes.medium)
              .clickable { if (!selected) confirming = icon }
              .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Box(
              modifier = Modifier
                .size(80.dp)
                .border(
                  if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(0.dp, MaterialTheme.colorScheme.surface),
                  CircleShape,
                )
                .padding(5.dp)
                .clip(CircleShape),
            ) {
              // 絵はホーム画面で切り取られる外側まであるので、見える範囲 (108dpのうち72dp) まで拡大して出す
              Image(
                painter = painterResource(icon.art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(70.dp).graphicsLayer { scaleX = 1.5f; scaleY = 1.5f },
              )
            }
            Text(
              stringResource(icon.label),
              style = MaterialTheme.typography.bodyMedium,
              color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.padding(top = 8.dp),
            )
          }
        }
      }
    }
  }
  confirming?.let { icon ->
    AlertDialog(
      onDismissRequest = { confirming = null },
      title = { Text(stringResource(R.string.app_icon_confirm_title)) },
      text = { Text(stringResource(R.string.app_icon_confirm_body)) },
      confirmButton = {
        TextButton(onClick = {
          confirming = null
          onChanged()
          AppIcon.apply(context, icon)
          // アイコンを変えるとAndroidが画面を閉じるので、こちらから閉じる (少しあとで開き直す)
          (context as? android.app.Activity)?.finishAndRemoveTask()
        }) { Text(stringResource(R.string.action_change)) }
      },
      dismissButton = { TextButton(onClick = { confirming = null }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }
}
