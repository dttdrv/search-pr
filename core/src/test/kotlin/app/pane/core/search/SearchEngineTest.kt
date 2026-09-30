package app.pane.core.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchEngineTest {
    @Test fun buildsEncodedUrls() {
        assertEquals("https://duckduckgo.com/?q=a+%26+b%3F", SearchEngines.DuckDuckGo.searchUrl("a & b?"))
    }

    @Test fun extractsQueryBack() {
        val url = SearchEngines.DuckDuckGo.searchUrl("kotlin flows")
        assertEquals("kotlin flows", SearchEngines.DuckDuckGo.extractQuery(url))
        assertEquals("cats", SearchEngines.Google.extractQuery("https://www.google.com/search?q=cats&hl=en"))
        assertNull(SearchEngines.Google.extractQuery("https://example.com/?q=cats"))
    }

    @Test fun keywords() {
        val (engine, q) = SearchEngines.parseKeyword("@w Ada Lovelace")!!
        assertEquals("wikipedia", engine.id)
        assertEquals("Ada Lovelace", q)
        assertNull(SearchEngines.parseKeyword("@nope thing"))
        assertNull(SearchEngines.parseKeyword("plain"))
    }

    @Test fun unknownIdFallsBackToDuckDuckGo() {
        assertEquals("ddg", SearchEngines.byId("missing").id)
    }

    @Test fun parsesOpenSearchSuggestions() {
        assertEquals(listOf("kotlin", "kotlin flow"), Suggestions.parse("""["kot",["kotlin","kotlin flow",""]]""", SuggestFormat.OpenSearch))
    }

    @Test fun parsesDuckDuckGoObjects() {
        assertEquals(listOf("a", "b"), Suggestions.parse("""[{"phrase":"a"},{"phrase":"b"}]""", SuggestFormat.DuckDuckGo))
    }

    @Test fun malformedIsEmpty() {
        assertEquals(emptyList(), Suggestions.parse("<html>", SuggestFormat.OpenSearch))
        assertEquals(emptyList(), Suggestions.parse("{}", SuggestFormat.OpenSearch))
    }

    @Test fun extractsReorderedQueriesWithoutFragmentsAndRejectsOtherPaths() {
        assertEquals("cats & dogs", SearchEngines.Google.extractQuery("https://www.google.com/search?hl=en&q=cats+%26+dogs#top"))
        assertNull(SearchEngines.Google.extractQuery("https://www.google.com/other?q=cats"))
        assertNull(SearchEngines.Google.extractQuery("https://www.google.com.evil.com/search?q=cats"))
        assertNull(SearchEngines.Google.extractQuery("https://evil@www.google.com/search?q=cats"))
        assertNull(SearchEngines.Google.extractQuery("https://www.google.com/search?q=%ZZ"))
    }

    @Test fun keywordSeparatorsIncludeTabsAndNewlines() {
        assertEquals(SearchEngines.Wikipedia to "Ada Lovelace", SearchEngines.parseKeyword("@w\tAda Lovelace"))
        assertEquals(SearchEngines.GitHub to "kotlin", SearchEngines.parseKeyword("@gh\nkotlin"))
    }
}
