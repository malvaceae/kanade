plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt)
}

android {
  namespace = "me.mallows.kanade"
  compileSdk = 37
  // 同梱のネイティブライブラリ (libFLAC) からデバッグ情報を取り除くのに使う。decoder-flacと同じ版にする
  ndkVersion = "27.1.12297006"
  defaultConfig {
    applicationId = "me.mallows.kanade"
    minSdk = 31
    targetSdk = 37
    versionCode = 7
    versionName = "1.5.1"
    // 32bitのx86 (古いエミュレーター用) は入れない。実機はARM、エミュレーターはx86_64で足りる
    ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
  }

  // リリース用の鍵。場所とパスワードはリポジトリの外 (~/.gradle/gradle.properties) に書く。
  // 書かれていなければ署名しない (CIなど)
  val releaseStoreFile = providers.gradleProperty("kanade.release.storeFile").orNull
  signingConfigs {
    if (releaseStoreFile != null) {
      create("release") {
        storeFile = file(releaseStoreFile)
        storePassword = providers.gradleProperty("kanade.release.storePassword").get()
        keyAlias = providers.gradleProperty("kanade.release.keyAlias").get()
        // PKCS12の鍵は、鍵のパスワードがキーストアのパスワードと同じ
        keyPassword = providers.gradleProperty("kanade.release.keyPassword").orElse(providers.gradleProperty("kanade.release.storePassword")).get()
      }
    }
  }

  buildTypes {
    release {
      signingConfig = signingConfigs.findByName("release")
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = false
    shaders = false
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Project modules
  implementation(project(":core:common"))
  implementation(project(":core:data"))
  implementation(project(":playback"))

  // Logging
  implementation(libs.timber)

  // DI
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.hilt.lifecycle.viewmodel.compose)

  // アートワークからの配色
  implementation(libs.androidx.palette)

  // ドラッグでの並べ替え
  implementation(libs.reorderable)

  implementation(project(":core:domain"))

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
