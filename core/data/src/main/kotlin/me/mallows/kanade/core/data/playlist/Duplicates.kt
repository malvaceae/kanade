package me.mallows.kanade.core.data.playlist

import me.mallows.kanade.core.data.db.TrackEntity

/**
 * プレイリストの重複を見分ける鍵。アーティスト・曲名・長さ (秒) がすべて同じなら同じ曲とみなす
 * (同じ曲の別のファイル、別の形式で入れたものも重複になる)
 */
data class DuplicateKey(val artist: String, val title: String, val seconds: Long)

val TrackEntity.duplicateKey: DuplicateKey get() = DuplicateKey(artist.orEmpty(), title, durationMs / 1000)
