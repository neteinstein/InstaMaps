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

rootProject.name = "InstaMaps"

include(":core:common")
include(":core:designsystem")
include(":core:settings")
include(":core:instagramauth")
include(":feature:maps")
include(":feature:geocoding")
include(":feature:videoprocessing")
include(":feature:share")
include(":feature:settings")
include(":feature:instagramauth")
include(":app")
