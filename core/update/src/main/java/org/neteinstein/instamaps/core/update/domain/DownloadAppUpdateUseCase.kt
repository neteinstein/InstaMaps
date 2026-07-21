package org.neteinstein.instamaps.core.update.domain

import java.io.File

/**
 * Downloads the APK for a previously-found [AppUpdate] to local storage, ready to be handed to
 * `core:update`'s `AppUpdateInstaller`. Kept as its own use case (rather than folded into
 * [CheckForUpdateUseCase]) so a caller can gate the download behind a sideloading-permission check
 * in between the two calls, without downloading (and then discarding) an APK the system won't
 * currently let it install.
 */
class DownloadAppUpdateUseCase(
    private val repository: UpdateRepository,
) {
    suspend operator fun invoke(update: AppUpdate): Result<File> = repository.downloadUpdate(update)
}
