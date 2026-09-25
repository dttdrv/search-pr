package app.pane.core.suggest

import app.pane.core.url.UrlDisplay
import app.pane.core.url.UrlInput
import kotlin.math.exp
import kotlin.math.ln

/** A page the user has been to or saved. */
data class PlaceCandidate(
    val url: String,
    val title: String,
    val visitCount: Int = 0,
    val lastVisited: Long = 0L,
    val bookmarked: Boolean = false,
)

sealed interface Suggestion {
    val key: String

    data class Place(val url: String, val title: String, val bookmarked: Boolean) : Suggestion {
        override val key get() = "place:$url"
    }

    data class OpenTab(val tabId: String, val url: String, val title: String) : Suggestion {
        override val key get() = "tab:$tabId"
    }

    data class Search(val query: String, val fromEngine: Boolean) : Suggestion {
        override val key get() = "search:$query"
    }
}

object Frecency {
    private const val HALF_LIFE_DAYS = 14.0

    /** Visit count weighted by an exponential recency decay; bookmarks get a strong boost. */
    fun score(visitCount: Int, lastVisited: Long, bookmarked: Boolean, now: Long): Double {
        val ageDays = ((now - lastVisited).coerceAtLeast(0L)) / 86_400_000.0
        val recency = exp(-ln(2.0) * ageDays / HALF_LIFE_DAYS)
        val visits = ln(1.0 + visitCount.coerceAtLeast(0))
        return (visits + 0.5) * (0.25 + recency) * (if (bookmarked) 2.0 else 1.0)
    }
}

object Autocomplete {
    /**
     * Inline-completes a typed prefix to a host the user already knows, like Safari's
     * "Top Hit". `gi` → `github.com`. Only hosts are completed, never paths, so what the user sees
     * is exactly what they get.
     */
    fun complete(typed: String, candidates: List<PlaceCandidate>, now: Long): String? {
        val t = typed.trim().lowercase()
        if (t.isEmpty() || t.any { it.isWhitespace() || it == '/' }) return null
        val stripped = t.removePrefix("https://").removePrefix("http://")
        return candidates
            .mapNotNull { c -> UrlInput.hostOf(c.url)?.let { host -> host to c } }
            .flatMap { (host, c) ->
                listOfNotNull(host, host.removePrefix("www.").takeIf { it != host }).map { it to c }
            }
            .filter { (host, _) -> host.startsWith(stripped) && host != stripped }
            .maxByOrNull { (host, c) ->
                Frecency.score(c.visitCount, c.lastVisited, c.bookmarked, now) - host.length * 0.001
            }
            ?.first
    }
}

object SuggestionRanker {
    /**
     * Merges open tabs, history/bookmarks and engine suggestions into the list shown under the
     * address bar. Every token of the query must match the title or URL.
     */
    fun rank(
        query: String,
        places: List<PlaceCandidate>,
        openTabs: List<Suggestion.OpenTab>,
        engineSuggestions: List<String>,
        now: Long,
        maxPlaces: Int = 5,
        maxTotal: Int = 10,
    ): List<Suggestion> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val tokens = q.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

        fun matches(url: String, title: String): Boolean {
            val hay = (title + " " + url.removePrefix("https://").removePrefix("http://")).lowercase()
            return tokens.all { hay.contains(it) }
        }

        val tabs = openTabs.filter { matches(it.url, it.title) }.take(2)
        val tabUrls = tabs.map { it.url }.toSet()

        val rankedPlaces = places
            .asSequence()
            .filter { it.url !in tabUrls && matches(it.url, it.title) }
            .distinctBy { it.url }
            .sortedByDescending { c ->
                val host = UrlDisplay.toolbarText(c.url).lowercase()
                val hostBoost = if (host.startsWith(tokens.first())) 3.0 else 0.0
                Frecency.score(c.visitCount, c.lastVisited, c.bookmarked, now) + hostBoost
            }
            .take(maxPlaces)
            .map { Suggestion.Place(it.url, it.title, it.bookmarked) }
            .toList()

        val searches = buildList {
            add(Suggestion.Search(q, fromEngine = false))
            engineSuggestions.filter { !it.equals(q, ignoreCase = true) }.forEach { add(Suggestion.Search(it, fromEngine = true)) }
        }

        return (tabs + rankedPlaces + searches).distinctBy { it.key }.take(maxTotal)
    }
}
