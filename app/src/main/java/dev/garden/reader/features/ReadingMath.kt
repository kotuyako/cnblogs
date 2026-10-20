package dev.garden.reader.features

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object ReadingMath {
    fun delta(previous: Long, now: Long, active: Boolean): Long = if (active && now >= previous && now - previous <= 5000) now - previous else 0
    fun dates(today: LocalDate, weeks: Int = 26): List<LocalDate> {
        require(weeks in 1..53)
        val end = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val start = end.minusDays(weeks * 7L - 1)
        return List(weeks * 7) { start.plusDays(it.toLong()) }
    }
    fun level(millis: Long): Int = when {
        millis <= 0 -> 0; millis < 5 * 60_000 -> 1; millis < 15 * 60_000 -> 2; millis < 30 * 60_000 -> 3; else -> 4
    }
}
