package dev.garden.reader.features

import android.content.Context
import java.time.LocalDate
import java.security.MessageDigest

data class ReadingSnapshot(val days: Map<LocalDate, Long>, val articles: Int) {
    val total: Long get() = days.values.sum()
}
class ReadingStats(context: Context) {
    private val preferences = context.getSharedPreferences("reading_stats_v1", Context.MODE_PRIVATE)
    companion object { private val lock = Any() }
    fun add(day: LocalDate, millis: Long, url: String) = synchronized(lock) {
        require(millis in 1..60_000)
        val key = "day_$day"
        val seen = preferences.getStringSet("articles", emptySet()).orEmpty().toMutableSet()
        val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        if (seen.size < 20000) seen.add(hash)
        preferences.edit().putLong(key, preferences.getLong(key, 0) + millis).putStringSet("articles", seen).apply()
    }
    fun snapshot(): ReadingSnapshot = synchronized(lock) {
        val days = preferences.all.mapNotNull { (key, value) ->
            if (!key.startsWith("day_") || value !is Long) null else runCatching { LocalDate.parse(key.removePrefix("day_")) to value }.getOrNull()
        }.toMap()
        ReadingSnapshot(days, preferences.getStringSet("articles", emptySet()).orEmpty().size)
    }
}
