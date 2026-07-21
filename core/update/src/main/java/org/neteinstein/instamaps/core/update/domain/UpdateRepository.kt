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

    /**
     * Deletes any APK previously written by [downloadUpdate] - called once the system finishes
     * installing it (see `core:update`'s `UpdateApkCleanupReceiver`) so it doesn't sit in the
     * cache directory forever wasting space. A no-op, not a failure, if nothing was downloaded.
     */
    suspend fun clearDownloadedUpdate(): Result<Unit>
}
