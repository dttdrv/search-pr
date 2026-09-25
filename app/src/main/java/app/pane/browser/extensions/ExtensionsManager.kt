package app.pane.browser.extensions

import android.content.Context
import app.pane.browser.engine.BrowserController
import app.pane.browser.engine.SessionManager
import app.pane.browser.engine.WebFetcher
import app.pane.core.tabs.BrowserStore
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.mozilla.geckoview.GeckoRuntime

/** An extension's toolbar button as it applies to the selected tab. */
data class ExtensionActionUi(
    val extensionId: String,
    val title: String,
    val icon: ImageBitmap?,
    val badgeText: String?,
    val enabled: Boolean,
)

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
    /** Browser/page actions of enabled extensions for the selected tab, shown in the menu. */
    val actions: StateFlow<List<ExtensionActionUi>> = MutableStateFlow(emptyList())

    /** Called once after the runtime is up. */
    fun start() = Unit

    /** Performs an extension's action (usually opens its popup). */
    fun clickAction(extensionId: String) = Unit

    /** Toggles reader view for a tab (called from the toolbar). */
    fun toggleReaderMode(tabId: String) = Unit
}
