package app.pane.core.adblock

/** Host-name helpers shared by the filter engine and the code that feeds it. */
object Hosts {
    private val secondLevel = setOf("co", "com", "org", "net", "gov", "ac", "edu", "or", "ne", "go", "gob", "mil")

    /** The registrable domain, judged by the last two labels (three under a country second level). */
    fun registrable(host: String): String {
        val parts = host.split('.')
        if (parts.size <= 2) return host
        val take = if (parts.last().length == 2 && parts[parts.size - 2] in secondLevel) 3 else 2
        return parts.takeLast(take).joinToString(".")
    }

    /** Same registrable domain: the request is first-party to the page. */
    fun sameSite(a: String, b: String): Boolean = registrable(a.lowercase()) == registrable(b.lowercase())

    /** Whether [candidate] is [domain] or one of its subdomains. */
    fun isSameOrSub(candidate: String, domain: String): Boolean =
        candidate == domain || (candidate.length > domain.length && candidate.endsWith(domain) &&
            candidate[candidate.length - domain.length - 1] == '.')

    internal fun hasSecondLevel(label: String) = label in secondLevel
}
