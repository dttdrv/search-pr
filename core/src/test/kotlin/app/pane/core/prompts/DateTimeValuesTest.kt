package app.pane.core.prompts

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DateTimeValuesTest {
    private fun roundTrip(kind: DateTimeKind, value: String) =
        assertEquals(value, DateTimeValues.format(kind, DateTimeValues.parse(kind, value)!!))

    @Test fun roundTripsEveryKind() {
        roundTrip(DateTimeKind.Date, "2026-09-25")
        roundTrip(DateTimeKind.Month, "2026-09")
        roundTrip(DateTimeKind.Week, "2026-W39")
        roundTrip(DateTimeKind.Time, "07:05")
        roundTrip(DateTimeKind.DateTimeLocal, "2026-09-25T23:59")
    }

    @Test fun acceptsLooserEngineValues() {
        assertEquals("14:30", DateTimeValues.format(DateTimeKind.Time, DateTimeValues.parse(DateTimeKind.Time, "14:30:15.250")!!))
        assertEquals(
            "2026-09-25T08:00",
            DateTimeValues.format(DateTimeKind.DateTimeLocal, DateTimeValues.parse(DateTimeKind.DateTimeLocal, "2026-09-25 08:00:30")!!),
        )
    }

    @Test fun weeksFollowIso8601() {
        // 1 January 2021 was a Friday and belongs to the last week of 2020.
        val jan1 = LocalDate.of(2021, 1, 1).atStartOfDay()
        assertEquals("2020-W53", DateTimeValues.format(DateTimeKind.Week, jan1))
        assertEquals(LocalDate.of(2020, 12, 28).atStartOfDay(), DateTimeValues.parse(DateTimeKind.Week, "2020-W53"))
        assertNull(DateTimeValues.parse(DateTimeKind.Week, "2021-W53"))
        assertNull(DateTimeValues.parse(DateTimeKind.Week, "2021-W00"))
        assertEquals(53, DateTimeValues.weeksInYear(2020))
        assertEquals(52, DateTimeValues.weeksInYear(2021))
        assertEquals(LocalDate.of(2021, 12, 27), DateTimeValues.weekStart(2021, 60))
        assertEquals(LocalDate.of(2021, 1, 4), DateTimeValues.weekStart(2021, 1))
    }

    @Test fun rejectsGarbage() {
        assertNull(DateTimeValues.parse(DateTimeKind.Date, "2026-02-30"))
        assertNull(DateTimeValues.parse(DateTimeKind.Month, "2026-13"))
        assertNull(DateTimeValues.parse(DateTimeKind.Time, "25:00"))
        assertNull(DateTimeValues.parse(DateTimeKind.Date, ""))
        assertNull(DateTimeValues.parse(DateTimeKind.Date, null))
    }

    @Test fun normalizesToWhatTheKindCanExpress() {
        val t = LocalDateTime.of(2026, 9, 25, 14, 37, 12)
        assertEquals(LocalDateTime.of(2026, 9, 1, 0, 0), DateTimeValues.normalize(DateTimeKind.Month, t))
        assertEquals(LocalDateTime.of(2026, 9, 21, 0, 0), DateTimeValues.normalize(DateTimeKind.Week, t))
        assertEquals(DateTimeValues.TIME_BASE_DATE.atTime(14, 37), DateTimeValues.normalize(DateTimeKind.Time, t))
        assertEquals(LocalDateTime.of(2026, 9, 25, 14, 37), DateTimeValues.normalize(DateTimeKind.DateTimeLocal, t))
    }

    @Test fun initialValueIsClampedToRange() {
        val now = LocalDateTime.of(2026, 9, 25, 10, 0)
        assertEquals("2026-09-25", DateTimeValues.format(DateTimeKind.Date, DateTimeValues.initial(DateTimeKind.Date, null, null, null, now)))
        assertEquals(
            "2027-01-01",
            DateTimeValues.format(DateTimeKind.Date, DateTimeValues.initial(DateTimeKind.Date, "", "2027-01-01", "2027-12-31", now)),
        )
        assertEquals(
            "09:00",
            DateTimeValues.format(DateTimeKind.Time, DateTimeValues.initial(DateTimeKind.Time, "22:00", "08:00", "09:00", now)),
        )
        // An inverted range is ignored rather than trapping the picker.
        assertEquals(
            "2026-05-05",
            DateTimeValues.format(DateTimeKind.Date, DateTimeValues.initial(DateTimeKind.Date, "2026-05-05", "2026-12-01", "2026-01-01", now)),
        )
    }
}
