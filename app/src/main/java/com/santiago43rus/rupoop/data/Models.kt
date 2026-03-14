package com.santiago43rus.rupoop.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RutubeResponse(
    @SerialName("video_balancer") val videoBalancer: VideoBalancer? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    val tags: List<Tag>? = emptyList(),
    val duration: Int? = null
)

@Serializable
data class Tag(val name: String)

@Serializable
data class VideoBalancer(val m3u8: String? = null)

@Serializable
data class SearchResponse(val results: List<SearchResult> = emptyList())

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
    val syncFrequencyHours: Int = 24,
    val enabledGenres: List<String> = listOf(
        "аниме", "боевики", "комедии", "фантастика", "ужасы",
        "драма", "документальные", "мультфильмы", "сериалы", "музыка"
    )
)

@Serializable
data class Gist(val id: String, val files: Map<String, GistFile>)

@Serializable
data class GistFile(val content: String? = null, val filename: String? = null)

@Serializable
data class GistRequest(val description: String, val public: Boolean, val files: Map<String, GistFile>)

@Serializable
data class GitHubUser(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

