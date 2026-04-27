package org.neteinstein.instamaps.data.repository

import org.neteinstein.instamaps.data.api.InstagramOEmbedApi
import org.neteinstein.instamaps.domain.model.ReelInfo
import org.neteinstein.instamaps.domain.repository.LocationRepository

class LocationRepositoryImpl(
    private val api: InstagramOEmbedApi,
) : LocationRepository {
    override suspend fun getReelInfo(url: String): Result<ReelInfo> =
        try {
            val response = api.getOEmbed(url)
            Result.success(
                ReelInfo(
                    url = url,
                    description = response.title ?: "",
                    authorName = response.authorName ?: "",
                ),
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
}
