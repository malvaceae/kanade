package me.mallows.kanade

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** 全曲一覧 */
@Serializable data object Library : NavKey

/** プレイヤー画面 */
@Serializable data object Player : NavKey
