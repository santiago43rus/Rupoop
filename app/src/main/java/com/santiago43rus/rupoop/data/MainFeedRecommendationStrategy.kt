package com.santiago43rus.rupoop.data

class MainFeedRecommendationStrategy(private val registryManager: UserRegistryManager) {

    private fun isTargetContent(title: String): Boolean {
        // Strict pattern matches year (19xx or 20xx) or words "сезон"/"серия"
        val regex = Regex("(?:\\b(?:19|20)\\d{2}\\b)|(?:сезон)|(?:сери[яий])|(?:s\\d{1,2}e\\d{1,3})", RegexOption.IGNORE_CASE)
        return regex.containsMatchIn(title)
    }

    fun recommend(results: List<SearchResult>): List<SearchResult> {
        val registry = registryManager.registry
        val historyMap = registry.watchHistory.associateBy { it.videoId }
        val now = System.currentTimeMillis()
        val oneYearMs = 365L * 24 * 60 * 60 * 1000

        // Clean old history
        val cleanedHistory = registry.watchHistory.filter { now - it.timestamp < oneYearMs }
        if (cleanedHistory.size != registry.watchHistory.size) {
            registryManager.updateRegistry(registry.copy(watchHistory = cleanedHistory))
        }

        val hiddenIds = registry.hiddenVideos.toSet()
        val dislikedIds = registry.dislikedVideos.toSet()
        val hiddenTitles = registry.hiddenTitles.toSet()

        val filteredResults = results.filter { result ->
            val videoId = parseVideoId(result.videoUrl)
            if (videoId in hiddenIds || videoId in dislikedIds) return@filter false
            if (hiddenTitles.any { result.title.contains(it, ignoreCase = true) }) return@filter false

            // Filter out verified channels (original Rutube content)
            if (result.author?.isVerifiedChannel == true) return@filter false

            // Filter CAPS lock, banned words and genre plurals
            if (RecommendationUtils.hasCapsWord(result.title)) return@filter false
            if (RecommendationUtils.isBannedTitle(result.title)) return@filter false

            val historyItem = historyMap[videoId]

            if (historyItem != null) {
                val duration = result.duration ?: 1
                val watchedSec = historyItem.progress
                val percentWatched = if (duration > 0) watchedSec.toFloat() / duration * 100 else 0f

                if (percentWatched >= 90f) return@filter false
                if (percentWatched <= 10f) return@filter false
            }

            isTargetContent(result.title)
        }

        // 95% new, 5% partially watched
        val (partiallyWatched, newVideos) = filteredResults.partition { result ->
             val videoId = parseVideoId(result.videoUrl)
             historyMap.containsKey(videoId)
        }

        val newCount = (newVideos.size * 0.95).toInt()
        val partialCount = (newCount * 0.05).toInt().coerceAtMost(partiallyWatched.size)

        val finalList = newVideos.shuffled().take(newCount) + partiallyWatched.shuffled().take(partialCount)

        return finalList.shuffled().sortedWith(compareByDescending<SearchResult> {
            (it.duration ?: 0) > 100 // High priority
        }.thenByDescending {
            RecommendationUtils.calculateScore(it, registry)
        })
    }

    private fun parseVideoId(url: String): String {
        return url.substringAfterLast("/").substringBefore("?")
    }
}