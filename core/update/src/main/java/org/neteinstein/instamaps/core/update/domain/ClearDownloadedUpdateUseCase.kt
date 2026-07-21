package org.neteinstein.instamaps.core.update.domain

/**
 * Deletes a previously-downloaded update APK (see [DownloadAppUpdateUseCase]) once it's no longer
 * needed - called by `core:update`'s `UpdateApkCleanupReceiver` right after the system finishes
 * installing it, so the file doesn't sit in the cache directory taking up space indefinitely.
 */
class ClearDownloadedUpdateUseCase(
    private val repository: UpdateRepository,
) {
    suspend operator fun invoke(): Result<Unit> = repository.clearDownloadedUpdate()
}
