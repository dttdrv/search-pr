package app.pane.core.library

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ByteSizesTest {
    private fun f(bytes: Long) = ByteSizes.format(bytes, Locale.US)

    @Test fun smallSizesInBytes() {
        assertEquals("0 bytes", f(0))
        assertEquals("1 byte", f(1))
        assertEquals("999 bytes", f(999))
    }

    @Test fun decimalUnits() {
        assertEquals("1 KB", f(1_000))
        assertEquals("14 KB", f(14_200))
        assertEquals("3.4 MB", f(3_400_000))
        assertEquals("12.3 MB", f(12_345_678))
        assertEquals("123 MB", f(123_456_789))
        assertEquals("1.2 GB", f(1_234_567_890))
    }

    @Test fun dropsTrailingZeroDecimal() {
        assertEquals("1 MB", f(1_000_000))
        assertEquals("2 MB", f(1_960_000))
    }

    @Test fun roundsUpIntoNextUnit() {
        assertEquals("999 KB", f(999_400))
        assertEquals("1 MB", f(999_600))
    }

    @Test fun usesLocaleDecimalSeparator() {
        assertEquals("3,4 MB", ByteSizes.format(3_400_000, Locale.GERMANY))
    }

    @Test fun negativeIsUnknown() {
        assertEquals("", f(-1))
    }

    @Test fun progressText() {
        assertEquals("1.2 MB of 3.4 MB", ByteSizes.progress(1_200_000, 3_400_000, Locale.US))
        assertEquals("1.2 MB", ByteSizes.progress(1_200_000, -1, Locale.US))
    }

    @Test fun fractionIsClampedAndNullWhenUnknown() {
        assertEquals(0.5f, ByteSizes.fraction(50, 100))
        assertEquals(1f, ByteSizes.fraction(150, 100))
        assertNull(ByteSizes.fraction(50, -1))
    }
}
