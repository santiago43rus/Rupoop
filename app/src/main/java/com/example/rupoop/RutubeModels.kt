package com.example.rupoop

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.*

@Serializable
data class RutubeResponse(
    @SerialName("video_balancer") val videoBalancer: VideoBalancer? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null
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
data class Author(
    val name: String,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

@Serializable
data class SyncData(
    val favoriteVideoUrls: List<String> = emptyList()
)

@Serializable
data class Gist(
    val id: String,
    val files: Map<String, GistFile>
)

@Serializable
data class GistFile(
    val content: String? = null,
    val filename: String? = null
)

@Serializable
data class GistRequest(
    val description: String,
    val public: Boolean,
    val files: Map<String, GistFile>
)

@Serializable
data class GitHubUser(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

interface RutubeApi {
    @GET("api/search/video/?format=json")
    suspend fun searchVideos(@Query("query") query: String): SearchResponse

    @GET("api/play/options/{id}/?format=json")
    suspend fun getVideoOptions(@Path("id") id: String): RutubeResponse
}

interface GistApi {
    @GET("gists")
    suspend fun listGists(@Header("Authorization") authHeader: String): List<Gist>

    @GET("gists/{id}")
    suspend fun getGist(@Header("Authorization") authHeader: String, @Path("id") id: String): Gist

    @POST("gists")
    suspend fun createGist(@Header("Authorization") authHeader: String, @Body request: GistRequest): Gist
}

interface GitHubApi {
    @GET("user")
    suspend fun getUser(@Header("Authorization") authHeader: String): GitHubUser
}

object RetrofitClient {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Rupoop-App")
                .build()
            chain.proceed(request)
        }
        .build()

    val api: RutubeApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://rutube.ru/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RutubeApi::class.java)
    }

    val gistApi: GistApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.PROXY_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GistApi::class.java)
    }

    val gitHubApi: GitHubApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.PROXY_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GitHubApi::class.java)
    }
}
