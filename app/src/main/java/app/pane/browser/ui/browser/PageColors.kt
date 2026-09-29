package app.pane.browser.ui.browser

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** What the edges of the page look like right now, so the chrome can match the site. */
data class PageEdges(
    /** The colour of the page along its top edge; the status area is painted with it. */
    val top: Color,
    /** How light the page is behind the floating bar (0 = black, 1 = white). */
    val bottomLuma: Float,
)

/**
 * Reads the colours the page is actually showing rather than trusting `theme-color`, which most
 * sites don't set and many set to something other than what's on screen. A handful of pixels
 * along the top edge decide the status area; the band behind the bar decides whether the glass
 * should be light or dark.
 */
object PageColors {
    private const val COLUMNS = 48

    fun sample(bitmap: Bitmap, bottomBandPx: Int): PageEdges? {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 8 || h < 8 || bitmap.isRecycled) return null
        return try {
            PageEdges(topColor(bitmap, w), bottomLuma(bitmap, w, h, bottomBandPx.coerceIn(8, h)))
        } catch (_: Exception) {
            null
        }
    }

    /** The dominant colour of the top rows; a busy image falls back to their average. */
    private fun topColor(bitmap: Bitmap, w: Int): Color {
        val rows = intArrayOf(1, 3, 6)
        val buckets = HashMap<Int, IntArray>()
        var sumR = 0
        var sumG = 0
        var sumB = 0
        var n = 0
        for (y in rows) {
            for (i in 0 until COLUMNS) {
                val px = bitmap.getPixel((i * (w - 1)) / (COLUMNS - 1), y)
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                sumR += r
                sumG += g
                sumB += b
                n++
                val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
                val bucket = buckets.getOrPut(key) { IntArray(4) }
                bucket[0]++
                bucket[1] += r
                bucket[2] += g
                bucket[3] += b
            }
        }
        val best = buckets.values.maxByOrNull { it[0] }
        return if (best != null && best[0] * 100 >= n * 45) {
            Color(best[1] / best[0], best[2] / best[0], best[3] / best[0])
        } else {
            Color(sumR / n, sumG / n, sumB / n)
        }
    }

    private fun bottomLuma(bitmap: Bitmap, w: Int, h: Int, band: Int): Float {
        var total = 0f
        var n = 0
        val top = h - band
        for (row in 0 until 4) {
            val y = top + (row * (band - 1)) / 3
            for (i in 0 until COLUMNS) {
                val px = bitmap.getPixel((i * (w - 1)) / (COLUMNS - 1), y.coerceIn(0, h - 1))
                total += Color(px).luminance()
                n++
            }
        }
        return total / n
    }
}
