package app.pane.core.extensions

/**
 * Turns WebExtension permission ids and host match patterns into the plain-language sentences
 * shown before an install and on an extension's page.
 *
 * Wording follows Firefox's own prompts where Firefox has one, so a person who has read one
 * browser's install dialog can read the other's. Permissions Firefox stays quiet about (storage,
 * alarms, menus…) still get a sentence here, placed after the ones that matter, because Pane shows
 * the full list on the extension page and an unexplained id is worse than a mundane sentence.
 */
object ExtensionPermissions {

    /** Most consequential first; several ids may share a sentence and are then shown once. */
    private val messages: Map<String, String> = linkedMapOf(
        "nativeMessaging" to "Exchange messages with apps other than Pane",
        "userScripts" to "Allow unverified third-party scripts to access your data",
        "proxy" to "Control browser proxy settings",
        "privacy" to "Read and modify privacy settings",
        "browserSettings" to "Read and modify browser settings",
        "history" to "Access browsing history",
        "topSites" to "Access browsing history",
        "declarativeNetRequestFeedback" to "Read your browsing history",
        "tabs" to "Access browser tabs",
        "webNavigation" to "Access browser activity during navigation",
        "find" to "Read the text of all open tabs",
        "devtools" to "Extend developer tools to access your data in open tabs",
        "bookmarks" to "Read and modify bookmarks",
        "downloads" to "Download files and read and modify the browser’s download history",
        "downloads.open" to "Open files downloaded to your device",
        "browsingData" to "Clear recent browsing history, cookies, and related data",
        "cookies" to "Read and change cookies on sites it can access",
        "webRequestBlocking" to "Block or change network requests",
        "webRequestFilterResponse" to "Change the content of pages as they load",
        "webRequest" to "Monitor network requests on sites it can access",
        "declarativeNetRequest" to "Block content on any page",
        "declarativeNetRequestWithHostAccess" to "Block content on sites it can access",
        "scripting" to "Run scripts on sites it can access",
        "clipboardRead" to "Get data from the clipboard",
        "clipboardWrite" to "Input data to the clipboard",
        "geolocation" to "Access your location",
        "notifications" to "Display notifications to you",
        "management" to "Monitor extension usage and manage themes",
        "sessions" to "Access recently closed tabs",
        "tabHide" to "Hide and show browser tabs",
        "pkcs11" to "Provide cryptographic authentication services",
        "identity" to "Sign you in to online services",
        "search" to "Use your search engines",
        "theme" to "Change how the browser looks",
        "activeTab" to "Access the current tab when you use the extension",
        "contextMenus" to "Add items to menus",
        "menus" to "Add items to menus",
        "idle" to "Know when you’re not using your device",
        "dns" to "Look up website addresses",
        "unlimitedStorage" to "Store an unlimited amount of data on your device",
        "storage" to "Store data on your device",
        "alarms" to "Schedule tasks to run later",
    )

    /** Engine plumbing that says nothing about what an extension can do to the user. */
    private val internal = setOf(
        "nativeMessagingFromContent",
        "geckoViewAddons",
        "mozillaAddons",
        "telemetry",
        "normandyAddonStudy",
        "networkStatus",
        "captivePortal",
        "menus.overrideContext",
        "webRequestAuthProvider",
        "webRequestFilterResponse.serviceWorkerScript",
    )

    private val dataCollectionNames: Map<String, String> = linkedMapOf(
        "personallyIdentifyingInfo" to "personally identifying information",
        "healthInfo" to "health information",
        "financialAndPaymentInfo" to "financial and payment information",
        "authenticationInfo" to "authentication information",
        "personalCommunications" to "personal communications",
        "locationInfo" to "location",
        "browsingActivity" to "browsing activity",
        "websiteActivity" to "website activity",
        "websiteContent" to "website content",
        "searchTerms" to "search terms",
        "bookmarksInfo" to "bookmarks",
        "technicalAndInteraction" to "technical and interaction data",
    )

