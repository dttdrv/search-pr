pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
        maven("https://maven.mozilla.org/maven2/") {
            content { includeGroup("org.mozilla.geckoview") }
        }
    }
}

rootProject.name = "pane"

// Pure-Kotlin engine-agnostic logic lives in its own build so it can be compiled
// and tested without the Android SDK.
includeBuild("core")
include(":app")
