package app.pane.core.prompts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColorValuesTest {
    @Test fun normalizesHtmlColorValues() {
        assertEquals("#aabbcc", ColorValues.normalize("#AABBCC"))
        assertEquals("#aabbcc", ColorValues.normalize(" aabbcc "))
        assertEquals("#aabbcc", ColorValues.normalize("#abc"))
        assertNull(ColorValues.normalize("#abcd"))
        assertNull(ColorValues.normalize("red"))
        assertNull(ColorValues.normalize("#ggg"))
        assertNull(ColorValues.normalize(null))
    }

    @Test fun argbRoundTrip() {
        assertEquals(0xFF0A7AFF.toInt(), ColorValues.toArgb("#0a7aff"))
        assertEquals("#0a7aff", ColorValues.fromArgb(0xFF0A7AFF.toInt()))
        assertEquals("#000000", ColorValues.fromArgb(0xFF000000.toInt()))
    }

    @Test fun hslConversion() {
        assertEquals("#ff0000", ColorValues.fromHsl(0f, 1f, 0.5f))
        assertEquals("#008000", ColorValues.fromHsl(120f, 1f, 0.25f))
        assertEquals("#0000ff", ColorValues.fromHsl(240f, 1f, 0.5f))
        assertEquals("#808080", ColorValues.fromHsl(77f, 0f, 0.5f))
        assertEquals("#ff0000", ColorValues.fromHsl(360f, 1f, 0.5f))
    }

    @Test fun gridIsRectangularAndValid() {
        val grid = ColorValues.grid
        assertEquals(0, grid.size % ColorValues.GRID_COLUMNS)
        assertEquals("#ffffff", grid.first())
        assertEquals("#000000", grid[ColorValues.GRID_COLUMNS - 1])
        grid.forEach { assertEquals(it, ColorValues.normalize(it)) }
        assertEquals(grid.size, grid.toSet().size)
    }

    @Test fun lightness() {
        assertTrue(ColorValues.isLight("#ffffff"))
        assertTrue(ColorValues.isLight("#ffcc00"))
        assertFalse(ColorValues.isLight("#000000"))
        assertFalse(ColorValues.isLight("#0a4aff"))
    }
}
