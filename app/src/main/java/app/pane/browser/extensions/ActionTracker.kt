package app.pane.browser.extensions

import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.mozilla.geckoview.Image
import org.mozilla.geckoview.WebExtension

/**
 * Keeps the latest browser/page action of every extension: the extension-wide default and the
 * per-tab overrides extensions set with a `tabId` (badges, icons, titles).
 *
 * Icons are rendered to bitmaps lazily. Blockers update their per-tab badge and icon many times a
 * second while pages load, so at most one render per button is in flight, per-tab icons are only
 * rendered for the selected tab, and the previous bitmap stays up meanwhile so nothing flickers.
 *
 * Main thread only, like the engine callbacks that feed it.
 */
internal class ActionTracker(private val iconPx: Int) {

    data class Entry(val action: WebExtension.Action, val icon: ImageBitmap?, val isPageAction: Boolean)

    private val _defaults = MutableStateFlow<Map<String, Entry>>(emptyMap())
    val defaults: StateFlow<Map<String, Entry>> = _defaults.asStateFlow()

    /** tabId → extensionId → override. */
    private val _perTab = MutableStateFlow<Map<String, Map<String, Entry>>>(emptyMap())
    val perTab: StateFlow<Map<String, Map<String, Entry>>> = _perTab.asStateFlow()

    private var selectedTabId: String? = null

    /** Buttons whose bitmap doesn't match their latest action yet. */
    private val stale = HashSet<String>()
    private val rendering = HashSet<String>()

    fun onAction(extensionId: String, tabId: String?, action: WebExtension.Action, isPageAction: Boolean) {
        val image: Image? = action.icon
        val previous = get(extensionId, tabId)
        put(extensionId, tabId, Entry(action, if (image != null) previous?.icon else null, isPageAction))
        val key = key(extensionId, tabId)
        if (image == null) {
            stale.remove(key)
            return
        }
        stale.add(key)
        if (tabId == null || tabId == selectedTabId) render(extensionId, tabId)
    }

    fun onTabSelected(tabId: String?) {
        selectedTabId = tabId
        if (tabId == null) return
        _perTab.value[tabId]?.keys?.forEach { extensionId -> render(extensionId, tabId) }
    }

    fun removeExtension(extensionId: String) {
        _defaults.update { it - extensionId }
        _perTab.update { all -> all.mapValues { (_, byExtension) -> byExtension - extensionId } }
        stale.removeAll { it.startsWith("$extensionId$SEPARATOR") }
    }

    fun removeTab(tabId: String) {
        _perTab.update { it - tabId }
        stale.removeAll { it.endsWith("$SEPARATOR$tabId") }
    }

    /**
     * What clicking [extensionId]'s button in [tabId] should act on: the tab's override merged over
     * the default, then the default alone as a fallback.
     */
    fun clickTargets(extensionId: String, tabId: String?): List<WebExtension.Action> {
        val base = _defaults.value[extensionId]?.action
        val override = tabId?.let { _perTab.value[it]?.get(extensionId)?.action }
        return listOfNotNull(merge(override, base), base).distinct()
    }

    private fun render(extensionId: String, tabId: String?) {
        val key = key(extensionId, tabId)
        if (key !in stale || !rendering.add(key)) return
        stale.remove(key)
        val image: Image? = get(extensionId, tabId)?.action?.icon
        if (image == null) {
            rendering.remove(key)
            return
        }
        val finish = { bitmap: ImageBitmap? ->
            rendering.remove(key)
            val current = get(extensionId, tabId)
            if (bitmap != null && current != null && current.action.icon != null) {
                put(extensionId, tabId, current.copy(icon = bitmap))
            }
            // Newer actions arrived while this one was rendering.
            if (key in stale && (tabId == null || tabId == selectedTabId)) render(extensionId, tabId)
        }
        try {
            image.getBitmap(iconPx).accept(
                { bitmap -> finish(bitmap?.asImageBitmap()) },
                { error ->
                    Log.d(TAG, "No action icon for $extensionId", error)
                    finish(null)
                },
            )
        } catch (e: Exception) {
            Log.d(TAG, "No action icon for $extensionId", e)
            finish(null)
        }
    }

    private fun get(extensionId: String, tabId: String?): Entry? =
        if (tabId == null) _defaults.value[extensionId] else _perTab.value[tabId]?.get(extensionId)

    private fun put(extensionId: String, tabId: String?, entry: Entry) {
        if (tabId == null) {
            _defaults.update { it + (extensionId to entry) }
        } else {
            _perTab.update { all -> all + (tabId to (all[tabId].orEmpty() + (extensionId to entry))) }
        }
    }

    private fun key(extensionId: String, tabId: String?) = extensionId + SEPARATOR + tabId.orEmpty()

    companion object {
        private const val TAG = "ExtensionActions"
        private const val SEPARATOR = "\u0000"

        /** The toolbar/menu buttons for the selected tab, in the order of [installed]. */
        fun toUi(
            tabId: String?,
            defaults: Map<String, Entry>,
            perTab: Map<String, Map<String, Entry>>,
            installed: List<InstalledExtension>,
        ): List<ExtensionActionUi> {
            val overrides = tabId?.let { perTab[it] }.orEmpty()
            return installed.filter { it.enabled }.mapNotNull { ext ->
                val base = defaults[ext.id]
                val override = overrides[ext.id]
                val action = merge(override?.action, base?.action) ?: return@mapNotNull null
                val isPageAction = (base ?: override)?.isPageAction == true
                // A page action only exists while the extension shows it for this tab.
                if (isPageAction && action.enabled != true) return@mapNotNull null
                ExtensionActionUi(
                    extensionId = ext.id,
                    title = action.title?.takeIf { it.isNotBlank() } ?: ext.name,
                    icon = override?.icon ?: base?.icon ?: ext.icon,
                    badgeText = action.badgeText?.takeIf { it.isNotBlank() },
                    enabled = action.enabled != false,
                )
            }
        }

        private fun merge(override: WebExtension.Action?, base: WebExtension.Action?): WebExtension.Action? = when {
            override == null -> base
            base == null -> override
            else -> try {
                override.withDefault(base)
            } catch (e: Exception) {
                // withDefault refuses actions of different types (browser vs page).
                override
            }
        }
    }
}
