package app.pane.core.library

import java.time.Instant
import java.time.ZoneId

/**
 * The spans offered when clearing history or browsing data, worded the way Safari words them.
 * Day boundaries follow the device's calendar, so "Today" means since local midnight rather than
 * the last 24 hours.
 */
enum class TimeRange(val label: String) {
    LastHour("Last Hour"),
    Today("Today"),
    TodayAndYesterday("Today and Yesterday"),
    AllTime("All Time"),
    ;

    /** Start of the range in epoch millis. [AllTime] is 0, so "delete since" removes everything. */
    fun since(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long = when (this) {
        LastHour -> now - HOUR_MS
        Today -> startOfDay(now, zone, daysBack = 0)
        TodayAndYesterday -> startOfDay(now, zone, daysBack = 1)
        AllTime -> 0L
    }
}

private const val HOUR_MS = 3_600_000L

/** Local midnight [daysBack] days before the day containing [now]. */
internal fun startOfDay(now: Long, zone: ZoneId, daysBack: Long): Long =
    Instant.ofEpochMilli(now).atZone(zone).toLocalDate().minusDays(daysBack).atStartOfDay(zone).toInstant().toEpochMilli()
