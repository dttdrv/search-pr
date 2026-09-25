package app.pane.core.library

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** A day bucket in the history list: Today, Yesterday, a weekday within the last week, or Earlier. */
sealed interface DaySection {
    /** Days before today this section starts at; orders sections newest first. */
    val daysAgo: Int

    fun title(locale: Locale = Locale.getDefault()): String

    data object Today : DaySection {
        override val daysAgo = 0
        override fun title(locale: Locale) = "Today"
    }

    data object Yesterday : DaySection {
        override val daysAgo = 1
        override fun title(locale: Locale) = "Yesterday"
    }

    /** A day 2–6 days ago; the name alone is unambiguous because it can't repeat within a week. */
    data class Weekday(val day: DayOfWeek, override val daysAgo: Int) : DaySection {
        override fun title(locale: Locale): String = day.getDisplayName(TextStyle.FULL, locale)
    }

    data object Earlier : DaySection {
        override val daysAgo = 7
        override fun title(locale: Locale) = "Earlier"
    }
}

data class HistoryGroup<T>(val section: DaySection, val items: List<T>)

/**
 * Buckets history into the sections Safari shows. Kept free of Android types so the calendar edge
 * cases (midnight, week boundaries, clock skew) are unit-tested.
 */
object HistoryGrouping {

    fun sectionFor(time: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): DaySection {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val date = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(date, today)
        return when {
            // Visits stamped in the future (clock changes) still belong to today.
            days <= 0L -> DaySection.Today
            days == 1L -> DaySection.Yesterday
            days < 7L -> DaySection.Weekday(date.dayOfWeek, days.toInt())
            else -> DaySection.Earlier
        }
    }

    /**
     * Groups [items] by day, newest section first. Items keep their relative order within a
     * section, so pass them sorted newest first.
     */
    fun <T> group(items: List<T>, now: Long, zone: ZoneId = ZoneId.systemDefault(), time: (T) -> Long): List<HistoryGroup<T>> {
        val buckets = LinkedHashMap<DaySection, MutableList<T>>()
        for (item in items) {
            buckets.getOrPut(sectionFor(time(item), now, zone)) { mutableListOf() } += item
        }
        return buckets.entries
            .sortedBy { it.key.daysAgo }
            .map { HistoryGroup(it.key, it.value) }
    }
}
