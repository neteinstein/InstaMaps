package org.neteinstein.instamaps.data.api

import org.neteinstein.instamaps.data.api.model.OEmbedResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface InstagramOEmbedApi {
    @GET("oembed/")
    suspend fun getOEmbed(
        @Query("url") url: String,
        @Query("omitscript") omitScript: Boolean = true,
    ): OEmbedResponse
}
