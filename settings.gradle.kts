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
include(":core:domain")
include(":core:database")
include(":core:data")
include(":core:presentation")
include(":core:design-system")
include(":feature:onboarding:presentation")
include(":feature:compounds:presentation")
include(":feature:protocols:presentation")
include(":feature:sites:presentation")
include(":feature:dashboard:presentation")
include(":feature:reconstitution:presentation")
include(":feature:logging:presentation")
include(":feature:settings:presentation")
include(":widget")
include(":shortcut")
include(":work")
include(":notification")
include(":benchmark")
