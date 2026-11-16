package me.mallows.kanade

import android.app.Application
import android.content.pm.ApplicationInfo
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class KanadeApplication : Application() {

  override fun onCreate() {
    super.onCreate()
    // デバッグビルドのときだけログを出す
    if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
      Timber.plant(Timber.DebugTree())
    }
  }
}
