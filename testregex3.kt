import java.util.Locale

data class SequenceInfo(
    val baseName: String,
    val season: Int?,
    val episode: Int?,
    val part: Int?,
    val year: Int?
)

fun parseSequenceInfo(title: String): SequenceInfo {
    var base = title
    
    // First, maybe separate by slash, taking first part, BUT for parts/series we need to be careful.
    // Let's just remove anything after slash:
    val splitBySlash = base.split("/")
    if (splitBySlash.size > 1) {
        base = splitBySlash[0]
    }
    
    // remove quotes like «...»
    base = base.replace(Regex("«[^»]*»?"), "")

    // Try to extract year before deleting all brackets
    val yearRegex = Regex("\\((\\d{4})\\)")
    var year: Int? = null
    yearRegex.find(base)?.let { match ->
        year = match.groupValues[1].toIntOrNull()
    }
    // remove stuff in brackets
    val bracketRegex = Regex("\\[.*?\\]|\\(.*?\\)")
    base = base.replace(bracketRegex, "")

    // Season and Episode multiple formats
    var season: Int? = null
    var episode: Int? = null
    
    // Format: с01э05
    val seRegex1 = Regex("с(\\d{1,2})э(\\d{1,2})", RegexOption.IGNORE_CASE)
    // Format: 1 сезон 5 серия
    val seRegex2 = Regex("(\\d+)\\s*-?я?\\s*(?:сезон|season).*?(\\d+)\\s*-?я?\\s*(?:серия|эпизод|episode|ep)", RegexOption.IGNORE_CASE)
    // Format: сезон 1 серия 5
    val seRegex3 = Regex("(?:сезон|season|s)\\s*(\\d+).*?(?:серия|эпизод|episode|ep|e)\\s*(\\d+)", RegexOption.IGNORE_CASE)
    // Format: 5 серия (without season)
    val eRegexOnly = Regex("(\\d+)\\s*-?я?\\s*(?:серия|эпизод|episode|ep|выпуск)", RegexOption.IGNORE_CASE)
    val eRegexOnly2 = Regex("(?:серия|эпизод|episode|ep|выпуск|e)\\s*(\\d+)", RegexOption.IGNORE_CASE)

    when {
        seRegex1.containsMatchIn(base) -> {
            val match = seRegex1.find(base)!!
            season = match.groupValues[1].toIntOrNull()
            episode = match.groupValues[2].toIntOrNull()
            base = base.replace(match.value, "")
        }
        seRegex2.containsMatchIn(base) -> {
            val match = seRegex2.find(base)!!
            season = match.groupValues[1].toIntOrNull()
            episode = match.groupValues[2].toIntOrNull()
            base = base.replace(match.value, "")
        }
        seRegex3.containsMatchIn(base) -> {
            val match = seRegex3.find(base)!!
            season = match.groupValues[1].toIntOrNull()
            episode = match.groupValues[2].toIntOrNull()
            base = base.replace(match.value, "")
        }
        eRegexOnly.containsMatchIn(base) -> {
            val match = eRegexOnly.find(base)!!
            episode = match.groupValues[1].toIntOrNull()
            base = base.replace(match.value, "")
        }
        eRegexOnly2.containsMatchIn(base) -> {
            val match = eRegexOnly2.find(base)!!
            episode = match.groupValues[1].toIntOrNull()
            base = base.replace(match.value, "")
        }
    }

    // Part
    var part: Int? = null
    val pRegex1 = Regex("(?:часть|part|ч|p|фильм)\\s*(\\d+)", RegexOption.IGNORE_CASE)
    val pRegex2 = Regex("(\\d+)\\s*-?я?\\s*(?:часть|part)", RegexOption.IGNORE_CASE)
    // Just number at the end
    val pRegex3 = Regex("\\s+(\\d+)\\s*$")

    if (season == null && episode == null) {
        when {
            pRegex1.containsMatchIn(base) -> {
                val match = pRegex1.find(base)!!
                part = match.groupValues[1].toIntOrNull()
                base = base.replace(match.value, "")
            }
            pRegex2.containsMatchIn(base) -> {
                val match = pRegex2.find(base)!!
                part = match.groupValues[1].toIntOrNull()
                base = base.replace(match.value, "")
            }
            pRegex3.containsMatchIn(base) -> {
                val match = pRegex3.find(base)!!
                part = match.groupValues[1].toIntOrNull()
                base = base.replace(match.value, "")
            }
        }
    }

    base = base.replace(Regex("[^a-zA-Zа-яА-Я0-9]"), "").lowercase(Locale.getDefault())

    return SequenceInfo(base, season, episode, part, year)
}

fun main() {
    val cases = listOf(
        "Гадкий я (2010)", 
        "Гадкий я 2 (2013) / Despicable Me",
        "История игрушек (1995) / Toy Story",
        "История игрушек 2 (1999) / Toy Story 2",
        "С приветом по планетам - 1 сезон 1 серия «Покоритель / Яй...",
        "С приветом по планетам 2 сезон 4 серия «Тут-и-Тамы / Уволен» (му...",
        "Сериал Пацаны - 5 сезон 4 серия / The Boys Dragon Money Studio"
    )
    for (c in cases) {
        println("${c} -> ${parseSequenceInfo(c)}")
    } // output
}

