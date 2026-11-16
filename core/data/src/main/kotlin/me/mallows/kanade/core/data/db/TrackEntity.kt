package me.mallows.kanade.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 端末内の1曲。MediaStoreから取り込んだ情報を保存する */
@Entity(
  tableName = "track",
  indices = [Index(value = ["media_store_id"], unique = true), Index("album"), Index("artist")],
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
  /** 「Music/Album/」のようなストレージ内のフォルダー */
  @ColumnInfo(name = "relative_path") val relativePath: String? = null,
  val genre: String? = null,
)
