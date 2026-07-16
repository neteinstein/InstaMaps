plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.secrets) apply false
    // Unlike the plugins above, Kover is genuinely applied here (not `apply false`): the root
    // project acts as Kover's "merging module" that aggregates coverage from every module below
    // into one report, so it needs the plugin active to expose the `kover { }` / `dependencies {
    // kover(...) }` DSLs.
    alias(libs.plugins.kover)
}

// Keep this list in sync with the `include(...)` calls in settings.gradle.kts - a module left
// out here is silently excluded from the merged coverage report.
dependencies {
    kover(project(":core:common"))
    kover(project(":core:designsystem"))
    kover(project(":feature:maps"))
    kover(project(":feature:geocoding"))
    kover(project(":feature:videoprocessing"))
    // TODO(build-verify): add kover(project(":feature:share")) and kover(project(":app")) once
    // those modules are re-enabled in settings.gradle.kts.
}

kover {
    reports {
        filters {
            excludes {
                // Koin `di` modules are declarative wiring (a list of `factory { }`/`single { }`
                // calls) with no branching logic worth measuring; Compose UI in the design system
                // has no business logic and isn't exercisable without instrumentation/Robolectric,
                // which this project doesn't use. Extend this list as new UI packages land in
                // feature modules (e.g. a future `*.presentation` package).
                packages("*.di", "org.neteinstein.instamaps.core.designsystem*")
            }
        }
    }
}

