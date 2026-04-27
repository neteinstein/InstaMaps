package com.neteinstein.instagramtogooglemaps.domain.repository

import com.neteinstein.instagramtogooglemaps.domain.model.ReelInfo

interface LocationRepository {
    suspend fun getReelInfo(url: String): Result<ReelInfo>
}
