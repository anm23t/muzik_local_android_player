import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "org.muzzzlo.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.muzzzlo.player"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0-start"
    }

    // The release key stays with the owner: keystore.properties (git-ignored) points at it. Without the file
    // the release build is left unsigned, so CI and contributors build without any secrets.
    val keystoreProperties =
        Properties().apply {
            rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
        }
    if (!keystoreProperties.isEmpty) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // A separate app next to the installed release: the release key differs, and replacing the release
            // would mean uninstalling it and losing its library data.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    splits {
        abi {
            // One APK per phone architecture plus a universal one; x86_64 only ships in the universal APK.
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:library"))
    implementation(project(":core:playback"))
    implementation(project(":core:tags"))
    implementation(project(":core:stats"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:library"))
    implementation(project(":feature:player"))
    implementation(project(":feature:playlists"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:stats"))
    implementation(project(":feature:tag-editor"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation(libs.junit5)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
