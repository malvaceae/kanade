package me.mallows.kanade.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.mallows.kanade.R
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Image
import me.mallows.kanade.ui.icons.KanadeIcons
import me.mallows.kanade.ui.selection.navigationBarHeight

private const val APACHE = "Apache License 2.0"
private const val BSD = "BSD 3-Clause License"

/** 使っているライブラリ (名前、作者、ライセンス) */
private val LIBRARIES = listOf(
  Triple("Kotlin, kotlinx.coroutines, kotlinx.serialization", "JetBrains s.r.o.", APACHE),
  Triple("AndroidX (Activity, Compose, Core, DataStore, Lifecycle, Navigation 3, Palette, Room)", "The Android Open Source Project", APACHE),
  Triple("AndroidX Media3 (ExoPlayer, Session, FLAC decoder)", "The Android Open Source Project", APACHE),
  Triple("Dagger / Hilt", "The Dagger Authors", APACHE),
  Triple("Guava", "The Guava Authors", APACHE),
  Triple("libFLAC", "Josh Coalson, Xiph.Org Foundation", BSD),
  Triple("Reorderable", "Calvin Liang", APACHE),
  Triple("Timber", "Jake Wharton", APACHE),
)

/** Kanadeについて: アプリのアイコン・名前・バージョンと、オープンソースのライセンス */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
  val res = LocalResources.current
  val context = LocalContext.current
  val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
  val icon = remember { AppIcon.current(context) }
  val licenses = remember {
    listOf(
      APACHE to res.openRawResource(R.raw.apache_license_2_0).bufferedReader().use { it.readText() },
      "$BSD (libFLAC)" to res.openRawResource(R.raw.libflac_license).bufferedReader().use { it.readText() },
    )
  }
  Column(Modifier.fillMaxSize().statusBarsPadding()) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) { Icon(KanadeIcons.Back, contentDescription = stringResource(R.string.action_back)) }
      Text(
        stringResource(R.string.about_title),
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(start = 4.dp),
      )
    }
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp + navigationBarHeight())) {
      // アプリのアイコン (今選んでいるもの)・名前・バージョン
      item {
        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          Box(Modifier.size(96.dp).clip(CircleShape)) {
            // 絵はホーム画面で切り取られる外側まであるので、見える範囲 (108dpのうち72dp) まで拡大して出す
            Image(
              painter = painterResource(icon.art),
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.size(96.dp).graphicsLayer { scaleX = 1.5f; scaleY = 1.5f },
            )
          }
          Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(top = 12.dp),
          )
          Text(
            stringResource(R.string.about_version, version),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
          )
        }
      }
      item { HorizontalDivider() }
      item {
        Text(
          stringResource(R.string.licenses_title),
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          modifier = Modifier.padding(top = 16.dp),
        )
      }
      item {
        Text(
          stringResource(R.string.licenses_intro),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 8.dp),
        )
      }
      items(LIBRARIES) { (name, author, license) ->
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
          Text(name, style = MaterialTheme.typography.bodyLarge)
          Text("$author · $license", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      // ライセンスの本文 (ライセンスごとに1つ)
      items(licenses) { (title, text) ->
        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
        Text(
          text,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
