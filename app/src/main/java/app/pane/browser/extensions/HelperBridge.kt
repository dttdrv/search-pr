package app.pane.browser.extensions

import android.content.SharedPreferences
import android.util.Log
import app.pane.browser.engine.SessionManager
import app.pane.core.tabs.BrowserStore
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension

/**
 * Native end of Pane's built-in helper extension, whose content script runs in the top frame of
 * every page. It reports the page's `theme-color` and whether Reader View can show it, and renders
 * Reader View in place when asked.
 *
 * Each document opens its own port (`runtime.connectNative`), so a tab's port is replaced on every
 * navigation and dropped when the page unloads or goes into the back-forward cache. Leaving reader
 * view reloads the page, which is the only reliable way to get the original DOM back.
 *
 * Main thread only.
 */
internal class HelperBridge(
    private val store: BrowserStore,
    private val sessions: SessionManager,
    private val prefs: SharedPreferences,
    private val onMessage: (String) -> Unit,
) {
    private var extension: WebExtension? = null
    private val ports = HashMap<String, WebExtension.Port>()
    private val lastUrls = HashMap<String, String>()

    /** Starts listening to [live] sessions once the extension is installed. */
    fun attach(ext: WebExtension, live: Map<String, GeckoSession>) {
        val first = extension == null
        extension = ext
        // Delegates are keyed by extension id, so a newer instance of the same extension needs no re-registration.
        if (first) live.forEach { (tabId, session) -> attachSession(tabId, session) }
    }

    fun attachSession(tabId: String, session: GeckoSession) {
        val ext = extension ?: return
        try {
            session.webExtensionController.setMessageDelegate(ext, messageDelegate(tabId), NATIVE_APP)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't listen to the helper in $tabId", e)
        }
    }

    fun onSessionClosed(tabId: String) {
        ports.remove(tabId)?.let { runCatching { it.disconnect() } }
        lastUrls.remove(tabId)
    }

    /**
     * Same-document navigations (history.pushState, fragments) reset the tab's reader state without
     * reloading the content script, so ask it to report again.
     */
    fun onTabUrls(urls: Map<String, String>) {
        for ((tabId, url) in urls) {
            val previous = lastUrls.put(tabId, url)
            if (previous != null && previous != url && ports.containsKey(tabId)) {
                post(tabId, JSONObject().put("type", "getMeta"))
            }
        }
        lastUrls.keys.retainAll(urls.keys)
    }

    fun toggleReader(tabId: String) {
        val tab = store.state.value.tab(tabId) ?: return
        if (tab.inReaderMode) {
            exitReader(tabId)
            return
        }
        if (extension == null || !ports.containsKey(tabId)) {
            onMessage("Reader View isn’t ready yet. Try again once the page has loaded.")
            return
        }
        val message = JSONObject()
            .put("type", "reader")
            .put("enter", true)
            .put("theme", readerTheme)
            .put("fontScale", readerFontScale.toDouble())
        if (!post(tabId, message)) onMessage("Reader View isn’t available for this page.")
    }

    private fun exitReader(tabId: String) {
        store.updateTab(tabId) { it.copy(inReaderMode = false) }
        sessions.reload(tabId)
    }

    val readerTheme: String get() = prefs.getString(KEY_THEME, null)?.takeIf { it in THEMES } ?: "auto"
    val readerFontScale: Float get() = prefs.getFloat(KEY_SCALE, 1f).coerceIn(MIN_SCALE, MAX_SCALE)

    private fun post(tabId: String, message: JSONObject): Boolean {
        val port = ports[tabId] ?: return false
        return try {
            port.postMessage(message)
            true
        } catch (e: Exception) {
            Log.d(TAG, "Helper port for $tabId is gone", e)
            if (ports[tabId] === port) ports.remove(tabId)
            false
        }
    }

    private fun messageDelegate(tabId: String) = object : WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
            // A newer document's port replaces the previous one; the old one disconnects on its own.
            ports[tabId] = port
            port.setDelegate(portDelegate(tabId))
        }

        override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
            handle(tabId, message)
            return null
        }
    }

    private fun portDelegate(tabId: String) = object : WebExtension.PortDelegate {
        override fun onPortMessage(message: Any, port: WebExtension.Port) = handle(tabId, message)

        override fun onDisconnect(port: WebExtension.Port) {
            if (ports[tabId] === port) ports.remove(tabId)
        }
    }

    private fun handle(tabId: String, message: Any?) {
        val msg = message as? JSONObject ?: return
        try {
            when (msg.optString("type")) {
                "meta" -> onMeta(tabId, msg)
                "reader" -> onReaderState(tabId, msg)
                "readerStyle" -> saveStyle(msg)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bad helper message", e)
        }
    }

    private fun onMeta(tabId: String, msg: JSONObject) {
        val tab = store.state.value.tab(tabId) ?: return
        // Replies from a document that has since been navigated away from are stale.
        val url = msg.stringOrNull("url")
        if (url != null && !sameDocument(url, tab.url)) return
        val color = msg.stringOrNull("themeColor")?.let { SessionManager.parseCssColor(it) }
        val inReader = msg.optBoolean("reader", false)
        val readerable = msg.optBoolean("readerable", false) || inReader
        store.updateTab(tabId) {
            // No theme-color keeps what the web app manifest provided, if anything.
            it.copy(themeColor = color ?: it.themeColor, readerable = readerable, inReaderMode = inReader)
        }
    }

    private fun onReaderState(tabId: String, msg: JSONObject) {
        val active = msg.optBoolean("active", false)
        store.updateTab(tabId) { it.copy(inReaderMode = active, readerable = it.readerable || active) }
        if (!active && msg.has("error")) onMessage("Reader View isn’t available for this page.")
    }

    private fun saveStyle(msg: JSONObject) {
        val editor = prefs.edit()
        msg.stringOrNull("theme")?.takeIf { it in THEMES }?.let { editor.putString(KEY_THEME, it) }
        if (msg.has("fontScale")) {
            val scale = msg.optDouble("fontScale", 1.0).toFloat()
            if (!scale.isNaN()) editor.putFloat(KEY_SCALE, scale.coerceIn(MIN_SCALE, MAX_SCALE))
        }
        editor.apply()
    }

    companion object {
        private const val TAG = "PaneHelper"

        /** The name content scripts pass to `browser.runtime.connectNative`. */
        const val NATIVE_APP = "pane"

        private const val KEY_THEME = "reader_theme"
        private const val KEY_SCALE = "reader_font_scale"
        private const val MIN_SCALE = 0.7f
        private const val MAX_SCALE = 2.0f
        private val THEMES = setOf("auto", "light", "sepia", "dark")

        /** Ignores the fragment and a trailing slash, which don't change the document. */
        fun sameDocument(a: String, b: String): Boolean = normalize(a) == normalize(b)

        private fun normalize(url: String) = url.substringBefore('#').trimEnd('/')

        private fun JSONObject.stringOrNull(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    }
}
