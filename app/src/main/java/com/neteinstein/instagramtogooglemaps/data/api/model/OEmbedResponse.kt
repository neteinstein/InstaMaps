package com.neteinstein.instagramtogooglemaps.data.api.model

import com.google.gson.annotations.SerializedName

data class OEmbedResponse(
    @SerializedName("title") val title: String?,
    @SerializedName("author_name") val authorName: String?,
    @SerializedName("html") val html: String?,
    @SerializedName("thumbnail_url") val thumbnailUrl: String?,
)
