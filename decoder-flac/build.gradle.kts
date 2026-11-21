// Media3のFLACデコーダー拡張 (libraries/decoder_flac) を、Media3と同じ版のソースから作る。
// Media3はこの拡張をMavenで配っていないため、Javaとjniのソースをこのモジュールに置き、
// libFLAC (BSDライセンス) はgitのサブモジュール (src/main/jni/libflac) から一緒にビルドする。
// Media3の版を上げたら、同じ版のソースで置き換えること
plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "androidx.media3.decoder.flac"
  compileSdk {
    version = release(37) { minorApiLevel = 2 }
  }
  ndkVersion = "30.0.16248370"
  defaultConfig {
    minSdk = 24
    consumerProguardFiles("consumer-rules.pro")
    // アプリと同じABIだけ作る (x86は作らない)
    ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    externalNativeBuild {
      cmake {
        arguments("-DWITH_OGG=OFF", "-DINSTALL_MANPAGES=OFF", "-DBUILD_PROGRAMS=OFF", "-DBUILD_EXAMPLES=OFF", "-DBUILD_TESTING=OFF", "-DBUILD_DOCS=OFF")
        targets("flacJNI")
      }
    }
  }
  externalNativeBuild {
    cmake {
      path = file("src/main/jni/CMakeLists.txt")
      version = "4.1.2"
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

dependencies {
  api(libs.media3.decoder)
  implementation(libs.media3.exoplayer)
  implementation(libs.androidx.annotation)
  compileOnly(libs.checker.qual)
  // 依存のクラスが参照する Kotlin のアノテーション (MigrationStatus) を、javac が読めるようにする。APKには入らない
  compileOnly(libs.kotlin.annotations.jvm)
}

// Media3のソースは手を加えずに使うので、Lintにはかけない (見つかっても直さないため)
tasks.matching { it.name.startsWith("lint") }.configureEach { enabled = false }
