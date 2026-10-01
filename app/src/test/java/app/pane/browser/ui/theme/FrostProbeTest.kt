package app.pane.browser.ui.theme

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrostProbeTest {
    private fun row(vararg levels: Int) = IntArray(levels.size) { (0xFF shl 24) or (levels[it] shl 16) or (levels[it] shl 8) or levels[it] }

    // black and white stripes of one width: a blur averages them to the middle
    @Test fun evenGreyMeansTheBlurWorked() = assertNull(blurFault(row(128, 125, 131, 127)))

    // a blur that skips pixels (skia's downscale) leaves a slow ramp across the stripes, with no edge in it
    @Test fun aSlowRampMeansTheBlurWorked() = assertNull(blurFault(row(105, 108, 112, 117, 123, 128, 134, 140, 146, 150)))

    @Test fun stripesLeftAloneMeanTheBlurWasIgnored() = assertEquals("sharp", blurFault(row(0, 255, 0, 255)))

    @Test fun nothingDrawnIsEmpty() = assertEquals("empty", blurFault(IntArray(4)))

    @Test fun theGroundWithoutThePageIsNoPage() = assertEquals("no page", blurFault(row(255, 255, 255, 255)))

    @Test fun theLadderStopsAtTheFirstRadiusThatWorks() = runBlocking {
        val tried = mutableListOf<Float>()
        val verdict = frostVerdict(200f) { radius ->
            tried += radius
            if (radius <= 50f) null else "empty"
        }
        assertEquals(listOf(200f, 100f, 50f), tried)
        assertEquals(50f, verdict.radius)
    }

    @Test fun noRadiusThatWorksLeavesTheFrostSolid() = runBlocking {
        val verdict = frostVerdict(200f) { "empty" }
        assertNull(verdict.radius)
        assertEquals(4, verdict.faults.size)
    }

    @Test fun theSummaryNamesTheModeAndWhy() {
        val worked = FrostVerdict(200f, listOf("empty", null))
        assertEquals("Frost: blur radius 50 dp. Self-test: 100 dp empty, 50 dp ok", frostSummary(worked, 2f, 35))
        val failed = FrostVerdict(200f, listOf("empty", "sharp"))
        assertEquals("Frost: solid, the blur failed the self-test: 100 dp empty, 50 dp sharp", frostSummary(failed, 2f, 35))
        assertEquals("Frost: solid, API 31 is below 33", frostSummary(null, 2f, 31))
        assertEquals("Frost: solid, self-test not finished", frostSummary(null, 2f, 35))
    }
}
