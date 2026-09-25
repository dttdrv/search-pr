package app.pane.core.library

import java.util.Locale
import kotlin.math.roundToLong

/**
 * File sizes in the decimal units Files and Finder use (1 KB = 1000 bytes), with at most one
 * decimal and none when it would be ".0": "812 bytes", "14 KB", "3.4 MB", "1 GB".
 */
object ByteSizes {
    private val units = arrayOf("KB", "MB", "GB", "TB")

    fun format(bytes: Long, locale: Locale = Locale.getDefault()): String {
        if (bytes < 0) return ""
        if (bytes < 1000) return if (bytes == 1L) "1 byte" else "$bytes bytes"
        var value = bytes / 1000.0
        var unit = 0
        // Move up a unit before rounding would print "1000 KB".
        while (value >= 999.5 && unit < units.lastIndex) {
            value /= 1000.0
            unit++
        }
        val oneDecimal = unit > 0 && value < 99.95 && (value * 10).roundToLong() % 10 != 0L
        val number = if (oneDecimal) String.format(locale, "%.1f", value) else String.format(locale, "%.0f", value)
        return "$number ${units[unit]}"
    }

    /** "1.2 MB of 3.4 MB" while the total is known, otherwise just what has arrived. */
    fun progress(downloaded: Long, total: Long, locale: Locale = Locale.getDefault()): String =
        if (total > 0) "${format(downloaded, locale)} of ${format(total, locale)}" else format(downloaded, locale)

    /** Completed fraction in 0..1, or null when the total size is unknown. */
    fun fraction(downloaded: Long, total: Long): Float? =
        if (total > 0) (downloaded.toDouble() / total).coerceIn(0.0, 1.0).toFloat() else null
}
