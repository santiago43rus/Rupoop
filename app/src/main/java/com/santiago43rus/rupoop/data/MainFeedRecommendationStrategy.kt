package com.santiago43rus.rupoop.data

import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.data.UserRegistry
import com.santiago43rus.rupoop.data.UserRegistryManager

class MainFeedRecommendationStrategy(private val registryManager: UserRegistryManager) {

    private val PERSONALIZATION_THRESHOLD = 3

    fun recommend(results: List<SearchResult>): List<SearchResult> {
        val registry = registryManager.registry
        val historySize = registry.watchHistory.size

        return results.filter { !it.title.contains("мультик", ignoreCase = true) }
            .filter { (it.duration ?: 0) >= 900 } // Minimum 15 minutes (900 seconds)
            .shuffled().sortedWith(compareByDescending<SearchResult> {
                RecommendationUtils.calculateScore(it, registry)
            }.thenByDescending {
                if (historySize < PERSONALIZATION_THRESHOLD) RecommendationUtils.isMovie(it) else false
            })
    }
}
