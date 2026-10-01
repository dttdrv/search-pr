package app.pane.browser.engine

import android.content.Context
import app.pane.core.library.SiteOrigins
import app.pane.core.prompts.SitePermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What the user told each site, kept on the device: allow or block per origin and permission.
 * No entry means "ask".
 *
 * Tiny on purpose. The system WebView has no permission store of its own, so this is the only one.
 * It is read in memory and written through to SharedPreferences, one line per origin
 * (`Location=1,Camera=0`). Private tabs never read or write it; that is enforced by the caller
 * ([app.pane.browser.engine.prompts.PromptBridge]), not here.
 */
class SitePermissionStore(context: Context) {
    /** Application context, for the few things next to a permission that need one (file staging). */
    internal val appContext: Context = context.applicationContext

    private val prefs = appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val lock = Any()
    private val entries: MutableMap<String, Map<SitePermission, Boolean>> = HashMap()

    private val _changes = MutableStateFlow(0)

    /** Bumped on every write, so a screen listing sites knows when to look again. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    init {
        for ((key, value) in prefs.all) {
            val decoded = (value as? String)?.let(::decode).orEmpty()
            if (decoded.isNotEmpty()) entries[key] = decoded
        }
    }

    /** True to allow, false to block, null to ask. */
    fun get(origin: String, kind: SitePermission): Boolean? {
        val key = normalize(origin) ?: return null
        return synchronized(lock) { entries[key]?.get(kind) }
    }

    /** Stores the answer; null forgets it so the site asks again. */
    fun set(origin: String, kind: SitePermission, allow: Boolean?) {
        val key = normalize(origin) ?: return
        synchronized(lock) {
            val current = entries[key].orEmpty()
            if (current[kind] == allow) return
            val next = if (allow == null) current - kind else current + (kind to allow)
            write(key, next)
        }
        bump()
    }

    /** Everything stored for [origin], in the order the settings screens list them. */
    fun forOrigin(origin: String): Map<SitePermission, Boolean> {
        val key = normalize(origin) ?: return emptyMap()
        return synchronized(lock) { entries[key].orEmpty() }.toList().sortedBy { it.first.ordinal }.toMap()
    }

    /** Origins that have at least one stored answer, by host. */
    fun origins(): List<String> = synchronized(lock) { entries.keys.toList() }.sortedBy(SiteOrigins::displayName)

    /** Forgets everything about [origin]. */
    fun clear(origin: String) {
        val key = normalize(origin) ?: return
        synchronized(lock) {
            if (entries[key] == null) return
            write(key, emptyMap())
        }
        bump()
    }

    fun clearAll() {
        synchronized(lock) {
            if (entries.isEmpty()) return
            entries.clear()
            prefs.edit().clear().apply()
        }
        bump()
    }

    private fun write(key: String, value: Map<SitePermission, Boolean>) {
        if (value.isEmpty()) {
            entries.remove(key)
            prefs.edit().remove(key).apply()
        } else {
            entries[key] = value
            prefs.edit().putString(key, encode(value)).apply()
        }
    }

    private fun bump() = _changes.update { it + 1 }

    companion object {
        private const val FILE = "pane_site_permissions"

        /** `https://Example.com:443/a` becomes `https://example.com`; null for anything without a host. */
        fun normalize(originOrUrl: String): String? {
            val origin = SiteOrigins.originOf(originOrUrl.trim()) ?: return null
            return origin.takeIf { SiteOrigins.hostOf(it).isNotEmpty() }
        }

        private fun encode(value: Map<SitePermission, Boolean>): String =
            value.entries.joinToString(",") { (kind, allow) -> "${kind.name}=${if (allow) 1 else 0}" }

        private fun decode(text: String): Map<SitePermission, Boolean> {
            val out = LinkedHashMap<SitePermission, Boolean>()
            for (part in text.split(',')) {
                val kind = SitePermission.entries.firstOrNull { it.name == part.substringBefore('=') } ?: continue
                when (part.substringAfter('=', "")) {
                    "1" -> out[kind] = true
                    "0" -> out[kind] = false
                }
            }
            return out
        }
    }
}
