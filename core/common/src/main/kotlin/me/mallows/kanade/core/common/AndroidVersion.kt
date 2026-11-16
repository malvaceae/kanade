package me.mallows.kanade.core.common

import android.os.Build

/**
 * OSバージョンによる機能差の判定をまとめる。
 * 画面やサービスで`Build.VERSION.SDK_INT`を直接比較せず、ここを経由する。
 */
class AndroidVersion(val sdkInt: Int) {

  /** 音楽ファイルの読み取りに`READ_MEDIA_AUDIO`を使う (Android 13以降)。それより前は`READ_EXTERNAL_STORAGE` */
  val usesReadMediaAudio: Boolean get() = sdkInt >= TIRAMISU

  /** 通知の表示に`POST_NOTIFICATIONS`の許可が必要 (Android 13以降) */
  val requiresNotificationPermission: Boolean get() = sdkInt >= TIRAMISU

  /** `AudioManager.getDirectProfilesForAttributes`が使える (Android 13以降) */
  val hasDirectProfiles: Boolean get() = sdkInt >= TIRAMISU

  /** USB DACへのビットパーフェクト出力 (`AudioMixerAttributes`) が使える (Android 14以降) */
  val hasBitPerfectApi: Boolean get() = sdkInt >= UPSIDE_DOWN_CAKE

  /** バックグラウンド音声の制限がある (Android 17以降) */
  val hasBackgroundAudioHardening: Boolean get() = sdkInt >= ANDROID_17

  companion object {
    const val S = 31
    const val TIRAMISU = 33
    const val UPSIDE_DOWN_CAKE = 34
    const val ANDROID_17 = 37

    /** 実行中の端末のバージョン */
    val current: AndroidVersion by lazy { AndroidVersion(Build.VERSION.SDK_INT) }
  }
}
