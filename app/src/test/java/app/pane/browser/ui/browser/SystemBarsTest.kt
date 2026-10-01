package app.pane.browser.ui.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemBarsTest {
    private val all = listOf(false, true)

    @Test fun thePageDecidesWhileNothingCoversIt() {
        for (priv in all) for (dark in all) {
            assertNull(coverIsDark(pushed = false, tabs = false, editing = false, privateGround = priv, appDark = dark))
        }
    }

    // a pushed screen is drawn in the app's theme even while a private tab is selected underneath
    @Test fun aPushedScreenUsesTheAppGroundNotThePrivateOne() {
        for (priv in all) for (dark in all) {
            assertEquals(dark, coverIsDark(pushed = true, tabs = false, editing = false, privateGround = priv, appDark = dark))
        }
    }

    @Test fun theTabOverviewAndTheEditorAreDarkOverPrivateTabsAndFollowTheAppOtherwise() {
        for (tabs in all) for (dark in all) {
            val editing = !tabs
            assertEquals(true, coverIsDark(pushed = false, tabs = tabs, editing = editing, privateGround = true, appDark = dark))
            assertEquals(dark, coverIsDark(pushed = false, tabs = tabs, editing = editing, privateGround = false, appDark = dark))
        }
    }
}
