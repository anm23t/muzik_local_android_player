import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/** ABIs shipped in the APK, the same as in core:tags (docs/decisions/005-tag-library.md). */
val supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")

android {
    namespace = "org.muzzzlo.player.core.playback"
    compileSdk = 35
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters += supportedAbis
        }
        externalNativeBuild {
            cmake {
                // The C++ runtime is linked into libalac_jni.so, so the APK does not need libc++_shared.so.
                arguments += "-DANDROID_STL=c++_static"
            }
        }
    }

    buildTypes {
        release {
            externalNativeBuild {
                cmake {
                    arguments += "-DCMAKE_BUILD_TYPE=MinSizeRel"
                }
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = libs.versions.cmake.get()
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

// Host build of the same CMake project, so JVM unit tests can load the JNI bridge (libalac_jni.dylib / .so).
// Works like the TagLib host build in core:tags: CMake and Ninja from the Android SDK, the host C++ compiler and
// jni.h of the JDK that runs Gradle.
val nativeSourceDir = layout.projectDirectory.dir("src/main/cpp")
val hostBuildDir = layout.buildDirectory.dir("native-host")
val sdkCmakeBin = androidComponents.sdkComponents.sdkDirectory.map { it.dir("cmake/${libs.versions.cmake.get()}/bin") }

/** Optional macOS SDK for the host build; the same `tags.host.osxSysroot` in local.properties as core:tags uses. */
val hostOsxSysroot: String? =
    rootProject.file("local.properties").takeIf { it.exists() }
        ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }
        ?.getProperty("tags.host.osxSysroot")

val configureHostAlac by tasks.registering(Exec::class) {
    description = "Configures the host build of libalac_jni for JVM unit tests."
    inputs.file(nativeSourceDir.file("CMakeLists.txt"))
    inputs.property("osxSysroot", hostOsxSysroot.orEmpty())
    outputs.file(hostBuildDir.map { it.file("build.ninja") })
    doFirst {
        val cmakeBin = sdkCmakeBin.get().asFile
        check(cmakeBin.resolve("cmake").exists()) {
            "CMake ${libs.versions.cmake.get()} is missing from the Android SDK. " +
                "Install it: sdkmanager \"cmake;${libs.versions.cmake.get()}\""
        }
        commandLine(
            cmakeBin.resolve("cmake").path,
            "-S", nativeSourceDir.asFile.path,
            "-B", hostBuildDir.get().asFile.path,
            "-G", "Ninja",
            "-DCMAKE_MAKE_PROGRAM=${cmakeBin.resolve("ninja").path}",
            "-DCMAKE_BUILD_TYPE=Release",
            "-DALAC_JNI_HOST=ON",
            "-DJNI_INCLUDE_ROOT=${File(System.getProperty("java.home"), "include").path}",
            *listOfNotNull(hostOsxSysroot?.let { "-DCMAKE_OSX_SYSROOT=$it" }).toTypedArray(),
        )
    }
}

val buildHostAlac by tasks.registering(Exec::class) {
    description = "Builds libalac_jni for the host so JVM unit tests can load it."
    dependsOn(configureHostAlac)
    inputs.dir(nativeSourceDir).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(hostBuildDir)
    doFirst {
        commandLine(sdkCmakeBin.get().asFile.resolve("cmake").path, "--build", hostBuildDir.get().asFile.path)
    }
}

tasks.withType<Test>().configureEach {
    dependsOn(buildHostAlac)
    systemProperty("java.library.path", hostBuildDir.get().asFile.path)
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation(libs.junit5)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
