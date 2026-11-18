package me.mallows.kanade.ui.library

import androidx.annotation.StringRes
import me.mallows.kanade.core.domain.text.TextOrder
import me.mallows.kanade.R
import me.mallows.kanade.core.data.db.AlbumSummary
import me.mallows.kanade.core.data.db.GroupSummary
import me.mallows.kanade.core.data.db.TrackEntity
import me.mallows.kanade.core.data.settings.SortSetting

/** 文字の並べ方: 数字・記号 → 英字 → かな → 漢字の順 (詳しくは TextOrder) */
private val collator: Comparator<String> = TextOrder

/** 値がないものは常に後ろ */
private fun <T> nullsLastBy(selector: (T) -> String?): Comparator<T> = Comparator { a, b ->
  val x = selector(a)
  val y = selector(b)
  when {
    x == null && y == null -> 0
    x == null -> 1
    y == null -> -1
    else -> collator.compare(x, y)
  }
}

/** ソートの選択肢 */
interface SortOption<T> {
  val key: String
  @get:StringRes val label: Int
  val comparator: Comparator<T>
}

enum class TrackSort(override val key: String, override val label: Int, override val comparator: Comparator<TrackEntity>) : SortOption<TrackEntity> {
  TITLE("title", R.string.sort_title, nullsLastBy { it.title }),
  ARTIST("artist", R.string.sort_artist, nullsLastBy<TrackEntity> { it.artist }.then(nullsLastBy { it.title })),
  ALBUM("album", R.string.sort_album, nullsLastBy<TrackEntity> { it.album }
    .thenBy { it.discNo ?: 0 }.thenBy { it.trackNo ?: 0 }.then(nullsLastBy { it.title })),
  YEAR("year", R.string.sort_year, compareBy<TrackEntity> { it.year ?: Int.MAX_VALUE }.then(nullsLastBy { it.title })),
  DURATION("duration", R.string.sort_duration, compareBy { it.durationMs }),
  DATE_ADDED("date_added", R.string.sort_date_added, compareBy { it.dateAdded }),
}

enum class AlbumSort(override val key: String, override val label: Int, override val comparator: Comparator<AlbumSummary>) : SortOption<AlbumSummary> {
  TITLE("title", R.string.sort_title, nullsLastBy { it.album }),
  ARTIST("artist", R.string.sort_artist, nullsLastBy<AlbumSummary> { it.artist }.thenBy { it.year ?: 0 }.then(nullsLastBy { it.album })),
  YEAR("year", R.string.sort_year, compareBy<AlbumSummary> { it.year ?: Int.MAX_VALUE }.then(nullsLastBy { it.album })),
  DATE_ADDED("date_added", R.string.sort_date_added, compareBy { it.dateAdded }),
}

/** アーティストの画面のアルバムの並べ方 (アーティストはどれも同じなので除く) */
val ARTIST_ALBUM_SORTS: Array<AlbumSort> by lazy { arrayOf(AlbumSort.YEAR, AlbumSort.TITLE, AlbumSort.DATE_ADDED) }

enum class GroupSort(override val key: String, override val label: Int, override val comparator: Comparator<GroupSummary>) : SortOption<GroupSummary> {
  NAME("name", R.string.sort_name, nullsLastBy { it.name }),
  TRACK_COUNT("track_count", R.string.sort_track_count, compareBy<GroupSummary> { it.trackCount }.then(nullsLastBy { it.name })),
}

/** 保存されたソート条件で並べる。条件がなければ[default] (昇順) */
fun <T, S> List<T>.sortedWith(setting: SortSetting?, options: Array<S>, default: S): List<T> where S : SortOption<T> {
  val option = options.firstOrNull { it.key == setting?.key } ?: default
  val sorted = sortedWith(option.comparator)
  return if (setting?.descending == true) sorted.reversed() else sorted
}
