package me.mallows.kanade.core.domain.m3u

import java.net.URLDecoder

/** M3U・M3U8のプレイリストの1曲 */
data class M3uEntry(val path: String, val durationSec: Long? = null, val title: String? = null)

/** M3U・M3U8の読み書き */
object M3u {

  /** 本文を読む。`#EXTINF` 以外の `#` で始まる行と空行は飛ばす */
  fun parse(text: String): List<M3uEntry> {
    val entries = mutableListOf<M3uEntry>()
    var duration: Long? = null
    var title: String? = null
    for (raw in text.removePrefix("\uFEFF").lineSequence()) {
      val line = raw.trim()
      when {
        line.isEmpty() -> continue
        line.startsWith("#EXTINF:", ignoreCase = true) -> {
          val body = line.substring(8)
          val comma = body.indexOf(',')
          duration = (if (comma >= 0) body.substring(0, comma) else body).trim().toLongOrNull()?.takeIf { it >= 0 }
          title = if (comma >= 0) body.substring(comma + 1).trim().ifEmpty { null } else null
        }
        line.startsWith("#") -> continue
        else -> {
          entries += M3uEntry(line, duration, title)
          duration = null
          title = null
        }
      }
    }
    return entries
  }

  /** M3U8 (UTF-8) の本文を作る */
  fun format(entries: List<M3uEntry>): String = buildString {
    append("#EXTM3U\n")
    for (e in entries) {
      append("#EXTINF:").append(e.durationSec ?: -1).append(',').append(e.title.orEmpty()).append('\n')
      append(e.path).append('\n')
    }
  }
}

/**
 * プレイリストに書かれたパスを、端末内の曲に結びつける。
 * 書き出した端末とフォルダーの位置が違ってもよいよう、ファイル名で探し、
 * 同じ名前が複数あれば、フォルダーの並びが後ろから最も長く一致するものを選ぶ。
 */
class PathMatcher<T>(files: List<Pair<T, String>>) {

  private val byName: Map<String, List<Pair<T, List<String>>>> = files
    .map { (id, path) -> id to segments(path) }
    .filter { it.second.isNotEmpty() }
    .groupBy { it.second.last() }

  fun match(path: String): T? {
    val wanted = segments(path)
    val name = wanted.lastOrNull() ?: return null
    val candidates = byName[name] ?: return null
    if (candidates.size == 1) return candidates.first().first
    return candidates.maxByOrNull { (_, segs) -> commonSuffix(segs, wanted) }?.first
  }

  private fun commonSuffix(a: List<String>, b: List<String>): Int {
    var n = 0
    while (n < a.size && n < b.size && a[a.size - 1 - n] == b[b.size - 1 - n]) n++
    return n
  }

  companion object {
    /** 区切りをそろえ、大文字と小文字を区別せずに比べるための部品に分ける */
    fun segments(path: String): List<String> {
      var p = path.trim().removePrefix("file://")
      if ('%' in p) p = runCatching { URLDecoder.decode(p.replace("+", "%2B"), "UTF-8") }.getOrDefault(p)
      return p.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }.map { it.lowercase() }
    }
  }
}
