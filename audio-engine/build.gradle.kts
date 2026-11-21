plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "me.mallows.kanade.audio"
  compileSdk {
    version = release(37) { minorApiLevel = 2 }
  }
  defaultConfig {
    minSdk = 24
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  api(libs.media3.common)
  implementation(libs.timber)

  testImplementation(libs.junit)
}
