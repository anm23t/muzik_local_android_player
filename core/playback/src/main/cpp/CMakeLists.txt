# Builds the Apple ALAC decoder (only the decoding half of the codec) and the JNI bridge libalac_jni.
# Decision: docs/decisions/012-alac-decoder.md.
#
# Android: driven by the Android Gradle plugin (externalNativeBuild in core/playback/build.gradle.kts).
# Host (JVM unit tests): driven by the buildHostAlac Gradle task, which passes -DALAC_JNI_HOST=ON
# and JNI_INCLUDE_ROOT (the include folder of the JDK running Gradle).
cmake_minimum_required(VERSION 3.22)
project(alac_jni C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Vendored decoder sources with a small safety patch (alac/README.md).
set(ALAC_SRC ${CMAKE_CURRENT_SOURCE_DIR}/alac)

add_library(alac_jni SHARED
  alac_jni.cpp
  ${ALAC_SRC}/ALACDecoder.cpp
  ${ALAC_SRC}/ALACBitUtilities.c
  ${ALAC_SRC}/EndianPortable.c
  ${ALAC_SRC}/ag_dec.c
  ${ALAC_SRC}/dp_dec.c
  ${ALAC_SRC}/matrix_dec.c)
target_include_directories(alac_jni PRIVATE ${ALAC_SRC})
# The codec detects little endian only on x86 and would read the big-endian cookie and bit stream wrong on ARM.
# Every ABI the app ships and every host it is tested on is little endian.
target_compile_definitions(alac_jni PRIVATE TARGET_RT_LITTLE_ENDIAN=1)
target_compile_options(alac_jni PRIVATE -fvisibility=hidden -ffunction-sections -fdata-sections)
# Warnings of the Apple sources are not ours to fix; the bridge is built with -Wall -Wextra.
set_source_files_properties(alac_jni.cpp PROPERTIES COMPILE_OPTIONS "-Wall;-Wextra")

if(ANDROID)
  # Drop unused sections, hide the static C++ runtime, strip the result.
  target_link_options(alac_jni PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL -s)
elseif(ALAC_JNI_HOST)
  # Only jni.h is needed; FindJNI also requires AWT, so point at the JDK headers directly.
  if(NOT JNI_INCLUDE_ROOT)
    message(FATAL_ERROR "JNI_INCLUDE_ROOT must point at <JDK>/include")
  endif()
  target_include_directories(alac_jni PRIVATE ${JNI_INCLUDE_ROOT} ${JNI_INCLUDE_ROOT}/darwin ${JNI_INCLUDE_ROOT}/linux)
endif()
