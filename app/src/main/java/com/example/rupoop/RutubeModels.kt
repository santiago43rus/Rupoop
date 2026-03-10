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
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    val tags: List<Tag>? = emptyList(),
    val duration: Int? = null
)

@Serializable
data class Tag(
    val name: String
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
    val duration: Int? = null,
    val tags: List<String>? = emptyList(),
    @SerialName("created_ts") val createdTs: String? = null
)

@Serializable
data class Author(
    val id: Long? = null,
    val name: String,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val videos: List<SearchResult> = emptyList()
)

@Serializable
data class UserRegistry(
    val watchHistory: List<WatchHistoryItem> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val tagWeights: Map<String, Float> = emptyMap(),
    val subscriptions: List<Author> = emptyList(),
    val likedVideos: List<SearchResult> = emptyList(),
    val watchLater: List<SearchResult> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val appSettings: AppSettings = AppSettings(),
    val lastSynced: Long = System.currentTimeMillis(),
    val watchHistoryClearedAt: Long = 0,
    val searchHistoryClearedAt: Long = 0
)

@Serializable
data class WatchHistoryItem(
    val videoId: String,
    val timestamp: Long,
    val progress: Long,
    val totalDuration: Long,
    val title: String? = null,
    val thumbnailUrl: String? = null,
    val authorName: String? = null,
    @SerialName("author_avatar_url") val authorAvatarUrl: String? = null,
    @SerialName("author_id") val authorId: Long? = null,
    val videoUrl: String = ""
)

@Serializable
data class AppSettings(
    val theme: String = "dark",
    val language: String = "ru",
    val videoQuality: String = "auto",
    val adultContentEnabled: Boolean = true,
    val kidsContentEnabled: Boolean = true,
    val downloadQuality: String = "1080",
    val syncFrequencyHours: Int = 24
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
    suspend fun searchVideos(
        @Query("query") query: String,
        @Query("ordering") ordering: String? = null,
        @Query("page") page: Int = 1
    ): SearchResponse

    @GET("api/play/options/{id}/?format=json")
    suspend fun getVideoOptions(@Path("id") id: String): RutubeResponse
    
    @GET("api/video/person/{id}/?format=json")
    suspend fun getAuthorVideos(
        @Path("id") id: String,
        @Query("ordering") ordering: String? = "-created_ts",
        @Query("page") page: Int = 1
    ): SearchResponse
}

interface GistApi {
    @GET("gists")
    suspend fun listGists(@Header("Authorization") authHeader: String): List<Gist>

    @GET("gists/{id}")
    suspend fun getGist(@Header("Authorization") authHeader: String, @Path("id") id: String): Gist

    @POST("gists")
    suspend fun createGist(@Header("Authorization") authHeader: String, @Body request: GistRequest): Gist

    @PATCH("gists/{id}")
    suspend fun updateGist(@Header("Authorization") authHeader: String, @Path("id") id: String, @Body request: GistRequest): Gist
}

interface GitHubApi {
    @GET("user")
    suspend fun getUser(@Header("Authorization") authHeader: String): GitHubUser
}

object RetrofitClient {
    val json = Json {
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
