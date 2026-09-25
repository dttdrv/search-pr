package app.pane.core.prompts

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Colour values for `<input type=color>`, which always speaks lowercase `#rrggbb`, plus the swatch
 * grid shown by the colour sheet (modelled on the grid tab of the iOS colour picker).
 */
object ColorValues {
    private val hexDigits = Regex("[0-9a-fA-F]+")

    /** `#abc`, `abc`, `#AABBCC` or `aabbcc` → `#aabbcc`; anything else → null. */
    fun normalize(input: String?): String? {
        val s = input?.trim()?.removePrefix("#") ?: return null
        if (!hexDigits.matches(s)) return null
        return when (s.length) {
            3 -> "#" + s.lowercase().map { "$it$it" }.joinToString("")
            6 -> "#" + s.lowercase()
            else -> null
        }
    }

    /** Opaque ARGB for a colour [normalize] accepts. */
    fun toArgb(hex: String): Int? = normalize(hex)?.let { (0xFF shl 24) or it.substring(1).toInt(16) }

    fun fromArgb(argb: Int): String = "#" + (argb and 0xFFFFFF).toString(16).padStart(6, '0')

    /** Perceived brightness above the midpoint: dark marks read better on top of it. */
    fun isLight(hex: String): Boolean {
        val rgb = toArgb(hex) ?: return false
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b) > 160
    }

    /** [hue] in degrees, [saturation] and [lightness] in 0..1. */
    fun fromHsl(hue: Float, saturation: Float, lightness: Float): String {
        val h = ((hue % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val l = lightness.coerceIn(0f, 1f)
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r, g, b) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun channel(v: Float) = ((v + m) * 255f).roundToInt().coerceIn(0, 255)
        return fromArgb((channel(r) shl 16) or (channel(g) shl 8) or channel(b))
    }

    /** Columns of [grid]. */
    const val GRID_COLUMNS = 12

    private val hues = floatArrayOf(0f, 24f, 40f, 54f, 78f, 125f, 168f, 192f, 212f, 240f, 272f, 318f)
    private val lightness = floatArrayOf(0.88f, 0.78f, 0.67f, 0.56f, 0.47f, 0.38f, 0.29f, 0.2f)

    /**
     * Row-major swatches: a white-to-black row, then each hue from pale to deep. Every row has
     * [GRID_COLUMNS] entries.
     */
    val grid: List<String> by lazy {
        val grays = (0 until GRID_COLUMNS).map { i ->
            val v = (255f * (1f - i / (GRID_COLUMNS - 1f))).roundToInt()
            fromArgb((v shl 16) or (v shl 8) or v)
        }
        val shades = lightness.flatMap { l -> hues.map { h -> fromHsl(h, if (l > 0.8f) 0.85f else 0.95f, l) } }
        grays + shades
    }
}
