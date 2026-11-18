// Media3のFLACデコーダー拡張 (libraries/decoder_flac) を、Media3と同じ版のソースから作る。
// Media3はこの拡張をMavenで配っていないため、Javaとjniのソースをこのモジュールに置き、
// libFLAC (BSDライセンス) はgitのサブモジュール (src/main/jni/libflac) から一緒にビルドする。
// Media3の版を上げたら、同じ版のソースで置き換えること
plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "androidx.media3.decoder.flac"
  compileSdk = 37
  ndkVersion = "27.1.12297006"
  defaultConfig {
    minSdk = 31
    consumerProguardFiles("consumer-rules.pro")
    // アプリと同じABIだけ作る (x86は作らない)
    ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    externalNativeBuild {
      cmake {
        // 32bitのABIでfseekoの有無の判定が外れ、stdio.hと食い違うため、あることにする (API 24以降はある)
        arguments("-DHAVE_FSEEKO=1", "-DWITH_OGG=OFF", "-DINSTALL_MANPAGES=OFF", "-DBUILD_PROGRAMS=OFF", "-DBUILD_EXAMPLES=OFF", "-DBUILD_TESTING=OFF", "-DBUILD_DOCS=OFF")
        targets("flacJNI")
      }
    }
  }
  externalNativeBuild {
    cmake {
      path = file("src/main/jni/CMakeLists.txt")
      version = "3.22.1"
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
}

// Media3のソースは手を加えずに使うので、Lintにはかけない (見つかっても直さないため)
tasks.matching { it.name.startsWith("lint") }.configureEach { enabled = false }
