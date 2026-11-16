plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt)
  alias(libs.plugins.room)
}

android {
  namespace = "me.mallows.kanade.core.data"
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

room {
  schemaDirectory("$projectDir/schemas")
}

dependencies {
  implementation(project(":core:common"))
  implementation(project(":core:domain"))

  implementation(libs.room.runtime)
  ksp(libs.room.compiler)
  implementation(libs.datastore.preferences)
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.timber)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
