package org.neteinstein.instamaps.domain.repository

import org.neteinstein.instamaps.domain.model.ReelInfo

interface LocationRepository {
    suspend fun getReelInfo(url: String): Result<ReelInfo>
}
