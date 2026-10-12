package com.santiago43rus.rupoop.util

import okhttp3.OkHttpClient
import okhttp3.Request

object HlsVariantPicker {
    private val client = OkHttpClient()
    private val bandwidth = Regex("BANDWIDTH=(\\d+)")
    private val audioRendition = Regex("#EXT-X-MEDIA:[^\\n]*TYPE=AUDIO[^\\n]*URI=\"([^\"]+)\"")

    private fun fetch(url: String): String =
        client.newCall(Request.Builder().url(url).build()).execute().use { it.body.string() }

    private fun absolute(masterUrl: String, uri: String): String =
        if (uri.startsWith("http")) uri else masterUrl.substringBeforeLast("/") + "/" + uri

    fun audioOnlyUrl(masterUrl: String): String? {
        val body = fetch(masterUrl)
        if (!body.contains("#EXT-X-STREAM-INF")) return masterUrl
        audioRendition.find(body)?.let { return absolute(masterUrl, it.groupValues[1]) }

        val lines = body.lines()
        val variants = lines.indices.mapNotNull { i ->
            if (!lines[i].startsWith("#EXT-X-STREAM-INF")) return@mapNotNull null
            val uri = lines.getOrNull(i + 1)?.trim().orEmpty()
            if (uri.isEmpty() || uri.startsWith("#")) return@mapNotNull null
            val bw = bandwidth.find(lines[i])?.groupValues?.get(1)?.toLongOrNull() ?: Long.MAX_VALUE
            bw to absolute(masterUrl, uri)
        }
        return variants.minByOrNull { it.first }?.second
    }
}
