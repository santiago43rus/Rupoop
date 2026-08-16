package com.santiago43rus.rupoop.parser

import java.util.regex.Pattern

object JsPackerUnpacker {

    private val PACKER_PATTERN = Pattern.compile(
        """eval\(function\(p,a,c,k,e,[rd]\)\{.+?\}\s*\(\s*['"](.*?)['"]\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*['"](.*?)['"]\s*\.split\(['"]\\?\|['"]\)[^)]*\)\s*\)""",
        Pattern.DOTALL
    )

    fun unpack(packedJs: String): String {
        var current = packedJs
        var iterations = 0
        while (iterations < 5 && current.contains("eval(function(p,a,c,k,e,")) {
            val unpacked = unpackAllInText(current)
            if (unpacked == current) break
            current = unpacked
            iterations++
        }
        return current
    }

    private fun unpackAllInText(text: String): String {
        val matcher = PACKER_PATTERN.matcher(text)
        if (!matcher.find()) return text

        val sb = StringBuffer()
        matcher.reset()
        while (matcher.find()) {
            val payload = matcher.group(1) ?: ""
            val radix = matcher.group(2)?.toIntOrNull() ?: 62
            val count = matcher.group(3)?.toIntOrNull() ?: 0
            val rawKeywords = matcher.group(4) ?: ""
            val keywords = rawKeywords.split("|")

            val dict = HashMap<String, String>()
            for (i in 0 until count) {
                val key = encodeRadix(i, radix)
                val value = if (i < keywords.size && keywords[i].isNotEmpty()) keywords[i] else key
                dict[key] = value
            }

            val wordPattern = Pattern.compile("""\b\w+\b""")
            val wordMatcher = wordPattern.matcher(payload)
            val unpackedChunk = StringBuffer()
            while (wordMatcher.find()) {
                val word = wordMatcher.group()
                val replacement = dict[word] ?: word
                wordMatcher.appendReplacement(unpackedChunk, MatcherQuote(replacement))
            }
            wordMatcher.appendTail(unpackedChunk)

            matcher.appendReplacement(sb, MatcherQuote(unpackedChunk.toString()))
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    private fun encodeRadix(number: Int, radix: Int): String {
        if (number == 0) return "0"
        val chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        var n = number
        val sb = StringBuilder()
        while (n > 0) {
            sb.append(chars[n % radix])
            n /= radix
        }
        return sb.reverse().toString()
    }

    private fun MatcherQuote(s: String): String {
        return s.replace("\\", "\\\\").replace("$", "\\$")
    }
}
