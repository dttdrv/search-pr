package app.pane.core.extensions

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.net.URLEncoder

/** An add-on listed on addons.mozilla.org. */
data class AmoAddon(
    val guid: String,
    val slug: String,
    val name: String,
    val summary: String,
    val iconUrl: String?,
    val authors: List<String>,
    val users: Long,
    val rating: Double,
    val ratingCount: Long,
    val xpiUrl: String?,
    val listingUrl: String?,
    val recommended: Boolean,
)

data class AmoPage(val addons: List<AmoAddon>, val next: String?, val count: Long)

/** Talks to the public AMO API. Only builds URLs and parses JSON; networking lives in the app. */
object Amo {
    private const val API = "https://addons.mozilla.org/api/v5"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun searchUrl(query: String?, page: Int = 1, pageSize: Int = 25, lang: String = "en-US"): String {
        val q = query?.trim().orEmpty()
        val base = "$API/addons/search/?app=android&type=extension&page_size=$pageSize&page=$page&lang=${enc(lang)}"
        return if (q.isEmpty()) "$base&sort=recommended,users&promoted=recommended" else "$base&sort=relevance,users&q=${enc(q)}"
    }

    fun detailUrl(slugOrGuid: String, lang: String = "en-US") = "$API/addons/addon/${enc(slugOrGuid)}/?lang=${enc(lang)}"

    /** Always-latest signed download for an add-on. */
    fun latestXpiUrl(slug: String) = "https://addons.mozilla.org/firefox/downloads/latest/${enc(slug)}/latest.xpi"

    fun parsePage(body: String, lang: String = "en-US"): AmoPage {
        val root = json.parseToJsonElement(body).jsonObject
        val results = (root["results"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { o -> parseAddon(o, lang) } }
        return AmoPage(
            addons = results,
            next = (root["next"] as? JsonPrimitive)?.contentOrNull,
            count = (root["count"] as? JsonPrimitive)?.longOrNull ?: results.size.toLong(),
        )
    }

    fun parseAddon(body: String, lang: String = "en-US"): AmoAddon? =
        (json.parseToJsonElement(body) as? JsonObject)?.let { parseAddon(it, lang) }

    private fun parseAddon(o: JsonObject, lang: String): AmoAddon? {
        val guid = o.str("guid") ?: return null
        val slug = o.str("slug") ?: guid
        val file = (o["current_version"] as? JsonObject)?.get("file") as? JsonObject
        val ratings = o["ratings"] as? JsonObject
        val promoted = o["promoted"]
        return AmoAddon(
            guid = guid,
            slug = slug,
            name = translated(o["name"], lang) ?: slug,
            summary = translated(o["summary"], lang).orEmpty(),
            iconUrl = o.str("icon_url"),
            authors = (o["authors"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.str("name") },
            users = (o["average_daily_users"] as? JsonPrimitive)?.longOrNull ?: 0,
            rating = (ratings?.get("average") as? JsonPrimitive)?.doubleOrNull ?: 0.0,
            ratingCount = (ratings?.get("count") as? JsonPrimitive)?.longOrNull ?: 0,
            xpiUrl = file?.str("url"),
            listingUrl = o.str("url"),
            recommended = isRecommended(promoted),
        )
    }

    private fun isRecommended(promoted: JsonElement?): Boolean = when (promoted) {
        null, JsonNull -> false
        is JsonObject -> promoted.str("category") in setOf("recommended", "line")
        is JsonArray -> promoted.any { (it as? JsonObject)?.str("category") in setOf("recommended", "line") }
        else -> false
    }

    /** AMO returns translated fields either as plain strings or as `{locale: text}` objects. */
    private fun translated(e: JsonElement?, lang: String): String? = when (e) {
        is JsonPrimitive -> e.contentOrNull
        is JsonObject -> (e[lang] ?: e[lang.substringBefore('-')] ?: e["en-US"] ?: e.values.firstOrNull())
            ?.let { (it as? JsonPrimitive)?.contentOrNull }
        else -> null
    }

    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}

/** A hand-picked shortlist shown before the user searches, so the store never feels empty offline. */
data class RecommendedExtension(val slug: String, val name: String, val summary: String, val category: String)

object RecommendedExtensions {
    val all = listOf(
        RecommendedExtension("ublock-origin", "uBlock Origin", "Efficient blocker for ads, trackers and malware sites.", "Privacy"),
        RecommendedExtension("privacy-badger17", "Privacy Badger", "Learns to block invisible trackers.", "Privacy"),
        RecommendedExtension("clearurls", "ClearURLs", "Removes tracking elements from URLs.", "Privacy"),
        RecommendedExtension("istilldontcareaboutcookies", "I still don't care about cookies", "Gets rid of cookie warnings.", "Privacy"),
        RecommendedExtension("bitwarden-password-manager", "Bitwarden", "Open-source password manager.", "Security"),
        RecommendedExtension("proton-pass", "Proton Pass", "End-to-end encrypted password manager.", "Security"),
        RecommendedExtension("darkreader", "Dark Reader", "Dark mode for every website.", "Appearance"),
        RecommendedExtension("styl-us", "Stylus", "Restyle the web with user styles.", "Appearance"),
        RecommendedExtension("violentmonkey", "Violentmonkey", "Run user scripts.", "Power tools"),
        RecommendedExtension("sponsorblock", "SponsorBlock", "Skip sponsored segments in YouTube videos.", "Media"),
        RecommendedExtension("return-youtube-dislikes", "Return YouTube Dislike", "Shows dislike counts again.", "Media"),
        RecommendedExtension("video-background-play-fix", "Video Background Play Fix", "Keeps videos playing in the background.", "Media"),
        RecommendedExtension("localcdn-fork-of-decentraleyes", "LocalCDN", "Serves common libraries locally to stop CDN tracking.", "Privacy"),
        RecommendedExtension("single-file", "SingleFile", "Save a complete page into a single HTML file.", "Power tools"),
    )
}
