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
                
                val sequelStatus = getSequelStatus(candidate, video)
                when (sequelStatus) {
                    SequelStatus.NEXT_EPISODE -> score += 150.0f
                    SequelStatus.NEXT_SEASON_FIRST_EP -> score += 120.0f
                    SequelStatus.SAME_SEASON_FUTURE -> score += 50.0f
                    SequelStatus.OTHER_SEASON -> score -= 20.0f // Penalize wrong seasons
                    SequelStatus.NONE -> {}
                }
                
                score += calculateScore(candidate, registry)
                if (candidate.author?.name == video.author?.name) score += 10.0f
                score
            }).take(20)
    }

    private enum class SequelStatus { NEXT_EPISODE, NEXT_SEASON_FIRST_EP, SAME_SEASON_FUTURE, OTHER_SEASON, NONE }

    private fun getSequelStatus(candidate: SearchResult, original: SearchResult): SequelStatus {
        val cTitle = candidate.title.lowercase()
        val oTitle = original.title.lowercase()

        data class VideoInfo(val season: Int, val episode: Int?, val part: Int?, val base: String)
        
        fun parse(title: String): VideoInfo {
            val sRegex = Regex("(?:сезон|season|с|s)\\s*(\\d+)", RegexOption.IGNORE_CASE)
            val eRegex = Regex("(?:серия|эпизод|выпуск|episode|ep|e)\\s*(\\d+)", RegexOption.IGNORE_CASE)
            val pRegex = Regex("(?:часть|part|ч|p)\\s*(\\d+)", RegexOption.IGNORE_CASE)
            
            val sMatch = sRegex.find(title)
            val eMatch = eRegex.find(title)
            val pMatch = pRegex.find(title)
            
            val s = sMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val e = eMatch?.groupValues?.get(1)?.toIntOrNull()
            val p = pMatch?.groupValues?.get(1)?.toIntOrNull()
            
            var base = title
            listOf(sMatch, eMatch, pMatch).forEach { it?.let { base = base.replace(it.value, "") } }
            base = base.replace(Regex("[\\d\\W]+"), " ").trim()
            
            return VideoInfo(s, e, p, base)
        }
        
        val c = parse(cTitle)
        val o = parse(oTitle)
        
        if (c.base.isEmpty() || o.base.isEmpty()) return SequelStatus.NONE
        val baseSimilarity = c.base.startsWith(o.base.take(8)) || o.base.startsWith(c.base.take(8))
        if (!baseSimilarity) return SequelStatus.NONE

        if (c.season == o.season) {
            if (o.episode != null && c.episode == o.episode + 1) return SequelStatus.NEXT_EPISODE
            if (o.episode != null && c.episode != null && c.episode > o.episode) return SequelStatus.SAME_SEASON_FUTURE
            if (o.part != null && c.part == o.part + 1) return SequelStatus.NEXT_EPISODE
            return SequelStatus.NONE
        } else if (c.season == o.season + 1) {
            if (c.episode == null || c.episode == 1) return SequelStatus.NEXT_SEASON_FIRST_EP
            return SequelStatus.SAME_SEASON_FUTURE
        } else {
            return SequelStatus.OTHER_SEASON
        }
    }

    private fun isMovie(video: SearchResult): Boolean {
        return (video.duration ?: 0) > 3600 || video.title.contains("фильм", ignoreCase = true)
    }

    private fun calculateScore(video: SearchResult, registry: UserRegistry): Float {
        var score = 0f
        video.tags?.forEach { tag -> score += registry.tagWeights[tag] ?: 0f }
        if (registry.subscriptions.any { it.name == video.author?.name }) score += 15.0f
        
        val historyItem = registry.watchHistory.find { extractId(video.videoUrl) == it.videoId }
        if (historyItem != null) {
            val progressPercent = if (historyItem.totalDuration > 0) historyItem.progress.toFloat() / historyItem.totalDuration else 1f
            if (progressPercent > 0.9f) score -= 40.0f 
            else if (progressPercent > 0.05f) score += 30.0f 
        }
        return score
    }
    
    private fun extractId(url: String): String = url.split("/").lastOrNull { it.isNotEmpty() } ?: ""
}
