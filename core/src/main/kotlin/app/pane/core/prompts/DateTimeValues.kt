package app.pane.core.prompts

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/** The HTML input types that open a date/time picker. */
enum class DateTimeKind { Date, Month, Week, Time, DateTimeLocal }

/**
 * Converts between the value strings HTML date inputs use (`2026-09-25`, `2026-09`, `2026-W39`,
 * `14:05`, `2026-09-25T14:05`) and [LocalDateTime], which the pickers edit.
 *
 * Every value is normalised to what its kind can express (a month is its first day, a week its
 * Monday, a time sits on [TIME_BASE_DATE]) so values of one kind compare and clamp correctly.
 */
object DateTimeValues {
    /** The date that time-only values are placed on. */
    val TIME_BASE_DATE: LocalDate = LocalDate.of(2000, 1, 1)

    private val month = Regex("(\\d{4,6})-(\\d{2})")
    private val week = Regex("(\\d{4,6})-W(\\d{2})")

    fun parse(kind: DateTimeKind, value: String?): LocalDateTime? {
        val v = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            when (kind) {
                DateTimeKind.Date -> LocalDate.parse(v).atStartOfDay()
                DateTimeKind.Month -> month.matchEntire(v)?.let { m ->
                    LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), 1).atStartOfDay()
                }
                DateTimeKind.Week -> week.matchEntire(v)?.let { m -> mondayOfWeek(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
                DateTimeKind.Time -> LocalTime.parse(v).atDate(TIME_BASE_DATE)
                DateTimeKind.DateTimeLocal -> LocalDateTime.parse(v.replace(' ', 'T'))
            }?.let { normalize(kind, it) }
        } catch (_: DateTimeException) {
            null
        }
    }

    fun format(kind: DateTimeKind, value: LocalDateTime): String {
        val d = value.toLocalDate()
        return when (kind) {
            DateTimeKind.Date -> "${year(d.year)}-${two(d.monthValue)}-${two(d.dayOfMonth)}"
            DateTimeKind.Month -> "${year(d.year)}-${two(d.monthValue)}"
            DateTimeKind.Week -> "${year(d.get(IsoFields.WEEK_BASED_YEAR))}-W${two(d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))}"
            DateTimeKind.Time -> "${two(value.hour)}:${two(value.minute)}"
            DateTimeKind.DateTimeLocal -> "${format(DateTimeKind.Date, value)}T${format(DateTimeKind.Time, value)}"
        }
    }

    /** Drops whatever [kind] can't express. */
    fun normalize(kind: DateTimeKind, value: LocalDateTime): LocalDateTime = when (kind) {
        DateTimeKind.Date -> value.toLocalDate().atStartOfDay()
        DateTimeKind.Month -> value.toLocalDate().withDayOfMonth(1).atStartOfDay()
        DateTimeKind.Week -> value.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay()
        DateTimeKind.Time -> value.toLocalTime().truncatedTo(ChronoUnit.MINUTES).atDate(TIME_BASE_DATE)
        DateTimeKind.DateTimeLocal -> value.truncatedTo(ChronoUnit.MINUTES)
    }

    /** Where a picker starts when the input is empty. */
    fun initial(kind: DateTimeKind, defaultValue: String?, min: String?, max: String?, now: LocalDateTime): LocalDateTime =
        clamp(parse(kind, defaultValue) ?: normalize(kind, now), parse(kind, min), parse(kind, max))

    fun clamp(value: LocalDateTime, min: LocalDateTime?, max: LocalDateTime?): LocalDateTime = when {
        min != null && max != null && min > max -> value
        min != null && value < min -> min
        max != null && value > max -> max
        else -> value
    }

    /** 52 or 53: the number of ISO weeks in [weekBasedYear]. */
    fun weeksInYear(weekBasedYear: Int): Int =
        // January 4th always falls in week 1 of its week-based year.
        LocalDate.of(weekBasedYear, 1, 4).range(IsoFields.WEEK_OF_WEEK_BASED_YEAR).maximum.toInt()

    /** The Monday of ISO [week] in [weekBasedYear]; the week is clamped to the year's weeks. */
    fun weekStart(weekBasedYear: Int, week: Int): LocalDate =
        LocalDate.of(weekBasedYear, 1, 4)
            .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week.coerceIn(1, weeksInYear(weekBasedYear)).toLong())
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun mondayOfWeek(weekBasedYear: Int, week: Int): LocalDateTime? {
        if (week < 1 || week > weeksInYear(weekBasedYear)) return null
        return weekStart(weekBasedYear, week).atStartOfDay()
    }

    // Not String.format: some locales would render non-ASCII digits, which HTML rejects.
    private fun two(n: Int) = n.toString().padStart(2, '0')
    private fun year(n: Int) = n.toString().padStart(4, '0')
}
