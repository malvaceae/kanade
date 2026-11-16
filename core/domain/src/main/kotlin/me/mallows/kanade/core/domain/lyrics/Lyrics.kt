package me.mallows.kanade.core.domain.lyrics

/** 歌詞の1行。[timeMs] は時刻付き (LRC) のときだけ */
data class LyricLine(val timeMs: Long?, val text: String)

/** 歌詞。時刻付きなら [synced] で、行は時刻の順 */
data class Lyrics(val lines: List<LyricLine>, val synced: Boolean) {

  /** [positionMs] の時点で歌っている行の番号。時刻付きでないか、最初の行より前なら -1 */
  fun indexAt(positionMs: Long): Int {
    if (!synced) return -1
    var lo = 0
    var hi = lines.size - 1
    var found = -1
    while (lo <= hi) {
      val mid = (lo + hi) / 2
      if ((lines[mid].timeMs ?: 0) <= positionMs) { found = mid; lo = mid + 1 } else hi = mid - 1
    }
    return found
  }

  companion object {
    private val TIME = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val OFFSET = Regex("""^\[offset:\s*([+-]?\d+)\s*]$""", RegexOption.IGNORE_CASE)
    private val TAG = Regex("""^\[[a-zA-Z]+:.*]$""")

    /** 歌詞の文字列を読む。LRCの時刻があれば時刻付き、無ければ行ごとの文字にする。空ならnull */
    fun parse(text: String?): Lyrics? {
      val raw = text?.replace("\r\n", "\n")?.replace('\r', '\n')?.trim()
      if (raw.isNullOrEmpty()) return null
      var offset = 0L
      val timed = mutableListOf<LyricLine>()
      val plain = mutableListOf<String>()
      for (line in raw.lines()) {
        val l = line.trim()
        OFFSET.find(l)?.let { offset = it.groupValues[1].toLong(); continue }
        val times = mutableListOf<Long>()
        var rest = l
        while (true) {
          val m = TIME.find(rest) ?: break
          if (m.range.first != 0) break
          val min = m.groupValues[1].toLong()
          val sec = m.groupValues[2].toLong()
          val frac = m.groupValues[3]
          val ms = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 100
            2 -> frac.toLong() * 10
            else -> frac.take(3).toLong()
          }
          times += min * 60_000 + sec * 1_000 + ms
          rest = rest.substring(m.range.last + 1)
        }
        if (times.isNotEmpty()) {
          times.forEach { timed += LyricLine(it, rest.trim()) }
        } else if (!TAG.matches(l)) {
          plain += l
        }
      }
      if (timed.isNotEmpty()) {
        // LRCのoffsetは、正の値で歌詞を早める
        val lines = timed.map { it.copy(timeMs = ((it.timeMs ?: 0) - offset).coerceAtLeast(0)) }.sortedBy { it.timeMs }
        return Lyrics(lines, synced = true)
      }
      val lines = plain.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }
      return if (lines.isEmpty()) null else Lyrics(lines.map { LyricLine(null, it) }, synced = false)
    }
  }
}
