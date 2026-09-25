package app.pane.browser.engine.prompts

import app.pane.browser.settings.SettingsStore
import app.pane.core.settings.BrowserSettings
import app.pane.core.tabs.BrowserStore
import org.mozilla.geckoview.GeckoRuntime

/**
 * The few app services prompt delegates need beyond their `(tabId, queue)` constructors: settings
 * (autoplay, pop-ups), the tab's current URL (dialog throttling) and the runtime (permission
 * storage).
 *
 * Until [install] runs, the defaults are the conservative choices: autoplay and pop-ups blocked,
 * nothing persisted.
 */
object PromptEnvironment {
    @Volatile
    var settings: () -> BrowserSettings = { BrowserSettings() }
        private set

    @Volatile
    var pageUrl: (tabId: String) -> String? = { null }
        private set

    @Volatile
    var runtime: GeckoRuntime? = null
        private set

    /** Idempotent; called by the app container at start-up. */
    fun install(runtime: GeckoRuntime, settings: SettingsStore, store: BrowserStore) {
        this.runtime = runtime
        this.settings = { settings.current }
        this.pageUrl = { tabId -> store.state.value.tab(tabId)?.url }
    }
}
