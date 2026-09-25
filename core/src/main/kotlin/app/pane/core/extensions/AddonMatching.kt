package app.pane.core.extensions

import java.net.URLEncoder

/** The bits of an installed extension needed to recognise its store listing. */
data class InstalledAddonRef(val id: String, val name: String, val listingUrl: String? = null)

/**
 * Decides whether an add-on store listing is already installed, so the store can say "Installed"
 * instead of offering "Get" again.
 *
 * Search results carry the add-on's id and match exactly. The curated [RecommendedExtensions] only
 * know slugs, so they are matched, in order of reliability, through the slug → id pairs Pane records
 * when it installs something, a table of well-known ids, the AMO listing URL, and finally the name.
 */
object AddonMatching {

    /** Ids of the curated recommendations, keyed by AMO slug. */
    val knownIds: Map<String, String> = mapOf(
        "ublock-origin" to "uBlock0@raymondhill.net",
        "privacy-badger17" to "jid1-MnnxcxisBPnSXQ@jetpack",
        "clearurls" to "{74145f27-f039-47ce-a470-a662b129930a}",
        "istilldontcareaboutcookies" to "idcac-pub@guus.ninja",
        "bitwarden-password-manager" to "{446900e4-71c2-419f-a6a7-df9c091e268b}",
        "proton-pass" to "78272b6fa58f4a1abaac99321d503a20@proton.me",
        "darkreader" to "addon@darkreader.org",
        "styl-us" to "{7a7a4a92-a2a0-41d1-9fd7-1e92480d612d}",
        "violentmonkey" to "{aecec67f-0d10-4fa7-b7c7-609a2db280cf}",
        "sponsorblock" to "sponsorBlocker@ajay.app",
        "return-youtube-dislikes" to "{762f9885-5a13-4abd-9c77-433dcd38b8fd}",
        "localcdn-fork-of-decentraleyes" to "{b86e4813-687a-43e6-ab65-0bde4ab75758}",
        "single-file" to "{531906d3-e22f-4a6c-a102-8057b88a1a63}",
    )

    /**
     * Whether the listing ([slug], optional exact [guid], display [name]) is among [installed].
     * [recorded] holds slug → id pairs remembered from installs made inside Pane.
     */
    fun isInstalled(
        slug: String,
        name: String,
        installed: Collection<InstalledAddonRef>,
        guid: String? = null,
        recorded: Map<String, String> = emptyMap(),
    ): Boolean {
        if (installed.isEmpty()) return false
        val ids = installed.mapTo(HashSet()) { it.id }
        if (guid != null) return guid in ids
        recorded[slug]?.let { if (it in ids) return true }
        val known = knownIds[slug]
        if (known != null && known in ids) return true
        if (installed.any { slugFromListingUrl(it.listingUrl) == slug }) return true
        val wanted = normalize(name)
        if (wanted.isEmpty()) return false
        return installed.any { ref ->
            val have = normalize(ref.name)
            // Store names often carry a tagline ("Bitwarden Password Manager"); only trust a prefix
            // when there's no exact id to go by.
            have == wanted || (known == null && wanted.length >= 5 && have.startsWith(wanted))
        }
    }

    /** `https://addons.mozilla.org/en-US/android/addon/ublock-origin/` → `ublock-origin`. */
    fun slugFromListingUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val path = url.substringAfter("://", url).substringAfter('/', "").substringBefore('?').substringBefore('#')
        val segments = path.split('/').filter { it.isNotEmpty() }
        val index = segments.indexOf("addon")
        return segments.getOrNull(index + 1)?.takeIf { index >= 0 && it.isNotBlank() }
    }

    /** One AMO search request that returns the listings for several ids at once. */
    fun listingsByIdUrl(ids: Collection<String>, lang: String = "en-US"): String {
        val joined = ids.joinToString(",")
        return "https://addons.mozilla.org/api/v5/addons/search/?guid=${enc(joined)}&page_size=${ids.size.coerceIn(1, 50)}&lang=${enc(lang)}"
    }

    internal fun normalize(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
