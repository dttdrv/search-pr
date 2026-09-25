package app.pane.core.prompts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PromptTextTest {
    @Test fun dialogSourcePrefersTheCallingFrame() {
        assertEquals("ads.example", PromptText.dialogSource("The page at https://ads.example says:", "https://news.example/story"))
        assertEquals("example.com", PromptText.dialogSource("Die Seite https://www.example.com:8443 meldet:", null))
        assertEquals("news.example", PromptText.dialogSource("This page says:", "https://news.example/story"))
        assertEquals("news.example", PromptText.dialogSource(null, "https://news.example/"))
        assertNull(PromptText.dialogSource(null, null))
    }

    @Test fun findCounter() {
        assertEquals("3 of 12", PromptText.findCounter(found = true, current = 3, total = 12))
        assertEquals("No results", PromptText.findCounter(found = false, current = 0, total = 0))
        assertEquals("No results", PromptText.findCounter(found = true, current = 0, total = 0))
        assertEquals("5 of many", PromptText.findCounter(found = true, current = 5, total = -1))
    }

    @Test fun shorten() {
        assertEquals("short", PromptText.shorten("short"))
        assertEquals(120, PromptText.shorten("x".repeat(300)).length)
    }
}
