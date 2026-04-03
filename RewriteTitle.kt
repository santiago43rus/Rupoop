package com.santiago43rus.rupoop.data

data class VideoParsed(val base: String, val season: Int?, val episode: Int?, val part: Int?, val year: Int?)

fun parseTitle(title: String): VideoParsed {
    var baseTitle = title.lowercase()

    // Removing quality indicators
    baseTitle = baseTitle.replace(Regex("(?ui)\\b(?:1080p|720p|480p|2160p|4k|3d|2d)\\b"), " ")

    // Extract block elements
    val yearMatch = Regex("(?:^|\\s|\\()(19\\d{2}|20\\d{2})(?:\\s|$|\\)|\\.)").find(baseTitle)
    val year = yearMatch?.groupValues?.get(1)?.toIntOrNull()
    if (yearMatch != null) baseTitle = baseTitle.removeRange(yearMatch.range).trim()

    val seasonMatch = Regex("(?:сезон|season|s)\\s*(\\d+)").find(baseTitle)
    val season = seasonMatch?.groupValues?.get(1)?.toIntOrNull()
    if (seasonMatch != null) baseTitle = baseTitle.removeRange(seasonMatch.range).trim()

    val episodeMatch = Regex("(?:серия|эпизод|выпуск|episode|ep|e|сер|вып)\\s*(\\d+)").find(baseTitle)
    val episode = episodeMatch?.groupValues?.get(1)?.toIntOrNull()
    if (episodeMatch != null) baseTitle = baseTitle.removeRange(episodeMatch.range).trim()

    val partMatch = Regex("(?:часть|part|ч|p|фильм)\\s*(\\d+)").find(baseTitle)
    var part = partMatch?.groupValues?.get(1)?.toIntOrNull()
    if (partMatch != null) baseTitle = baseTitle.removeRange(partMatch.range).trim()

    baseTitle = baseTitle.replace(Regex("[^a-zа-я0-9\\s]"), " ").trim()
    baseTitle = baseTitle.replace(Regex("\\s+"), " ")

    if (part == null && season == null && episode == null) {
        val fbMatch = Regex("(?:^|\\s)(\\d{1,2})(?:\\s|$)").findAll(baseTitle).lastOrNull()
        if (fbMatch != null) {
            part = fbMatch.groupValues[1].toIntOrNull()
            baseTitle = baseTitle.removeRange(fbMatch.range).trim()
        }
    }

    baseTitle = baseTitle.replace(Regex("\\d"), "").trim()
    baseTitle = baseTitle.replace(Regex("\\s+"), " ")

    return VideoParsed(baseTitle, season, episode, part, year)
}

fun main() {
    println(parseTitle("История игрушек 2 4К 2021"))
    println(parseTitle("История игрушек 3: Большой побег"))
    println(parseTitle("Гадкий я"))
    println(parseTitle("Гадкий я 2"))
}

