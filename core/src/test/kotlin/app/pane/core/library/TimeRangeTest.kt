package app.pane.core.library

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class TimeRangeTest {
    private val zone = ZoneId.of("America/New_York")

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    private val now = at(2026, 9, 25, 14, 15)

    @Test fun lastHourIsRelative() {
        assertEquals(now - 3_600_000L, TimeRange.LastHour.since(now, zone))
    }

    @Test fun todayIsLocalMidnight() {
        assertEquals(at(2026, 9, 25, 0), TimeRange.Today.since(now, zone))
    }

    @Test fun todayAndYesterdayGoesBackOneCalendarDay() {
        assertEquals(at(2026, 9, 24, 0), TimeRange.TodayAndYesterday.since(now, zone))
        // Across a month boundary.
        assertEquals(at(2026, 8, 31, 0), TimeRange.TodayAndYesterday.since(at(2026, 9, 1, 0, 5), zone))
    }

    @Test fun allTimeStartsAtEpoch() {
        assertEquals(0L, TimeRange.AllTime.since(now, zone))
    }

    @Test fun springForwardMidnightStillResolves() {
        // Clocks jump 02:00 → 03:00 on 2026-03-08 in New York; midnight itself exists.
        val later = at(2026, 3, 8, 12)
        assertEquals(at(2026, 3, 8, 0), TimeRange.Today.since(later, zone))
    }
}
