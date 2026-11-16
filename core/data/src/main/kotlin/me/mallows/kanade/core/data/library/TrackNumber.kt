package me.mallows.kanade.core.data.library

/** ディスク番号とトラック番号 */
data class TrackNumber(val disc: Int?, val track: Int?) {
  companion object {
    /**
     * MediaStoreのTRACK列を分解する。
     * 1000以上の値は「ディスク番号 × 1000 + トラック番号」として扱う (例: 2003 → 2枚目の3曲目)。
     */
    fun fromMediaStore(value: Int?): TrackNumber {
      if (value == null || value <= 0) return TrackNumber(null, null)
      if (value < 1000) return TrackNumber(null, value)
      val track = value % 1000
      return TrackNumber(value / 1000, track.takeIf { it > 0 })
    }
  }
}
