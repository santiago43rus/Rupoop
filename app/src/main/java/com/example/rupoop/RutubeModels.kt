package com.example.rupoop

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class RutubeResponse(
    @SerialName("video_balancer") val videoBalancer: VideoBalancer? = null
)

@Serializable
data class VideoBalancer(
    val m3u8: String? = null
)

@Serializable
data class SearchResponse(
    val results: List<SearchResult> = emptyList()
)

@Serializable
data class SearchResult(
    @SerialName("video_url") val videoUrl: String,
    val title: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    val author: Author? = null,
    val duration: Int? = null
)

@Serializable
data class Author(val name: String)

interface RutubeApi {
    @GET("api/search/video/?format=json")
    suspend fun searchVideos(@Query("query") query: String): SearchResponse

    @GET("api/play/options/{id}/?format=json")
    suspend fun getVideoOptions(@Path("id") id: String): RutubeResponse
}

object RetrofitClient {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    val api: RutubeApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://rutube.ru/")
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RutubeApi::class.java)
    }
}

fun formatDuration(seconds: Int?): String {
    if (seconds == null || seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
}