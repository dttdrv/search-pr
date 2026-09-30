package app.pane.core.suggest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SuggestTest {
    private val day = 86_400_000L
    private val now = 100 * day

    @Test fun recentAndFrequentWins() {
        val old = Frecency.score(50, now - 90 * day, false, now)
        val fresh = Frecency.score(5, now - day, false, now)
        assertTrue(fresh > old)
        assertTrue(Frecency.score(1, now, true, now) > Frecency.score(1, now, false, now))
    }

    @Test fun autocompletesKnownHosts() {
        val places = listOf(
            PlaceCandidate("https://github.com/x", "GitHub", 30, now),
            PlaceCandidate("https://gitlab.com/", "GitLab", 1, now - 60 * day),
        )
        assertEquals("github.com", Autocomplete.complete("gi", places, now))
        assertEquals("gitlab.com", Autocomplete.complete("gitl", places, now))
        assertNull(Autocomplete.complete("github.com", places, now))
        assertNull(Autocomplete.complete("git hub", places, now))
    }

    @Test fun autocompleteSkipsWww() {
        val places = listOf(PlaceCandidate("https://www.wikipedia.org/", "Wikipedia", 3, now))
        assertEquals("wikipedia.org", Autocomplete.complete("wiki", places, now))
    }

    @Test fun rankingMergesSourcesWithoutDuplicates() {
        val places = listOf(
            PlaceCandidate("https://kotlinlang.org/docs", "Kotlin Docs", 10, now),
            PlaceCandidate("https://example.com/", "Unrelated", 100, now),
        )
        val tabs = listOf(Suggestion.OpenTab("t1", "https://kotlinlang.org/", "Kotlin"))
        val out = SuggestionRanker.rank("kotlin", places, tabs, listOf("kotlin", "kotlin coroutines"), now)
        assertEquals(
            listOf("tab:t1", "place:https://kotlinlang.org/docs", "search:kotlin", "search:kotlin coroutines"),
            out.map { it.key },
        )
    }

    @Test fun everyTokenMustMatch() {
        val places = listOf(PlaceCandidate("https://a.com/", "Rust book", 1, now))
        assertTrue(SuggestionRanker.rank("rust go", places, emptyList(), emptyList(), now).none { it is Suggestion.Place })
    }

    @Test fun mergedHistoryAndBookmarkRetainBothFrequencyAndBookmarkName() {
        val places = listOf(
            PlaceCandidate("https://example.com/", "Old page title", 20, now),
            PlaceCandidate("https://example.com/", "My saved page", 1, now - day, bookmarked = true),
        )
        val suggestion = SuggestionRanker.rank("example", places, emptyList(), emptyList(), now).first() as Suggestion.Place
        assertEquals("My saved page", suggestion.title)
        assertTrue(suggestion.bookmarked)
    }

    @Test fun completesExplicitSchemesButNotPathsOrCredentials() {
        val places = listOf(PlaceCandidate("https://github.com/", "GitHub", 3, now))
        assertEquals("github.com", Autocomplete.complete("https://gi", places, now))
        assertNull(Autocomplete.complete("github.com/a", places, now))
        assertNull(Autocomplete.complete("user@gi", places, now))
    }
}
