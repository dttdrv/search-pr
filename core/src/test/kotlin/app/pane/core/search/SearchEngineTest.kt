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
}
