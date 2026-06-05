pluginManagement {
    includeBuild("build-logic")

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

rootProject.name = "Stax"

include(":app")
include(":samples:android-application")
include(":samples:android-library")
include(":samples:android-feature")
include(":samples:kotlin-library")
include(":samples:compose")
include(":samples:koin")
include(":samples:room")
include(":samples:serialization")
