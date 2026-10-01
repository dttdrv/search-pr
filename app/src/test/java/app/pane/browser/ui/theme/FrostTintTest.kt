package app.pane.browser.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertEquals
import org.junit.Test

class FrostTintTest {
    // one step of an 8-bit channel, the most a colour is stored to
    private val Step = 1f / 255f

    // the owner's rule: a frosted surface is 10% darker than a white page, and 10% lighter than a black one
    @Test fun lightFrostIsTenPercentDarkerThanAWhitePage() {
        assertEquals(0.9f, frostTint(false).compositeOver(Color.White).red, Step)
    }

    @Test fun darkFrostIsTenPercentLighterThanABlackPage() {
        assertEquals(0.1f, frostTint(true).compositeOver(Color.Black).red, Step)
    }
}
