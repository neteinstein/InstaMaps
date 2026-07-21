package org.neteinstein.instamaps.core.update.domain

import java.io.File

/**
 * Boundary between the update-check domain layer and GitHub Releases (production:
 * `core:update`'s `GitHubUpdateRepository`; fakeable in tests).
 */
interface UpdateRepository {
    /**
     * Compares the installed build's version against GitHub's latest release (see
     * [isNewerVersion]) and returns which [UpdateCheckResult] case applies. Fails with
     * `AppError.NotFound` if the latest release has no APK asset attached, or
     * `AppError.Network`/`AppError.Parsing` for request/response failures - see
     * `core:common`'s `AppError`.
     */
    suspend fun checkForUpdate(): Result<UpdateCheckResult>

    /** Downloads [update]'s APK to local storage, returning the file it was written to. */
    suspend fun downloadUpdate(update: AppUpdate): Result<File>
}
