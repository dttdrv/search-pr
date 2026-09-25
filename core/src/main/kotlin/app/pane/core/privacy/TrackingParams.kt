package app.pane.core.privacy

/**
 * Removes click-tracking parameters from URLs before they are copied or shared.
 *
 * Gecko already strips a remote-managed list during navigation; this covers the copy/share path,
 * which is where tracking IDs usually leak to other people.
 */
object TrackingParams {
    private val exact = setOf(
        "fbclid", "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "msclkid", "yclid", "twclid", "ttclid",
        "igshid", "igsh", "mc_cid", "mc_eid", "mkt_tok", "_hsenc", "_hsmi", "__hssc", "__hstc", "__hsfp",
        "hsctatracking", "oly_anon_id", "oly_enc_id", "rb_clickid", "s_cid", "vero_conv", "vero_id",
        "wickedid", "_openstat", "srsltid", "ref_src", "ref_url", "spm", "scm", "epik", "li_fat_id",
        "trk", "trkcampaign", "sc_campaign", "sc_channel", "sc_content", "sc_medium", "sc_outcome",
        "sc_geo", "sc_country", "_ga", "_gl", "cvid", "ocid", "zanpid", "irclickid", "cmpid",
    )
    private val prefixes = listOf("utm_", "pk_", "mtm_", "hmb_", "ga_", "__s", "at_")

    /** Parameters that only mean tracking on specific sites. */
    private val perHost: Map<String, Set<String>> = mapOf(
        "youtube.com" to setOf("si", "pp", "feature"),
        "youtu.be" to setOf("si", "feature"),
        "open.spotify.com" to setOf("si", "context"),
        "twitter.com" to setOf("s", "t"),
        "x.com" to setOf("s", "t"),
        "instagram.com" to setOf("igsh", "img_index"),
        "amazon.com" to setOf("ref", "ref_", "pd_rd_r", "pd_rd_w", "pd_rd_wg", "pf_rd_p", "pf_rd_r", "content-id", "psc"),
        "reddit.com" to setOf("share_id", "utm_name", "rdt"),
        "linkedin.com" to setOf("trackingid", "refid", "lipi", "midtoken", "midsig", "trk"),
        "tiktok.com" to setOf("_r", "_t", "is_from_webapp", "sender_device", "sender_web_id"),
    )

    fun strip(url: String): String {
        val hashIndex = url.indexOf('#')
        val beforeHash = if (hashIndex >= 0) url.substring(0, hashIndex) else url
        val fragment = if (hashIndex >= 0) url.substring(hashIndex) else ""
        val queryIndex = beforeHash.indexOf('?')
        if (queryIndex < 0) return url
        val base = beforeHash.substring(0, queryIndex)
        val query = beforeHash.substring(queryIndex + 1)
        val host = hostOf(base)
        val hostRules = perHost.entries.filter { (domain, _) -> host == domain || host.endsWith(".$domain") }
            .flatMap { it.value }.toSet()
        val kept = query.split('&').filter { part ->
            if (part.isEmpty()) return@filter false
            val name = part.substringBefore('=').lowercase()
            !(name in exact || prefixes.any { name.startsWith(it) } || name in hostRules)
        }
        return base + (if (kept.isEmpty()) "" else "?" + kept.joinToString("&")) + fragment
    }

    private fun hostOf(base: String): String {
        val start = base.indexOf("://").takeIf { it >= 0 }?.plus(3) ?: return ""
        return base.substring(start).substringBefore('/').substringAfterLast('@').substringBefore(':').lowercase()
    }
}
