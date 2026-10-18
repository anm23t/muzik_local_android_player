# Builds TagLib (static, only the formats the app needs) plus a tiny JNI bridge.
# Host: cmake -S native -B build/native-host -G Ninja -DTAGLIB_SRC=build/src/taglib
# Android: add -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake -DANDROID_ABI=arm64-v8a
cmake_minimum_required(VERSION 3.22)
project(taglib_jni CXX)

set(CMAKE_CXX_STANDARD 17)
set(TAGLIB_SRC "" CACHE PATH "TagLib source checkout")

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(BUILD_BINDINGS OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(VISIBILITY_HIDDEN ON CACHE BOOL "" FORCE)
set(WITH_ZLIB OFF CACHE BOOL "" FORCE)
# Formats the app does not need: switching them off is the main size lever.
foreach(opt WITH_APE WITH_ASF WITH_DSF WITH_MATROSKA WITH_MOD WITH_RIFF WITH_SHORTEN WITH_TRUEAUDIO)
  set(${opt} OFF CACHE BOOL "" FORCE)
endforeach()
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
add_compile_options(-ffunction-sections -fdata-sections)
add_subdirectory(${TAGLIB_SRC} taglib EXCLUDE_FROM_ALL)

add_library(taglib_jni SHARED taglib_jni.cpp)
target_link_libraries(taglib_jni PRIVATE tag)
# TagLib only exports its include paths for installed builds; list the in-tree header folders.
file(GLOB_RECURSE TAGLIB_HEADERS ${TAGLIB_SRC}/taglib/*.h)
foreach(h ${TAGLIB_HEADERS})
  get_filename_component(dir ${h} DIRECTORY)
  list(APPEND TAGLIB_INCLUDE_DIRS ${dir})
endforeach()
list(REMOVE_DUPLICATES TAGLIB_INCLUDE_DIRS)
target_include_directories(taglib_jni PRIVATE ${TAGLIB_INCLUDE_DIRS} ${CMAKE_BINARY_DIR}/taglib ${CMAKE_BINARY_DIR}/taglib/taglib)
target_compile_options(taglib_jni PRIVATE -fvisibility=hidden)

if(ANDROID)
  target_link_options(taglib_jni PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL -s)
else()
  # Only the JNI headers are needed; FindJNI also insists on AWT, so point at the JDK directly.
  set(JDK_HOME "$ENV{JAVA_HOME}" CACHE PATH "JDK used for jni.h")
  target_include_directories(taglib_jni PRIVATE ${JDK_HOME}/include ${JDK_HOME}/include/darwin ${JDK_HOME}/include/linux)
  if(APPLE)
    target_link_options(taglib_jni PRIVATE -Wl,-dead_strip)
  endif()
endif()
