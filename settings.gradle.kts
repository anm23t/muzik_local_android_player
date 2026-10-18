pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "muzzzlo"

include(
    ":app",
    ":core:model",
    ":core:database",
    ":core:library",
    ":core:playback",
    ":feature:library",
    ":feature:player",
)
