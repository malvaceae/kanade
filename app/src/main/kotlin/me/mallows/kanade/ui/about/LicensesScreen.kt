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
import me.mallows.kanade.ui.icons.KanadeIcons
import me.mallows.kanade.ui.selection.navigationBarHeight

/** 使っているライブラリ (名前と作者)。どれも Apache License 2.0 */
private val LIBRARIES = listOf(
  "Kotlin, kotlinx.coroutines, kotlinx.serialization" to "JetBrains s.r.o.",
  "AndroidX (Activity, Compose, Core, DataStore, Lifecycle, Navigation 3, Palette, Room)" to "The Android Open Source Project",
  "AndroidX Media3 (ExoPlayer, Session)" to "The Android Open Source Project",
  "Dagger / Hilt" to "The Dagger Authors",
  "Guava" to "The Guava Authors",
  "Reorderable" to "Calvin Liang",
  "Timber" to "Jake Wharton",
)

/** オープンソースのライセンス */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
  val res = LocalResources.current
  val license = remember { res.openRawResource(R.raw.apache_license_2_0).bufferedReader().use { it.readText() } }
  Column(Modifier.fillMaxSize().statusBarsPadding()) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) { Icon(KanadeIcons.Back, contentDescription = stringResource(R.string.action_back)) }
      Text(
        stringResource(R.string.licenses_title),
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(start = 4.dp),
      )
    }
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp + navigationBarHeight())) {
      item {
        Text(
          stringResource(R.string.licenses_intro),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 8.dp),
        )
      }
      items(LIBRARIES) { (name, author) ->
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
          Text(name, style = MaterialTheme.typography.bodyLarge)
          Text("$author · Apache License 2.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      item { HorizontalDivider(Modifier.padding(vertical = 16.dp)) }
      item {
        Text(
          license,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
