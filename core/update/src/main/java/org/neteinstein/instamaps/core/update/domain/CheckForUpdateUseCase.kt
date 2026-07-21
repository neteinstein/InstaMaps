package org.neteinstein.instamaps.core.update.domain

/**
 * Checks GitHub Releases for a newer InstaMaps build than the one currently installed - see
 * [UpdateRepository] for where "newer" and "currently installed" each come from.
 */
class CheckForUpdateUseCase(
    private val repository: UpdateRepository,
) {
    suspend operator fun invoke(): Result<UpdateCheckResult> = repository.checkForUpdate()
}
