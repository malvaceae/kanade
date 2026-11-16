package me.mallows.kanade.core.data.db

/** アルバムの一覧の1行 */
data class AlbumSummary(
  val albumId: Long?,
  val album: String?,
  val artist: String?,
  val trackCount: Int,
  val durationMs: Long,
  val year: Int?,
  /** アートワークを読むための代表曲のURI */
  val artUri: String,
  val dateAdded: Long,
)

/** アーティスト・フォルダー・ジャンルなど、名前でまとめた一覧の1行 */
data class GroupSummary(
  val name: String?,
  val trackCount: Int,
  val albumCount: Int,
  val durationMs: Long,
  val artUri: String,
)
