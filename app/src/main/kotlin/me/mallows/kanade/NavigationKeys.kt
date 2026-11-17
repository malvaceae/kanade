package me.mallows.kanade

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** ライブラリ (タブで曲・アルバムなどを切り替える) */
@Serializable data object Library : NavKey

/** アルバム・アーティスト・フォルダ・ジャンルの中身。[kind]は GroupKind の名前 */
@Serializable data class GroupDetail(val kind: String, val key: String?, val title: String) : NavKey

/** プレイリストの中身 */
@Serializable data class PlaylistDetail(val id: Long) : NavKey

/** ホームの項目 (すべての曲・アルバムなど) の一覧。[name] は LibraryTab の名前 */
@Serializable data class Category(val name: String) : NavKey

/** オープンソースのライセンス */
@Serializable data object Licenses : NavKey

/** スキャン対象のフォルダの選択 */
@Serializable data object ScanFolders : NavKey

/** 検索 */
@Serializable data object Search : NavKey

/** イコライザー */
@Serializable data object Equalizer : NavKey

/** 再生キュー */
@Serializable data object Queue : NavKey

/** プレイヤー画面 */
@Serializable data object Player : NavKey
