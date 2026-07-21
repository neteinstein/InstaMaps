plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ktlint) apply false
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
    kover(project(":core:settings"))
    kover(project(":core:instagramauth"))
    kover(project(":core:history"))
    kover(project(":core:permissions"))
    kover(project(":core:update"))
    kover(project(":feature:maps"))
    kover(project(":feature:geocoding"))
    kover(project(":feature:videoprocessing"))
    kover(project(":feature:share"))
    kover(project(":feature:settings"))
    kover(project(":feature:instagramauth"))
    kover(project(":feature:history"))
    kover(project(":feature:permissions"))
    // :app is intentionally excluded: it is a pure composition root (Application class + a single
    // trampoline Activity wiring Koin/manifest intent-filters together) with no domain/data logic
    // of its own to cover - the same rationale as the `*.di`/`*.presentation`/`*.work` excludes
    // below, just for a whole module instead of a package.
}

kover {
    reports {
        filters {
            excludes {
                // Koin `di` modules are declarative wiring (a list of `factory { }`/`single { }`
                // calls) with no branching logic worth measuring; Compose UI (design system and
                // feature:share's `presentation` package) has no business logic and isn't
                // exercisable without instrumentation/Robolectric, which this project doesn't use.
                // `feature:share`'s `work` package (WorkManager worker + notification glue) is in
                // the same boat: it's Android-framework glue (CoroutineWorker/NotificationManager)
                // that can't be meaningfully unit tested on the JVM either, so the pipeline logic
                // it calls into (`ProcessSharedUrlUseCase`) is what's covered instead. `core:
                // permissions` is the same story: it's a thin wrapper around
                // `ActivityResultContracts`/`ContextCompat`/`LifecycleEventEffect` Android
                // framework calls with no meaningfully unit-testable branching of its own. Extend
                // this list as new UI/framework-glue packages land in feature modules.
                //
                // "org.neteinstein.instamaps.core.update" (deliberately without a trailing `*`,
                // unlike the designsystem/permissions entries above) excludes only that exact
                // package - i.e. just `AppUpdateInstaller`, the same PackageManager/Intent glue
                // shape as `core:permissions` - while still fully counting its `.domain`/`.data`
                // sub-packages, which hold real unit-tested logic (version comparison, GitHub
                // release JSON parsing).
                packages(
                    "*.di",
                    "*.presentation",
                    "*.work",
                    "org.neteinstein.instamaps.core.designsystem*",
                    "org.neteinstein.instamaps.core.permissions*",
                    "org.neteinstein.instamaps.core.update",
                )
            }
        }
    }
}

