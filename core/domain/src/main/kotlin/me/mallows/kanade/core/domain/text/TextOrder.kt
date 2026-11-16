package me.mallows.kanade.core.domain.text

import java.text.Collator
import java.util.Locale

/**
 * 曲名などの並べ方。文字コード順に近い「数字・記号 → 英字 → かな → 漢字 → その他」の順に分け、
 * 同じ種類の中は日本語の照合順序で並べる (かなとカナ、英字の大小、全角と半角は区別しない)。
 */
object TextOrder : Comparator<String> {

  private val collator: Collator = Collator.getInstance(Locale.JAPANESE).apply {
    strength = Collator.SECONDARY
    decomposition = Collator.FULL_DECOMPOSITION
  }

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

  private fun isLatin(c: Char): Boolean {
    val script = Character.UnicodeScript.of(c.code)
    return script == Character.UnicodeScript.LATIN
  }

  override fun compare(a: String, b: String): Int {
    val g = groupOf(a).compareTo(groupOf(b))
    if (g != 0) return g
    val c = collator.compare(a, b)
    return if (c != 0) c else a.compareTo(b)
  }
}
