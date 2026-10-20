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
    ":core:tags",
    ":core:stats",
    ":core:designsystem",
    ":feature:library",
    ":feature:player",
    ":feature:settings",
    ":feature:tag-editor",
)
