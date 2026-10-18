// Standalone build for the task 0.3.0 spike. Deliberately NOT included in the root settings.gradle.kts,
// so nothing here reaches the app, CI or `./gradlew test` of the main build.
// Run: ../../gradlew -p spikes/tag-library test   (see README.md)
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "tag-library-spike"
