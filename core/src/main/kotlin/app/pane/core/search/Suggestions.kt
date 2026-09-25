package app.pane.core.search

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object Suggestions {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Parses a suggestion response. Malformed input yields an empty list, never an exception. */
    fun parse(body: String, format: SuggestFormat, limit: Int = 6): List<String> {
        val root = try {
            json.parseToJsonElement(body)
        } catch (_: Exception) {
            return emptyList()
        }
        val raw: List<String> = when {
            // OpenSearch; also DuckDuckGo with `type=list`.
            root is JsonArray && root.size >= 2 && root[1] is JsonArray ->
                (root[1] as JsonArray).mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            format == SuggestFormat.DuckDuckGo && root is JsonArray ->
                root.mapNotNull { ((it as? JsonObject)?.get("phrase") as? JsonPrimitive)?.contentOrNull }
            else -> emptyList()
        }
        return raw.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(limit)
    }
}
