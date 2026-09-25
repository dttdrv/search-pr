package app.pane.core.extensions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtensionFormatTest {

    @Test fun compactCounts() {
        val cases = mapOf(
            0L to "0",
            7L to "7",
            999L to "999",
            1_000L to "1K",
            1_200L to "1.2K",
            9_960L to "10K",
            48_000L to "48K",
            999_400L to "999K",
            999_600L to "1M",
            9_200_000L to "9.2M",
            12_345_678L to "12M",
            2_500_000_000L to "2.5B",
        )
        cases.forEach { (n, expected) -> assertEquals(expected, ExtensionFormat.compactCount(n), "$n") }
    }

    @Test fun users() {
        assertEquals("1 user", ExtensionFormat.users(1))
        assertEquals("0 users", ExtensionFormat.users(0))
        assertEquals("9.2M users", ExtensionFormat.users(9_200_000))
    }

    @Test fun ratings() {
        assertEquals("4.8", ExtensionFormat.rating(4.78))
        assertEquals("5", ExtensionFormat.rating(5.0))
        assertEquals("0", ExtensionFormat.rating(-1.0))
    }

    @Test fun starFillsRoundToHalves() {
        assertEquals(listOf(1f, 1f, 1f, 1f, 0.5f), ExtensionFormat.starFills(4.6))
        assertEquals(listOf(1f, 1f, 1f, 1f, 1f), ExtensionFormat.starFills(4.8))
        assertEquals(listOf(1f, 1f, 1f, 0f, 0f), ExtensionFormat.starFills(3.2))
        assertEquals(listOf(0f, 0f, 0f, 0f, 0f), ExtensionFormat.starFills(0.0))
    }

    @Test fun updateSchedule() {
        val day = ExtensionUpdates.INTERVAL_MS
        assertTrue(ExtensionUpdates.isDue(0, 1_000))
        assertFalse(ExtensionUpdates.isDue(10_000, 10_000 + day - 1))
        assertTrue(ExtensionUpdates.isDue(10_000, 10_000 + day))
        assertTrue(ExtensionUpdates.isDue(10_000, 5_000), "clock moved backwards")
    }
}
