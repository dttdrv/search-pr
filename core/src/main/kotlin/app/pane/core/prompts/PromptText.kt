package app.pane.core.prompts

import app.pane.core.url.UrlDisplay

/** Small pieces of prompt wording that are worth getting exactly right. */
object PromptText {
    private val origin = Regex("[a-zA-Z][a-zA-Z0-9+.-]*://[^\\s/?#\"'<>]+")

    /**
     * Who is showing a script dialog. Gecko titles them "The page at https://example.com says:"
     * (localized); the origin inside is the frame that actually called `alert()`, which can be an
     * embedded third party rather than the tab's site, so it wins over [pageUrl].
     */
    fun dialogSource(engineTitle: String?, pageUrl: String?): String? {
        engineTitle?.let { title ->
            origin.find(title)?.value?.trimEnd(':', '.', ',', ';')?.let { url ->
                UrlDisplay.toolbarText(url).takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return pageUrl?.let(UrlDisplay::toolbarText)?.takeIf { it.isNotBlank() }
    }

    /** Find-in-page counter: "3 of 12". */
    fun findCounter(found: Boolean, current: Int, total: Int): String = when {
        !found || total == 0 -> "No results"
        // Gecko stops counting past its match limit and reports a negative total.
        total < 0 -> if (current > 0) "$current of many" else "Many results"
        current <= 0 -> "$total results"
        else -> "$current of $total"
    }

    /** Keeps very long URLs readable in alerts without hiding where they point. */
    fun shorten(text: String, max: Int = 120): String =
        if (text.length <= max) text else text.take(max - 1).trimEnd() + "…"
}
