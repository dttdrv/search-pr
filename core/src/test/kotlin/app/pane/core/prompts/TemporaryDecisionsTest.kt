package app.pane.core.prompts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TemporaryDecisionsTest {
    private var now = 0L
    private val decisions = TemporaryDecisions<String>(ttlMs = 1_000, clock = { now })

    @Test fun remembersUntilExpiry() {
        assertNull(decisions.lookup("geo|a.example"))
        decisions.record("geo|a.example", allowed = true)
        decisions.record("cam|a.example", allowed = false)
        now = 999
        assertEquals(true, decisions.lookup("geo|a.example"))
        assertEquals(false, decisions.lookup("cam|a.example"))
        now = 1_000
        assertNull(decisions.lookup("geo|a.example"))
    }

    @Test fun laterAnswerReplacesEarlierOne() {
        decisions.record("k", allowed = false)
        now = 900
        decisions.record("k", allowed = true)
        now = 1_500
        assertEquals(true, decisions.lookup("k"))
        decisions.forget("k")
        assertNull(decisions.lookup("k"))
    }
}
