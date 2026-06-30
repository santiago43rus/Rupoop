package com.santiago43rus.rupoop.util

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

fun Long.formatDuration(): String {
    val h = this / 3600; val m = (this % 3600) / 60; val s = this % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    else String.format(Locale.getDefault(), "%02d:%02d", m, s)
}

fun Int?.formatViewCount(): String {
    val views = this ?: 0
    if (views == 0) return "Нет просмотров"
    return when {
        views >= 1_000_000 -> String.format(Locale.getDefault(), "%.1f млн просмотров", views / 1_000_000.0).replace(",0", "").replace(".0", "")
        views >= 1_000 -> String.format(Locale.getDefault(), "%.1f тыс. просмотров", views / 1_000.0).replace(",0", "").replace(".0", "")
        else -> "$views ${getPlural(views, "просмотр", "просмотра", "просмотров")}"
    }
}

fun String?.formatTimeAgo(): String {
    if (this.isNullOrEmpty()) return ""
    try {
        var cleanDateStr = this
        if (cleanDateStr.contains(".")) {
            cleanDateStr = cleanDateStr.substringBefore(".")
        } else if (cleanDateStr.endsWith("Z")) {
            cleanDateStr = cleanDateStr.dropLast(1)
        }
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        format.timeZone = TimeZone.getTimeZone("Europe/Moscow")
        val parsedDate = format.parse(cleanDateStr) ?: return ""
        var diff = System.currentTimeMillis() - parsedDate.time
        
        if (diff < 0) diff = 0
        
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        val months = days / 30
        val years = days / 365
        
        return when {
            years > 0 -> "${years.toInt()} ${getPlural(years.toInt(), "год", "года", "лет")} назад"
            months > 0 -> "${months.toInt()} ${getPlural(months.toInt(), "месяц", "месяца", "месяцев")} назад"
            days > 0 -> "${days.toInt()} ${getPlural(days.toInt(), "день", "дня", "дней")} назад"
            hours > 0 -> "${hours.toInt()} ${getPlural(hours.toInt(), "час", "часа", "часов")} назад"
            minutes > 0 -> "${minutes.toInt()} ${getPlural(minutes.toInt(), "минуту", "минуты", "минут")} назад"
            else -> "Только что"
        }
    } catch (e: Exception) {
        return ""
    }
}

private fun getPlural(n: Int, form1: String, form2: String, form5: String): String {
    val n10 = n % 10
    val n100 = n % 100
    if (n10 == 1 && n100 != 11) return form1
    if (n10 in 2..4 && !(n100 in 12..14)) return form2
    return form5
}

fun Long.formatFileSize(): String {
    val kb = this / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format(Locale.getDefault(), "%.1f ГБ", gb)
        mb >= 1 -> String.format(Locale.getDefault(), "%.1f МБ", mb)
        else -> String.format(Locale.getDefault(), "%.0f КБ", kb)
    }
}
