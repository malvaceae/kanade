plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt)
}

android {
  namespace = "me.mallows.kanade.playback"
  compileSdk = 37
  defaultConfig {
    minSdk = 31
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
  implementation(project(":core:common"))
  implementation(project(":core:data"))

  api(libs.media3.exoplayer)
  api(libs.media3.session)
  implementation(libs.kotlinx.coroutines.guava)
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.timber)

  testImplementation(libs.junit)
}
