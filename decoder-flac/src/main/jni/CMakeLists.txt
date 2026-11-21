#
# Copyright 2024 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

cmake_minimum_required(VERSION 3.21.0 FATAL_ERROR)

# Enable C++11 features.
set(CMAKE_CXX_STANDARD 11)

# Define project name for your JNI module
project(libflacJNI C CXX)

# Suppress pointer cast alignment warnings from upstream libflac source.
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wno-cast-align")

set(libflac_jni_root "${CMAKE_CURRENT_SOURCE_DIR}")

# fseeko/ftello は、Android 7.0 (API 24) 以降は32bitのABIでも使える (stdio.hが宣言し、実体はfseeko64に名前が変わる)。
# libFLACは32bitのAndroidで、API 24より前だと見て fseeko を無効にする (compat.h が fseeko を fseek に置き換える) が、
# NDKでは CMAKE_SYSTEM_VERSION が常に 1 なので、いつでも無効になる。すると、stdio.hの宣言とぶつかってビルドが通らない。
# config.h では HAVE_FSEEKO がコメントになる (64bitでは値のない #define になる) ので、同じ形 (値なし) の定義をコンパイラへ渡して「ある」と伝える
add_compile_definitions("HAVE_FSEEKO=")

# Build libflac.
add_subdirectory("${libflac_jni_root}/libflac"
                 EXCLUDE_FROM_ALL)

# Build libflacJNI.
add_library(flacJNI
            SHARED
            flac_jni.cc
            flac_parser.cc)

# Add the include directory from libflac.
include_directories("${libflac_jni_root}/libflac/include")

# Locate NDK log library.
find_library(android_log_lib log)

# Link libflacJNI against used libraries.
target_link_libraries(flacJNI
                      PRIVATE android
                      PRIVATE FLAC
                      PRIVATE ${android_log_lib})
