import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/** ABIs shipped in the APK (docs/decisions/005-tag-library.md); x86_64 covers emulators. */
val supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")

android {
    namespace = "org.muzzzlo.player.core.tags"
    compileSdk = 35
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters += supportedAbis
        }
        externalNativeBuild {
            cmake {
                // The C++ runtime is linked into libtaglib_jni.so, so the APK does not need libc++_shared.so.
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

    sourceSets {
        // The instrumented smoke test reads the same small fixtures as the JVM tests.
        getByName("androidTest").resources.srcDir("src/test/resources")
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

// Host build of the same CMake project, so JVM unit tests can load the JNI bridge (libtaglib_jni.dylib / .so).
// It uses the CMake (with its bundled Ninja) from the Android SDK, the host C++ compiler and jni.h of the JDK
// that runs Gradle.
val nativeSourceDir = layout.projectDirectory.dir("src/main/cpp")
val hostBuildDir = layout.buildDirectory.dir("native-host")
val sdkCmakeBin = androidComponents.sdkComponents.sdkDirectory.map { it.dir("cmake/${libs.versions.cmake.get()}/bin") }

/**
 * Optional macOS SDK for the host build, from `tags.host.osxSysroot` in local.properties. Needed when the
 * Command Line Tools linker is older than the newest installed SDK ("tapi error: unknown architecture").
 */
val hostOsxSysroot: String? =
    rootProject.file("local.properties").takeIf { it.exists() }
        ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }
        ?.getProperty("tags.host.osxSysroot")

val configureHostTagLib by tasks.registering(Exec::class) {
    description = "Configures the host build of libtaglib_jni for JVM unit tests."
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
            "-DTAGLIB_JNI_HOST=ON",
            "-DJNI_INCLUDE_ROOT=${File(System.getProperty("java.home"), "include").path}",
            *listOfNotNull(hostOsxSysroot?.let { "-DCMAKE_OSX_SYSROOT=$it" }).toTypedArray(),
        )
    }
}

val buildHostTagLib by tasks.registering(Exec::class) {
    description = "Builds libtaglib_jni for the host so JVM unit tests can load it."
    dependsOn(configureHostTagLib)
    inputs.dir(nativeSourceDir).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(hostBuildDir)
    doFirst {
        commandLine(sdkCmakeBin.get().asFile.resolve("cmake").path, "--build", hostBuildDir.get().asFile.path)
    }
}

tasks.withType<Test>().configureEach {
    dependsOn(buildHostTagLib)
    systemProperty("java.library.path", hostBuildDir.get().asFile.path)
    // TagLibBridgeTest reads the raw descriptor out of java.io.FileDescriptor.
    jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED")
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation(libs.junit5)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.runner)
}
