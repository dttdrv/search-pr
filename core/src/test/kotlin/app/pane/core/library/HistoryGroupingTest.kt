package app.pane.core.library

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryGroupingTest {
    private val zone = ZoneId.of("Europe/Berlin")

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    // Friday 2026-09-25, 09:30 local.
    private val now = at(2026, 9, 25, 9, 30)

    @Test fun todayStartsAtLocalMidnight() {
        assertEquals(DaySection.Today, HistoryGrouping.sectionFor(at(2026, 9, 25, 0, 0), now, zone))
        assertEquals(DaySection.Yesterday, HistoryGrouping.sectionFor(at(2026, 9, 24, 23, 59), now, zone))
    }

    @Test fun futureVisitsCountAsToday() {
        assertEquals(DaySection.Today, HistoryGrouping.sectionFor(at(2026, 9, 26, 12), now, zone))
    }

    @Test fun lastWeekUsesWeekdayNames() {
        val section = HistoryGrouping.sectionFor(at(2026, 9, 23, 18), now, zone)
        assertEquals(DaySection.Weekday(DayOfWeek.WEDNESDAY, 2), section)
        assertEquals("Wednesday", section.title(Locale.ENGLISH))
        // Six days back is still named; seven would repeat today's weekday, so it's "Earlier".
        assertEquals(DaySection.Weekday(DayOfWeek.SATURDAY, 6), HistoryGrouping.sectionFor(at(2026, 9, 19, 8), now, zone))
        assertEquals(DaySection.Earlier, HistoryGrouping.sectionFor(at(2026, 9, 18, 23, 59), now, zone))
    }

    @Test fun groupsKeepOrderAndSortSections() {
        data class Visit(val id: Int, val time: Long)
        val visits = listOf(
            Visit(1, at(2026, 9, 25, 9)),
            Visit(2, at(2026, 9, 1, 9)),
            Visit(3, at(2026, 9, 25, 8)),
            Visit(4, at(2026, 9, 24, 8)),
            Visit(5, at(2026, 9, 22, 8)),
        )
        val groups = HistoryGrouping.group(visits, now, zone) { it.time }
        assertEquals(
            listOf(DaySection.Today, DaySection.Yesterday, DaySection.Weekday(DayOfWeek.TUESDAY, 3), DaySection.Earlier),
            groups.map { it.section },
        )
        assertEquals(listOf(1, 3), groups[0].items.map { it.id })
        assertEquals(listOf(2), groups[3].items.map { it.id })
    }

    @Test fun daylightSavingChangeDoesNotShiftDays() {
        // Clocks go back on 2026-10-25 in Berlin; a 25-hour day must still be one day.
        val afterChange = at(2026, 10, 26, 0, 30)
        assertEquals(DaySection.Yesterday, HistoryGrouping.sectionFor(at(2026, 10, 25, 0, 30), afterChange, zone))
    }

    @Test fun emptyInputHasNoGroups() {
        assertEquals(emptyList(), HistoryGrouping.group(emptyList<Long>(), now, zone) { it })
    }
}
