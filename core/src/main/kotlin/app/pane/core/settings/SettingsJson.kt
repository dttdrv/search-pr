package app.pane.core.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads and writes the one JSON blob settings are stored in. Unknown keys are ignored and missing
 * ones take their defaults; a stored choice that no longer exists falls back instead of failing the
 * whole load. The HTTPS choice is translated rather than dropped, so nobody's setting changes:
 *  - the earlier Pane's `httpsUpgrade` switch becomes [HttpsMode.First] (on) or [HttpsMode.Off],
 *  - the first Pane's `httpsMode` names (`HttpsOnly`, `HttpsFirst`) become [HttpsMode.Only] and [HttpsMode.First].
 */
object SettingsJson {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    fun encode(settings: BrowserSettings): String = json.encodeToString(BrowserSettings.serializer(), settings)

    /** Null when [raw] isn't a settings object at all. */
    fun decode(raw: String): BrowserSettings? = runCatching {
        json.decodeFromJsonElement(BrowserSettings.serializer(), migrate(json.parseToJsonElement(raw).jsonObject))
    }.getOrNull()

    internal fun migrate(stored: JsonObject): JsonObject = migrateCookies(migrateHttps(stored))

    /** Blocking third-party cookies broke everyday sites, so builds that defaulted to it are reset to allowed. */
    private fun migrateCookies(stored: JsonObject): JsonObject {
        val version = (stored["defaultsVersion"] as? JsonPrimitive)?.intOrNull ?: return stored
        if (version >= 5) return stored
        return JsonObject(stored + ("blockThirdPartyCookies" to JsonPrimitive(false)))
    }

    private fun migrateHttps(stored: JsonObject): JsonObject {
        val named = (stored["httpsMode"] as? JsonPrimitive)?.contentOrNull?.let(::httpsModeNamed)
        val legacySwitch = (stored["httpsUpgrade"] as? JsonPrimitive)?.booleanOrNull
        val mode = named ?: legacySwitch?.let { if (it) HttpsMode.First else HttpsMode.Off } ?: return stored
        return JsonObject(stored.filterKeys { it != "httpsUpgrade" } + ("httpsMode" to JsonPrimitive(mode.name)))
    }

    private fun httpsModeNamed(name: String): HttpsMode? = when (name) {
        "Off" -> HttpsMode.Off
        "First", "HttpsFirst" -> HttpsMode.First
        "Only", "HttpsOnly" -> HttpsMode.Only
        else -> null
    }
}
