package app.pane.core.adblock

import java.net.IDN

/** Host-name helpers shared by the filter engine and the code that feeds it. */
object Hosts {
    internal val suffixRules: Set<String> by lazy {
        Hosts::class.java.getResourceAsStream("/adblock/public-suffixes.txt")!!.bufferedReader().useLines {
            it.filter { line -> line.isNotEmpty() && !line.startsWith("//") }.toSet()
        }
    }

    fun isValid(host: String): Boolean = try {
        IDN.toASCII(host.trimEnd('.'))
        host.isNotEmpty()
    } catch (_: IllegalArgumentException) { false }

    internal fun normalize(host: String): String = IDN.toASCII(host.trimEnd('.').lowercase())

    internal fun publicSuffix(host: String): String {
        val h = normalize(host)
        val suffix = h.substringAfterLast('.')
        var candidate = h
        while (true) {
            if ("!$candidate" in suffixRules) return candidate.substringAfter('.')
            if (candidate in suffixRules || "*.${candidate.substringAfter('.')}" in suffixRules && candidate.contains('.')) return candidate
            if (!candidate.contains('.')) return suffix
            candidate = candidate.substringAfter('.')
        }
    }

    /** the registrable domain from the Public Suffix List, including its private section. */
    fun registrable(host: String): String {
        val h = normalize(host)
        if (h.contains(':') || h.split('.').let { it.size == 4 && it.all { p -> p.toIntOrNull() in 0..255 } }) return h
        val suffix = publicSuffix(h)
        if (h == suffix) return h
        val label = h.removeSuffix(".$suffix").substringAfterLast('.')
        return "$label.$suffix"
    }

    /** Same registrable domain: the request is first-party to the page. */
    fun sameSite(a: String, b: String): Boolean = registrable(a.lowercase()) == registrable(b.lowercase())

    /** Whether [candidate] is [domain] or one of its subdomains. */
    fun isSameOrSub(candidate: String, domain: String): Boolean =
        candidate == domain || (candidate.length > domain.length && candidate.endsWith(domain) &&
            candidate[candidate.length - domain.length - 1] == '.')

}
