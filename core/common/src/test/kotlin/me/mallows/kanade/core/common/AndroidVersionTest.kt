package me.mallows.kanade.core.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidVersionTest {

  @Test
  fun android12() {
    val v = AndroidVersion(AndroidVersion.S)
    assertFalse(v.usesReadMediaAudio)
    assertFalse(v.requiresNotificationPermission)
    assertFalse(v.hasDirectProfiles)
    assertFalse(v.hasBitPerfectApi)
    assertFalse(v.hasBackgroundAudioHardening)
  }

  @Test
  fun android14() {
    val v = AndroidVersion(AndroidVersion.UPSIDE_DOWN_CAKE)
    assertTrue(v.usesReadMediaAudio)
    assertTrue(v.hasBitPerfectApi)
    assertFalse(v.hasBackgroundAudioHardening)
  }

  @Test
  fun android17() {
    val v = AndroidVersion(AndroidVersion.ANDROID_17)
    assertTrue(v.usesReadMediaAudio)
    assertTrue(v.requiresNotificationPermission)
    assertTrue(v.hasDirectProfiles)
    assertTrue(v.hasBitPerfectApi)
    assertTrue(v.hasBackgroundAudioHardening)
  }
}
