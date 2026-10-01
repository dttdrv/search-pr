package app.pane.core.adblock

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.Base64

@Serializable
data class ScriptletResource(
    val name: String,
    val aliases: List<String>,
    val fn: String,
    val source: String,
    val dependencies: List<String>,
    val trusted: Boolean,
    val priority: Int = 0,
)

@Serializable
data class RedirectResource(
    val name: String,
    val aliases: List<String>,
    val mime: String,
    val data: String,
    val trusted: Boolean,
) {
    val bytes: ByteArray by lazy { Base64.getDecoder().decode(data) }
}

object FilterResources {
    // a fixed name: a minified build moves classes into the default package, which has no package name to read
    const val origin: String = "https://app.pane.core.adblock.invalid"
    @Serializable
    private class Catalogue(val revision: String, val notice: String, val scriptlets: List<ScriptletResource>, val redirects: List<RedirectResource>)

    private val catalogue by lazy {
        FilterResources::class.java.getResourceAsStream("/adblock/resources.json")!!.bufferedReader().use {
            Json.decodeFromString<Catalogue>(it.readText())
        }
    }
    private val scriptlets by lazy {
        buildMap { for (r in catalogue.scriptlets) { put(r.name, r); for (a in r.aliases) put(a, r) } }
    }
    private val redirects by lazy {
        buildMap { for (r in catalogue.redirects) { put(r.name, r); for (a in r.aliases) put(a, r) } }
    }

    val revision: String get() = catalogue.revision

    fun scriptlet(token: String): ScriptletResource? = if (token.endsWith(".fn")) null else scriptlets[if (token.endsWith(".js")) token else "$token.js"]

    fun redirect(token: String): RedirectResource? = redirects[token]

    internal fun definitions(tokens: Iterable<String>): String {
        val seen = LinkedHashSet<String>()
        val ordered = ArrayList<ScriptletResource>()
        fun visit(token: String) {
            if (!seen.add(token)) return
            val r = checkNotNull(scriptlets[token]) { "Missing scriptlet dependency: $token" }
            for (d in r.dependencies) visit(d)
            ordered.add(r)
        }
        for (token in tokens) visit(token)
        return ordered.filter { it.fn.isNotEmpty() }.joinToString("\n") { it.source }
    }
}
