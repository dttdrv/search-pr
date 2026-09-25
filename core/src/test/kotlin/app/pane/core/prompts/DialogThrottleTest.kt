package app.pane.core.prompts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DialogThrottleTest {
    private var now = 0L
    private val throttle = DialogThrottle(maxSuccessive = 3, successiveGapMs = 3_000, clock = { now })

    /** Opens and closes one dialog, as a page looping `alert()` would. */
    private fun cycle(page: String? = "evil.example", readMs: Long = 500, gapMs: Long = 50): DialogVerdict {
        now += gapMs
        val verdict = throttle.onDialogOpened(page)
        now += readMs
        if (verdict != DialogVerdict.Suppress) throttle.onDialogClosed()
        return verdict
    }

    @Test fun loopingPageGetsOptOutAfterThree() {
        assertEquals(DialogVerdict.Show, cycle())
        assertEquals(DialogVerdict.Show, cycle())
        assertEquals(DialogVerdict.Show, cycle())
        assertEquals(DialogVerdict.ShowWithOptOut, cycle())
        assertEquals(DialogVerdict.ShowWithOptOut, cycle())
    }

    @Test fun readingTimeDoesNotBreakTheStreak() {
        // The gap is measured from close to open, so slow readers still see the opt-out.
        repeat(3) { assertEquals(DialogVerdict.Show, cycle(readMs = 20_000)) }
        assertEquals(DialogVerdict.ShowWithOptOut, cycle(readMs = 20_000))
    }

    @Test fun spacedOutDialogsAreNeverThrottled() {
        repeat(10) { assertEquals(DialogVerdict.Show, cycle(gapMs = 5_000)) }
    }

    @Test fun optOutSuppressesUntilThePageChanges() {
        repeat(4) { cycle() }
        throttle.block()
        assertTrue(throttle.isBlocked)
        assertEquals(DialogVerdict.Suppress, cycle())
        assertEquals(DialogVerdict.Suppress, cycle())
        assertEquals(DialogVerdict.Show, cycle(page = "fine.example"))
        assertFalse(throttle.isBlocked)
    }

    @Test fun navigatingResetsTheStreak() {
        repeat(3) { cycle() }
        assertEquals(DialogVerdict.Show, cycle(page = "other.example"))
    }

    @Test fun simultaneousDialogsCountAsSuccessive() {
        repeat(3) { assertEquals(DialogVerdict.Show, throttle.onDialogOpened("frames.example")) }
        assertEquals(DialogVerdict.ShowWithOptOut, throttle.onDialogOpened("frames.example"))
    }
}
