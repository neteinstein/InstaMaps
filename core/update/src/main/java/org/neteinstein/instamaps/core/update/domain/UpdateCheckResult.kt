package org.neteinstein.instamaps.core.update.domain

/** Outcome of [UpdateRepository.checkForUpdate]/[CheckForUpdateUseCase]. */
sealed class UpdateCheckResult {
    /** The installed build is already the latest one published on GitHub Releases. */
    data class UpToDate(val currentVersionName: String) : UpdateCheckResult()

    /** GitHub's latest release is newer than the installed build. */
    data class UpdateAvailable(val update: AppUpdate) : UpdateCheckResult()
}
