package me.mallows.kanade.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 端末内の1曲 (音声または動画)。MediaStoreから取り込んだ情報を保存する */
@Entity(
  tableName = "track",
  indices = [Index(value = ["media_store_id"], unique = true), Index("album"), Index("artist"), Index("excluded")],
)
data class TrackEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  @ColumnInfo(name = "media_store_id") val mediaStoreId: Long,
  val uri: String,
  val title: String,
  val artist: String?,
  @ColumnInfo(name = "album_artist") val albumArtist: String?,
  val album: String?,
  @ColumnInfo(name = "album_id") val albumId: Long? = null,
  val year: Int?,
  @ColumnInfo(name = "track_no") val trackNo: Int?,
  @ColumnInfo(name = "disc_no") val discNo: Int?,
  @ColumnInfo(name = "duration_ms") val durationMs: Long,
  val mime: String?,
  @ColumnInfo(name = "date_added") val dateAdded: Long,
  @ColumnInfo(name = "date_modified") val dateModified: Long,
  /** 「Music/Album/」のようなストレージ内のフォルダ */
  @ColumnInfo(name = "relative_path") val relativePath: String? = null,
  val genre: String? = null,
  /** MediaStoreのボリューム名 ("external_primary"、SDカードは"1234-abcd"など) */
  val volume: String? = null,
  /**
   * 端末にはあるが、スキャン対象のフォルダの外にある曲。消さずに残し (プレイリストの登録と再生回数を守るため)、
   * ライブラリの一覧には出さない
   */
  @ColumnInfo(defaultValue = "0") val excluded: Boolean = false,
  /**
   * 音声か動画か ([MEDIA_TYPE_AUDIO]・[MEDIA_TYPE_VIDEO])。
   * MediaStoreのIDは、音声と動画で同じ番号の空間を共有する (どちらも`files`の表の行) ので、`media_store_id`はこのままで1つに決まる。
   * 再生に使うURIだけが、音声と動画で違う集まり (`uri`) を指す
   */
  @ColumnInfo(name = "media_type", defaultValue = "0") val mediaType: Int = MEDIA_TYPE_AUDIO,
) {
  val isVideo: Boolean get() = mediaType == MEDIA_TYPE_VIDEO
}

const val MEDIA_TYPE_AUDIO = 0
const val MEDIA_TYPE_VIDEO = 1
