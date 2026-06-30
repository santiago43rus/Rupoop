package com.santiago43rus.rupoop.data

object RecommendationUtils {

    private val scorer = TagWeightCalculator()
    private val predictor = SequelPredictor()

    fun isTooSimilar(s1: String, s2: String): Boolean {
        if (s1.isEmpty() || s2.isEmpty()) return false
        val s1l = s1.lowercase()
        val s2l = s2.lowercase()
        if (s1l == s2l) return true
        if (s1l.contains(s2l) || s2l.contains(s1l)) {
             val ratio = s1.length.toFloat() / s2.length.toFloat()
             if (ratio > 0.7f && ratio < 1.4f) return true
        }
        return false
    }

    fun parseTitle(title: String): VideoParsed {
        return predictor.parseTitle(title)
    }

    fun getSequelStatus(candidate: SearchResult, original: SearchResult): SequelStatus {
        return predictor.getSequelStatus(candidate, original)
    }

    fun isMovie(video: SearchResult): Boolean {
        return (video.duration ?: 0) > 3600 || video.title.contains("фильм", ignoreCase = true)
    }

    fun calculateScore(video: SearchResult, registry: UserRegistry): Float {
        return scorer.calculateScore(video, registry)
    }
}
