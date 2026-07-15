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
include(":feature:maps")
include(":feature:geocoding")
include(":feature:videoprocessing")
// TODO(build-verify): re-enable as each module is scaffolded.
// include(":app")
// include(":feature:share")
