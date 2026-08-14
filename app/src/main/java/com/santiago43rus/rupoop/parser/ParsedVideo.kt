package com.santiago43rus.rupoop.parser

import com.santiago43rus.rupoop.data.Author
import com.santiago43rus.rupoop.data.SearchResult
import kotlinx.serialization.Serializable

@Serializable
data class ParsedVideo(
    val videoUrl: String,
    val streamUrl: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val authorName: String? = null,
    val authorAvatarUrl: String? = null,
    val durationSeconds: Long? = null,
    val headers: Map<String, String> = emptyMap(),
    val sourceName: String = "Web"
) {
    fun toSearchResult(): SearchResult {
        return SearchResult(
            videoUrl = videoUrl,
            title = title,
            thumbnailUrl = thumbnailUrl,
            author = authorName?.let { Author(name = it, avatarUrl = authorAvatarUrl) },
            duration = durationSeconds?.toInt()
        )
    }
}
