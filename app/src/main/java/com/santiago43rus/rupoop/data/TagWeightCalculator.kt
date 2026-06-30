package com.santiago43rus.rupoop.data

import com.santiago43rus.rupoop.util.extractId

interface RecommendationScorer {
    fun calculateScore(video: SearchResult, registry: UserRegistry): Float
}

class TagWeightCalculator : RecommendationScorer {
    override fun calculateScore(video: SearchResult, registry: UserRegistry): Float {
        var score = 0f
        val effectiveTags = video.tags?.toMutableList() ?: mutableListOf()
        val filmGenres = listOf("боевик", "комедия", "драма", "ужасы", "фантастика", "триллер", "мелодрама", "детектив", "приключения", "криминал", "семейный", "военный", "история", "биография", "фэнтези", "вестерн")

        if (effectiveTags.any { tag -> filmGenres.any { genre -> tag.contains(genre, ignoreCase = true) } }) {
            effectiveTags.add("фильм")
        }

        effectiveTags.forEach { tag -> score += registry.tagWeights[tag] ?: 0f }
        if (registry.subscriptions.any { it.name == video.author?.name }) score += 15.0f

        val historyId = extractId(video.videoUrl)
        val historyItem = registry.watchHistory.find { historyId == it.videoId }
        if (historyItem != null) {
            val progressPercent = if (historyItem.totalDuration > 0) historyItem.progress.toFloat() / historyItem.totalDuration else 1f
            if (progressPercent > 0.9f) score -= 40.0f
            else if (progressPercent > 0.05f) score += 30.0f
        }
        return score
    }
}