    /** Hosts individually named before the rest are summarised as "N other sites". */
    private const val MAX_NAMED_HOSTS = 3

    /**
     * Sentences for [permissions] and [origins]: host access first, then API permissions from most
     * to least sensitive. Host patterns that arrive in [permissions] (Manifest V2 mixes them) are
     * treated as origins. Unknown ids are left out; see [unlisted].
     */
    fun describe(permissions: Collection<String>, origins: Collection<String> = emptyList()): List<String> {
        val (hostLike, api) = permissions.partition(::isHostPattern)
        val out = LinkedHashSet<String>()
        out += hostAccess(origins + hostLike)
        val granted = api.toSet()
        messages.forEach { (id, text) -> if (id in granted) out += text }
        return out.toList()
    }

    /** Permission ids that have no sentence and aren't engine internals, for a "Also uses" note. */
    fun unlisted(permissions: Collection<String>): List<String> =
        permissions.filter { !isHostPattern(it) && it !in messages && it !in internal && !it.startsWith("internal:") }.distinct()

    /** True for `<all_urls>` and anything shaped like a match pattern (`scheme://host/path`). */
    fun isHostPattern(value: String): Boolean = value == ALL_URLS || value.contains("://")

    /** Summarises host match patterns the way Firefox does: all sites, domains, then single sites. */
    fun hostAccess(origins: Collection<String>): List<String> {
        val domains = sortedSetOf<String>()
        val sites = sortedSetOf<String>()
        for (origin in origins) {
            when (val host = hostOf(origin) ?: continue) {
                ALL_HOSTS -> return listOf("Access your data for all websites")
                else -> if (host.startsWith("*.")) domains += host.removePrefix("*.") else sites += host
            }
        }
        // A wildcard domain already covers its own subdomains.
        sites.removeAll { site -> domains.any { site == it || site.endsWith(".$it") } }
        val out = mutableListOf<String>()
        domains.take(MAX_NAMED_HOSTS).forEach { out += "Access your data for sites in the $it domain" }
        if (domains.size > MAX_NAMED_HOSTS) {
            val more = domains.size - MAX_NAMED_HOSTS
            out += "Access your data in $more other ${if (more == 1) "domain" else "domains"}"
        }
        sites.take(MAX_NAMED_HOSTS).forEach { out += "Access your data for $it" }
        if (sites.size > MAX_NAMED_HOSTS) {
            val more = sites.size - MAX_NAMED_HOSTS
            out += "Access your data on $more other ${if (more == 1) "site" else "sites"}"
        }
        return out
    }

    /**
     * The developer's declared data collection, as one sentence, or null when nothing was declared.
     * `none` is an explicit promise and is reported as such.
     */
    fun dataCollection(ids: Collection<String>): String? {
        if (ids.isEmpty()) return null
        if ("none" in ids) return "The developer says this extension doesn’t collect data."
        val names = dataCollectionNames.filterKeys { it in ids }.values.toList()
        if (names.isEmpty()) return null
        return "The developer says this extension collects: ${joinNatural(names)}."
    }

    /** "a", "a and b", "a, b and c". */
    fun joinNatural(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
    }

    /**
     * The host part of a match pattern: [ALL_HOSTS] for patterns that match every website,
     * `*.example.com` for wildcard domains, the bare host otherwise. Null for patterns that don't
     * reach websites (`file:`, extension pages).
     */
    internal fun hostOf(pattern: String): String? {
        if (pattern == ALL_URLS) return ALL_HOSTS
        val scheme = pattern.substringBefore("://", "").lowercase()
        if (scheme !in webSchemes) return null
        val host = pattern.substringAfter("://").substringBefore('/').substringAfterLast('@').substringBefore(':').lowercase()
        return when {
            host.isEmpty() -> null
            host == "*" -> ALL_HOSTS
            else -> host
        }
    }

    private val webSchemes = setOf("*", "http", "https", "ws", "wss", "ftp")
    private const val ALL_URLS = "<all_urls>"
    private const val ALL_HOSTS = "*"
}
