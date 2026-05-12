package com.santiago43rus.rupoop.data

class RelatedVideoRecommendationStrategy(private val registryManager: UserRegistryManager) {

    fun recommendRelated(video: SearchResult, pool: List<SearchResult>): List<SearchResult> {
        val registry = registryManager.registry
        val originalTitleParsed = RecommendationUtils.parseTitle(video.title)

        return pool.filter { it.videoUrl != video.videoUrl }
            .filter { !it.title.contains("мультик", ignoreCase = true) }
            .filter { (it.duration ?: 0) >= 900 } // Minimum 15 minutes
            .filter { candidate ->
                val status = RecommendationUtils.getSequelStatus(candidate, video)
                if (status == SequelStatus.NONE) {
                    val candidateParsed = RecommendationUtils.parseTitle(candidate.title)
                    !RecommendationUtils.isTooSimilar(candidateParsed.base, originalTitleParsed.base)
                } else {
                    true
                }
            }
            .sortedWith(compareByDescending<SearchResult> { candidate ->
                var score = 0f
                val sequelStatus = RecommendationUtils.getSequelStatus(candidate, video)
                when (sequelStatus) {
                    SequelStatus.NEXT_EPISODE -> score += 50000.0f
                    SequelStatus.NEXT_SEASON_FIRST_EP -> score += 40000.0f
                    SequelStatus.SAME_SEASON_FUTURE -> score += 5000.0f
                    SequelStatus.SAME_SERIES_OTHER -> score += 1000.0f
                    SequelStatus.PAST_EPISODE -> score -= 50000.0f
                    SequelStatus.NONE -> {}
                }

                score += RecommendationUtils.calculateScore(candidate, registry)
                if (candidate.author?.name == video.author?.name) score += 50.0f
                score
            })
    }

    fun getSearchQueries(video: SearchResult): List<String> {
        val parsed = RecommendationUtils.parseTitle(video.title)
        val queries = mutableListOf<String>()
        val base = parsed.base.take(50).trim()

        if (parsed.season != null && parsed.episode != null) {
            queries.add("$base сезон ${parsed.season} серия ${parsed.episode + 1}")
            queries.add("$base сезон ${parsed.season + 1} серия 1")
        } else if (parsed.episode != null) {
            queries.add("$base серия ${parsed.episode + 1}")
        } else if (parsed.part != null) {
            queries.add("$base часть ${parsed.part + 1}")
        }
        queries.add(base)
        return queries
    }
}

