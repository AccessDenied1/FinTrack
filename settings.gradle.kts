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

rootProject.name = "FinTrack"

include(":app")
include(":core:common")
include(":core:model")
include(":core:database")
include(":core:data")
include(":core:ui")
include(":feature:expense")
include(":feature:home")
include(":feature:networth")
include(":service:sms")
include(":service:parser")
include(":service:categorizer")
include(":service:notification")
