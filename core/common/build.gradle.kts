plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "me.mallows.kanade.core.common"
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
  testImplementation(libs.junit)
}
