package me.mallows.kanade.ui.player

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * アートワークから背景色を作る。文字が読めるよう、暗い色に寄せる。
 * アートワークがないときは[fallback]を返す。
 */
@Composable
fun rememberArtworkBackground(artworkData: ByteArray?, key: Any?, fallback: Color, base: Color): Color {
  val color by produceState(initialValue = fallback, key) {
    val data = artworkData
    value = if (data == null) {
      // 曲を変えた直後は、アートワークが少し遅れて届くことがある。すぐに暗い色へ戻すと
      // 「前の色 → 暗い色 → 新しい色」とちらつくので、少し待ってから戻す (届けばこの処理は取り消される)
      delay(400)
      fallback
    } else {
      withContext(Dispatchers.Default) {
        runCatching {
          val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return@runCatching null
          val palette = Palette.from(bitmap).maximumColorCount(16).generate()
          val swatch = palette.darkMutedSwatch ?: palette.mutedSwatch ?: palette.dominantSwatch
          swatch?.let { lerp(base, Color(it.rgb), 0.45f) }
        }.getOrNull() ?: fallback
      }
    }
  }
  return color
}
