package com.santiago43rus.rupoop.parser

import com.santiago43rus.rupoop.data.Author
import com.santiago43rus.rupoop.data.SearchResult
import kotlinx.serialization.Serializable

@Serializable
data class AudioTrackSource(
    val id: String,
    val name: String,
    val streamUrl: String,
    val playerName: String? = null
)

@Serializable
data class Episode(
    val title: String,
    val seasonNumber: Int = 1,
    val episodeNumber: Int = 1,
    val streamUrl: String,
    val audioTracks: List<AudioTrackSource> = emptyList()
)

@Serializable
data class ParsedVideo(
    val videoUrl: String,
    val streamUrl: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val authorName: String? = null,
    val authorAvatarUrl: String? = null,
    val durationSeconds: Long? = null,
    val viewsCount: Int? = null,
    val headers: Map<String, String> = emptyMap(),
    val sourceName: String = "Web",
    val episodes: List<Episode> = emptyList(),
    val audioTracks: List<AudioTrackSource> = emptyList()
) {
    fun toSearchResult(): SearchResult {
        return SearchResult(
            videoUrl = videoUrl,
            title = title,
            thumbnailUrl = thumbnailUrl,
            author = authorName?.let { Author(name = it, avatarUrl = authorAvatarUrl) },
            duration = durationSeconds?.toInt(),
            hits = viewsCount
        )
    }
}
