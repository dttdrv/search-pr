package app.pane.core.search

import kotlinx.serialization.Serializable
import java.net.URLEncoder

/**
 * A search provider. [searchTemplate] and [suggestTemplate] contain `{searchTerms}`.
 */
@Serializable
data class SearchEngine(
    val id: String,
    val name: String,
    val searchTemplate: String,
    val suggestTemplate: String? = null,
    val suggestFormat: SuggestFormat = SuggestFormat.OpenSearch,
    /** Typed as `@keyword query` to search this engine once. */
    val keyword: String,
    val privacyFocused: Boolean = false,
) {
    fun searchUrl(query: String): String = searchTemplate.replace(TERMS, encode(query))

    fun suggestUrl(query: String): String? = suggestTemplate?.replace(TERMS, encode(query))

    /** Recovers the query from one of this engine's result pages, so the bar can show it instead of the URL. */
    fun extractQuery(url: String): String? {
        val prefix = searchTemplate.substringBefore(TERMS)
        if (!url.startsWith(prefix)) return null
        val param = prefix.substringAfterLast('?').substringAfterLast('&').removeSuffix("=")
        if (param.isEmpty()) return null
        val query = url.substringAfter('?', "").split('&')
            .firstOrNull { it.startsWith("$param=") }
            ?.substringAfter('=') ?: return null
        return try {
            java.net.URLDecoder.decode(query, "UTF-8").takeIf { it.isNotBlank() }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    companion object {
        const val TERMS = "{searchTerms}"
        private fun encode(q: String) = URLEncoder.encode(q, "UTF-8")
    }
}

@Serializable
enum class SuggestFormat {
    /** `["query", ["s1", "s2", …]]` */
    OpenSearch,

    /** DuckDuckGo: `[{"phrase": "s1"}, …]` */
    DuckDuckGo,
}

object SearchEngines {
    val DuckDuckGo = SearchEngine(
        id = "ddg", name = "DuckDuckGo", keyword = "ddg", privacyFocused = true,
        searchTemplate = "https://duckduckgo.com/?q={searchTerms}",
        suggestTemplate = "https://duckduckgo.com/ac/?q={searchTerms}&type=list",
    )
    val Startpage = SearchEngine(
        id = "startpage", name = "Startpage", keyword = "sp", privacyFocused = true,
        searchTemplate = "https://www.startpage.com/sp/search?query={searchTerms}",
        suggestTemplate = "https://www.startpage.com/osuggestions?q={searchTerms}",
    )
    val Brave = SearchEngine(
        id = "brave", name = "Brave Search", keyword = "brave", privacyFocused = true,
        searchTemplate = "https://search.brave.com/search?q={searchTerms}",
        suggestTemplate = "https://search.brave.com/api/suggest?q={searchTerms}",
    )
    val Kagi = SearchEngine(
        id = "kagi", name = "Kagi", keyword = "kagi", privacyFocused = true,
        searchTemplate = "https://kagi.com/search?q={searchTerms}",
        suggestTemplate = "https://kagi.com/api/autosuggest?q={searchTerms}",
    )
    val Mojeek = SearchEngine(
        id = "mojeek", name = "Mojeek", keyword = "mojeek", privacyFocused = true,
        searchTemplate = "https://www.mojeek.com/search?q={searchTerms}",
    )
    val Qwant = SearchEngine(
        id = "qwant", name = "Qwant", keyword = "qwant", privacyFocused = true,
        searchTemplate = "https://www.qwant.com/?q={searchTerms}",
        suggestTemplate = "https://api.qwant.com/api/suggest/?q={searchTerms}&client=opensearch",
    )
    val Ecosia = SearchEngine(
        id = "ecosia", name = "Ecosia", keyword = "eco",
        searchTemplate = "https://www.ecosia.org/search?q={searchTerms}",
        suggestTemplate = "https://ac.ecosia.org/autocomplete?q={searchTerms}&type=list",
    )
    val Google = SearchEngine(
        id = "google", name = "Google", keyword = "g",
        searchTemplate = "https://www.google.com/search?q={searchTerms}",
        suggestTemplate = "https://www.google.com/complete/search?client=firefox&q={searchTerms}",
    )
    val Bing = SearchEngine(
        id = "bing", name = "Bing", keyword = "bing",
        searchTemplate = "https://www.bing.com/search?q={searchTerms}",
        suggestTemplate = "https://www.bing.com/osjson.aspx?query={searchTerms}",
    )
    val Wikipedia = SearchEngine(
        id = "wikipedia", name = "Wikipedia", keyword = "w",
        searchTemplate = "https://en.wikipedia.org/wiki/Special:Search?search={searchTerms}",
        suggestTemplate = "https://en.wikipedia.org/w/api.php?action=opensearch&search={searchTerms}",
    )
    val YouTube = SearchEngine(
        id = "youtube", name = "YouTube", keyword = "yt",
        searchTemplate = "https://www.youtube.com/results?search_query={searchTerms}",
    )
    val GitHub = SearchEngine(
        id = "github", name = "GitHub", keyword = "gh",
        searchTemplate = "https://github.com/search?q={searchTerms}",
    )

    /** Engines offered as the default, privacy-respecting ones first. */
    val defaults: List<SearchEngine> = listOf(DuckDuckGo, Startpage, Brave, Kagi, Mojeek, Qwant, Ecosia, Google, Bing)

    /** Every engine, including the keyword-only ones. */
    val all: List<SearchEngine> = defaults + listOf(Wikipedia, YouTube, GitHub)

    fun byId(id: String?): SearchEngine = all.firstOrNull { it.id == id } ?: DuckDuckGo

    /**
     * `@w kotlin` → (Wikipedia, "kotlin"). Returns null when the input has no recognised `@keyword`.
     */
    fun parseKeyword(input: String, engines: List<SearchEngine> = all): Pair<SearchEngine, String>? {
        val trimmed = input.trimStart()
        if (!trimmed.startsWith("@")) return null
        val keyword = trimmed.drop(1).substringBefore(' ').lowercase()
        val engine = engines.firstOrNull { it.keyword == keyword || it.id == keyword } ?: return null
        val query = trimmed.substringAfter(' ', "").trim()
        return engine to query
    }
}
