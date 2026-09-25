package app.pane.core.library

/**
 * Normalises the URIs Gecko reports for stored permissions (`https://example.com/`,
 * `https://example.com:8443/`) into origins, so every permission of a site lands in one group.
 */
object SiteOrigins {

    /** `https://Example.com:443/path` → `https://example.com`; null for URIs without a host. */
    fun originOf(uri: String): String? {
        val schemeEnd = uri.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = uri.substring(0, schemeEnd).lowercase()
        val rest = uri.substring(schemeEnd + 3)
        val authority = rest.takeWhile { it != '/' && it != '?' && it != '#' }.substringAfterLast('@')
        if (authority.isEmpty()) return null
        val hostPort = authority.lowercase()
        val defaultPort = when (scheme) {
            "https" -> ":443"
            "http" -> ":80"
            else -> null
        }
        val trimmed = if (defaultPort != null && hostPort.endsWith(defaultPort) && !hostPort.endsWith("]")) hostPort.removeSuffix(defaultPort) else hostPort
        return "$scheme://$trimmed"
    }

    /** The host part of an origin for display: `https://example.com:8443` → `example.com:8443`. */
    fun displayName(origin: String): String = origin.substringAfter("://")

    /** Host without port, as the storage APIs expect it. */
    fun hostOf(origin: String): String {
        val authority = displayName(origin)
        if (authority.startsWith("[")) return authority.substringBefore(']') + "]"
        return authority.substringBefore(':')
    }
}
