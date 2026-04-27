package com.neteinstein.instagramtogooglemaps.data.network

import com.neteinstein.instagramtogooglemaps.data.api.InstagramOEmbedApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitFactory {
    private const val BASE_URL = "https://api.instagram.com/"

    fun createInstagramApi(): InstagramOEmbedApi {
        val logging =
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(logging)
                .build()

        return Retrofit
            .Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(InstagramOEmbedApi::class.java)
    }
}
