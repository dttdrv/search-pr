package app.pane.core.extensions

import kotlin.math.roundToLong

/** Short numbers and ratings for add-on listings ("9.2M users", ★★★★½). */
object ExtensionFormat {

    /** `950` → "950", `1_200` → "1.2K", `48_000` → "48K", `9_150_000` → "9.2M". */
    fun compactCount(count: Long): String {
        if (count < 1_000) return count.coerceAtLeast(0).toString()
        val units = listOf(1_000L to "K", 1_000_000L to "M", 1_000_000_000L to "B")
        for ((index, unit) in units.withIndex()) {
            val (divisor, suffix) = unit
            val value = count.toDouble() / divisor
            // One decimal below 10 ("9.2M"), none above ("48K"); tenths are kept as an integer.
            val tenths = if (value < 10) (value * 10).roundToLong() else value.roundToLong() * 10
            if (tenths < 10_000 || index == units.lastIndex) return formatTenths(tenths) + suffix
        }
        return count.toString()
    }

    fun users(count: Long): String = if (count == 1L) "1 user" else "${compactCount(count)} users"

    /** "4.8"; ratings are shown with one decimal like the add-on store does. */
    fun rating(value: Double): String = formatTenths((value.coerceIn(0.0, 5.0) * 10).roundToLong())

    /** How full each of five stars is (0, 0.5 or 1), rounding the rating to the nearest half. */
    fun starFills(rating: Double): List<Float> {
        val halves = (rating.coerceIn(0.0, 5.0) * 2).roundToLong() / 2.0
        return List(5) { i -> (halves - i).coerceIn(0.0, 1.0).toFloat() }
    }

    private fun formatTenths(tenths: Long): String =
        if (tenths % 10 == 0L) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"
}

/** When the daily background update check is due. */
object ExtensionUpdates {
    const val INTERVAL_MS: Long = 24 * 60 * 60 * 1000L

    /** Due when never checked, a full interval has passed, or the clock went backwards. */
    fun isDue(lastCheckMs: Long, nowMs: Long, intervalMs: Long = INTERVAL_MS): Boolean =
        lastCheckMs <= 0L || nowMs < lastCheckMs || nowMs - lastCheckMs >= intervalMs
}
