package me.mallows.kanade.core.domain.text

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/**
 * 曲名などの並べ方。文字コード順に近い「数字・記号 → 英字 → かな → 漢字 → その他」の順に分け、
 * 同じ種類の中は日本語の照合順序で並べる (かなとカナ、英字の大小、全角と半角は区別しない)。
 */
object TextOrder : Comparator<String> {

  private val collator: Collator = Collator.getInstance(Locale.JAPANESE).apply {
    strength = Collator.SECONDARY
    // Androidでは FULL_DECOMPOSITION が使えないので、全角・半角は比べる前に NFKC でそろえる
    decomposition = Collator.CANONICAL_DECOMPOSITION
  }

  private fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)

  /** 先頭の文字の種類 (小さいほど前) */
  fun groupOf(text: String): Int {
    val c = text.trimStart().firstOrNull() ?: return 0
    val block = Character.UnicodeBlock.of(c)
    return when {
      c.isLetter() && isLatin(c) -> 1
      block == Character.UnicodeBlock.HIRAGANA ||
        block == Character.UnicodeBlock.KATAKANA ||
        block == Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS ||
        (block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS && c in 'ｦ'..'ﾟ') -> 2
      block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
        block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS -> 3
      c.isLetter() -> 4
      else -> 0
    }
  }

  /**
   * ラテン文字か (英字・アクセント付き・全角の英字など)。`Character.UnicodeScript`はAndroid 7.0 (API 24) からなので、
   * Android 6.0でも動くよう、範囲で調べる。文字の種類は`isLetter()`で絞ってから呼ぶ
   */
  private fun isLatin(c: Char): Boolean = when (c.code) {
    in 0x41..0x5A, in 0x61..0x7A, 0xAA, 0xBA, in 0xC0..0xD6, in 0xD8..0xF6, in 0xF8..0x2B8,
    in 0x1E00..0x1EFF, in 0x2C60..0x2C7F, in 0xA720..0xA7FF, in 0xFF21..0xFF3A, in 0xFF41..0xFF5A -> true
    else -> false
  }

  override fun compare(a: String, b: String): Int {
    val g = groupOf(a).compareTo(groupOf(b))
    if (g != 0) return g
    val c = collator.compare(normalize(a), normalize(b))
    return if (c != 0) c else a.compareTo(b)
  }
}
