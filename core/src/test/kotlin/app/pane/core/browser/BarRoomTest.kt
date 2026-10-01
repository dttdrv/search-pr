package app.pane.core.browser

import kotlin.test.Test
import kotlin.test.assertEquals

class BarRoomTest {
    private val bar = 70f
    private val label = 50f
    private val find = 72f
    private val nav = 24f

    private fun of(onPage: Boolean = true, fullscreen: Boolean = false, keyboard: Boolean = false, finding: Boolean = false, folded: Boolean = false) =
        BarRoom.of(onPage, fullscreen, keyboard, finding, folded, bar, label, find, nav)

    @Test fun theOpenBarCoversItsZoneAndTheNavigationInset() {
        assertEquals(BarRoom((bar + nav).toInt(), (bar + nav).toInt()), of())
    }

    @Test fun foldingMovesTheLiftDownToTheLabelButNotTheEndOfThePage() {
        val open = of()
        val folded = of(folded = true)
        assertEquals((label + nav).toInt(), folded.inset)
        assertEquals(open.room, folded.room)
    }

    @Test fun nothingIsReservedWhereTheBarIsAway() {
        assertEquals(BarRoom(0, 0), of(onPage = false))
        assertEquals(BarRoom(0, 0), of(fullscreen = true))
        assertEquals(BarRoom(0, 0), of(keyboard = true))
    }

    @Test fun theFindBarFloatsAboveTheKeyboardAndStaysOpenSized() {
        assertEquals(BarRoom(find.toInt(), find.toInt()), of(keyboard = true, finding = true))
        // folding belongs to the bar, which the find bar replaces
        assertEquals(BarRoom((find + nav).toInt(), (find + nav).toInt()), of(finding = true, folded = true))
    }
}
