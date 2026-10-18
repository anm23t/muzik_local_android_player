# Builds TagLib (static, only the formats the app reads and writes) and the JNI bridge libtaglib_jni.
#
# Android: driven by the Android Gradle plugin (externalNativeBuild in core/tags/build.gradle.kts).
# Host (JVM unit tests): driven by the buildHostTagLib Gradle task, which passes -DTAGLIB_JNI_HOST=ON
# and JNI_INCLUDE_ROOT (the include folder of the JDK running Gradle).
cmake_minimum_required(VERSION 3.22)
project(taglib_jni CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(TAGLIB_SRC ${CMAKE_CURRENT_SOURCE_DIR}/taglib)
if(NOT EXISTS ${TAGLIB_SRC}/CMakeLists.txt)
  message(FATAL_ERROR "TagLib sources are missing. Run: git submodule update --init --recursive")
endif()

# TagLib options: a static library without bindings, tests, examples or zlib.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(BUILD_BINDINGS OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(VISIBILITY_HIDDEN ON CACHE BOOL "" FORCE)
set(WITH_ZLIB OFF CACHE BOOL "" FORCE)
# Formats the app does not need (docs/decisions/005-tag-library.md). MPEG, MP4, Ogg (Vorbis, Opus) and FLAC stay on.
foreach(opt WITH_APE WITH_ASF WITH_DSF WITH_MATROSKA WITH_MOD WITH_RIFF WITH_SHORTEN WITH_TRUEAUDIO)
  set(${opt} OFF CACHE BOOL "" FORCE)
endforeach()

add_compile_options(-ffunction-sections -fdata-sections)
add_subdirectory(${TAGLIB_SRC} taglib EXCLUDE_FROM_ALL)

add_library(taglib_jni SHARED taglib_jni.cpp)
target_link_libraries(taglib_jni PRIVATE tag)
target_compile_options(taglib_jni PRIVATE -fvisibility=hidden -Wall -Wextra)

# TagLib exports its include paths only for installed builds, so list the in-tree header folders.
file(GLOB_RECURSE TAGLIB_HEADERS ${TAGLIB_SRC}/taglib/*.h)
foreach(header ${TAGLIB_HEADERS})
  get_filename_component(dir ${header} DIRECTORY)
  list(APPEND TAGLIB_INCLUDE_DIRS ${dir})
endforeach()
list(REMOVE_DUPLICATES TAGLIB_INCLUDE_DIRS)
target_include_directories(taglib_jni PRIVATE
  ${TAGLIB_INCLUDE_DIRS}
  ${CMAKE_CURRENT_BINARY_DIR}/taglib
  ${CMAKE_CURRENT_BINARY_DIR}/taglib/taglib)

if(ANDROID)
  # Drop unused sections, hide the static C++ runtime and TagLib symbols, strip the result.
  target_link_options(taglib_jni PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL -s)
elseif(TAGLIB_JNI_HOST)
  # Only jni.h is needed; FindJNI also requires AWT, so point at the JDK headers directly.
  if(NOT JNI_INCLUDE_ROOT)
    message(FATAL_ERROR "JNI_INCLUDE_ROOT must point at <JDK>/include")
  endif()
  target_include_directories(taglib_jni PRIVATE ${JNI_INCLUDE_ROOT} ${JNI_INCLUDE_ROOT}/darwin ${JNI_INCLUDE_ROOT}/linux)
  if(APPLE)
    target_link_options(taglib_jni PRIVATE -Wl,-dead_strip)
  else()
    target_link_options(taglib_jni PRIVATE -Wl,--gc-sections)
  endif()
endif()
