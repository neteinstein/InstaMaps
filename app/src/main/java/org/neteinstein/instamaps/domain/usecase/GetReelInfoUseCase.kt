package org.neteinstein.instamaps.domain.usecase

import org.neteinstein.instamaps.domain.model.ReelInfo
import org.neteinstein.instamaps.domain.repository.LocationRepository

class GetReelInfoUseCase(
    private val repository: LocationRepository,
) {
    suspend operator fun invoke(url: String): Result<ReelInfo> {
        if (!isValidInstagramUrl(url)) {
            return Result.failure(IllegalArgumentException("Invalid Instagram URL"))
        }
        return repository.getReelInfo(url)
    }

    private fun isValidInstagramUrl(url: String): Boolean = url.contains("instagram.com") || url.contains("instagr.am")
}
