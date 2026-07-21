package org.neteinstein.instamaps.core.update.domain

/**
 * A GitHub release newer than the currently installed build, as identified by
 * [UpdateRepository.checkForUpdate]. [versionName] has the release tag's leading "v" stripped
 * (e.g. `"1.0.17"` from tag `"v1.0.17"`) so it matches the exact scheme `PackageManager` reports
 * for the installed build - see `app/build.gradle.kts`/`.github/workflows/release.yml`.
 */
data class AppUpdate(
    val versionName: String,
    val apkDownloadUrl: String,
)
