package com.example.rupoop

import kotlin.math.max

class RecommendationEngine(private val registryManager: UserRegistryManager) {
    
    private val PERSONALIZATION_THRESHOLD = 3

    fun recommend(results: List<SearchResult>): List<SearchResult> {
        val registry = registryManager.registry
        val historySize = registry.watchHistory.size
        
        return results.shuffled().sortedWith(compareByDescending<SearchResult> { 
            calculateScore(it, registry)
        }.thenByDescending { 
            if (historySize < PERSONALIZATION_THRESHOLD) isMovie(it) else false
        })
    }

    fun recommendRelated(video: SearchResult, pool: List<SearchResult>): List<SearchResult> {
        val registry = registryManager.registry
        
        return pool.filter { it.videoUrl != video.videoUrl }
            .shuffled()
            .sortedWith(compareByDescending<SearchResult> { candidate ->
                var score = 0f
                val commonTags = candidate.tags?.intersect((video.tags ?: emptyList()).toSet())?.size ?: 0
                score += commonTags * 5.0f
                if (isSequelOf(candidate, video)) score += 20.0f
                score += calculateScore(candidate, registry)
                if (candidate.author?.name == video.author?.name) score += 10.0f
                score
            }).take(20)
    }

    private fun isMovie(video: SearchResult): Boolean {
        return (video.duration ?: 0) > 3600 || video.title.contains("фильм", ignoreCase = true)
    }

    private fun calculateScore(video: SearchResult, registry: UserRegistry): Float {
        var score = 0f
        
        // Dynamic weight from tags
        video.tags?.forEach { tag ->
            score += registry.tagWeights[tag] ?: 0f
        }
        
        // Subscription bonus
        if (registry.subscriptions.any { it.name == video.author?.name }) {
            score += 15.0f
        }

        // Sequel of anything watched
        if (isSequelOfWatched(video, registry.watchHistory)) {
            score += 15.0f
        }

        // Freshness/Diversity
        val historyItem = registry.watchHistory.find { extractId(video.videoUrl) == it.videoId }
        if (historyItem != null) {
            val progressPercent = if (historyItem.totalDuration > 0) historyItem.progress.toFloat() / historyItem.totalDuration else 1f
            if (progressPercent > 0.9f) {
                score -= 30.0f // Penalize watched
            } else if (progressPercent > 0.1f) {
                score += 20.0f // Heavy boost for continue watching
            }
        }

        return score
    }

    private fun isSequelOf(candidate: SearchResult, original: SearchResult): Boolean {
        val cTitle = candidate.title
        val oTitle = original.title
        val regex = Regex(".*\\((?:Часть|ч\\.|part)\\s*(\\d+)\\).*", RegexOption.IGNORE_CASE)
        val cMatch = regex.find(cTitle)
        val oMatch = regex.find(oTitle)
        
        if (cMatch != null && oMatch != null) {
            val cPart = cMatch.groupValues[1].toIntOrNull() ?: return false
            val oPart = oMatch.groupValues[1].toIntOrNull() ?: return false
            return cPart == oPart + 1
        }
        return cTitle.contains(oTitle, ignoreCase = true) && cTitle.length > oTitle.length
    }

    private fun isSequelOfWatched(video: SearchResult, history: List<WatchHistoryItem>): Boolean {
        val title = video.title
        val regex = Regex(".*\\((?:Часть|ч\\.|part)\\s*(\\d+)\\).*", RegexOption.IGNORE_CASE)
        val match = regex.find(title)
        
        if (match != null) {
            val partNumber = match.groupValues[1].toIntOrNull() ?: return false
            if (partNumber > 1) {
                return history.any { it.title?.contains(title.substring(0, max(0, match.range.first)), ignoreCase = true) == true }
            }
        }
        return false
    }
    
    private fun extractId(url: String): String = url.split("/").lastOrNull { it.isNotEmpty() } ?: ""
}
