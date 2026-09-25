package app.pane.browser.extensions

import android.content.Context
import app.pane.browser.engine.BrowserController
import app.pane.browser.engine.SessionManager
import app.pane.browser.engine.WebFetcher
import app.pane.core.tabs.BrowserStore
import kotlinx.coroutines.CoroutineScope
import org.mozilla.geckoview.GeckoRuntime

/**
 * WebExtension support: installing from AMO or files, enabling/disabling, browser actions and
 * popups, and Pane's built-in helper extension (reader mode, page theme colour).
 *
 * STUB: owned by the extensions work stream.
 */
class ExtensionsManager(
    private val context: Context,
    private val runtime: GeckoRuntime,
    private val sessions: SessionManager,
    private val browser: BrowserController,
    private val store: BrowserStore,
    private val fetcher: WebFetcher,
    private val scope: CoroutineScope,
) {
    /** Called once after the runtime is up. */
    fun start() = Unit

    /** Toggles reader view for a tab (called from the toolbar). */
    fun toggleReaderMode(tabId: String) = Unit
}
