package com.neteinstein.instagramtogooglemaps.data.repository

import com.neteinstein.instagramtogooglemaps.data.api.InstagramOEmbedApi
import com.neteinstein.instagramtogooglemaps.domain.model.ReelInfo
import com.neteinstein.instagramtogooglemaps.domain.repository.LocationRepository

class LocationRepositoryImpl(
    private val api: InstagramOEmbedApi,
) : LocationRepository {

    override suspend fun getReelInfo(url: String): Result<ReelInfo> {
        return try {
            val response = api.getOEmbed(url)
            Result.success(
                ReelInfo(
                    url = url,
                    description = response.title ?: "",
                    authorName = response.authorName ?: "",
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
