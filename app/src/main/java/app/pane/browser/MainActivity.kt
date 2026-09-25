package app.pane.browser

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import app.pane.browser.ui.AppRoot
import app.pane.browser.ui.navigation.Navigator
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.mozilla.geckoview.BasicSelectionActionDelegate

class MainActivity : ComponentActivity() {
    private val container: AppContainer get() = (application as PaneApp).container
    private val navigator = Navigator()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        container.sessions.selectionDelegateFactory = { BasicSelectionActionDelegate(this) }

        setContent { AppRoot(container, navigator) }

        lifecycleScope.launch {
            val restored = container.store.state.value.tabs.isNotEmpty() || container.sessions.restore()
            val handled = savedInstanceState == null && handleIntent(intent)
            if (!handled && !restored) container.browser.newTab(private = false)
        }
        observeWindowFlags()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch { container.sessions.persistNow() }
    }

    /** Opens links, searches and shared text from other apps. Returns true if a tab was opened. */
    private fun handleIntent(intent: Intent?): Boolean {
        intent ?: return false
        val browser = container.browser
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val url = intent.dataString ?: return false
                val scheme = intent.data?.scheme?.lowercase()
                if (scheme != "http" && scheme != "https") return false
                navigator.closeAll()
                browser.open(url, newTab = true, private = false)
                return true
            }
            Intent.ACTION_WEB_SEARCH, Intent.ACTION_SEARCH -> {
                val query = intent.getStringExtra(SearchManager.QUERY)?.takeIf { it.isNotBlank() } ?: return false
                navigator.closeAll()
                browser.submit(query, tabId = browser.newTab(private = false), private = false)
                return true
            }
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotEmpty() } ?: return false
                // Shared text often wraps a link in prose; prefer the first URL in it.
                val url = Regex("https?://\\S+").find(text)?.value
                navigator.closeAll()
                val tab = browser.newTab(private = false)
                browser.submit(url ?: text, tabId = tab, private = false)
                return true
            }
            Intent.ACTION_PROCESS_TEXT -> {
                val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return false
                navigator.closeAll()
                browser.submit(text, tabId = browser.newTab(private = false), private = false)
                return true
            }
        }
        return false
    }

    /** Screenshots are blocked while a private tab is showing; fullscreen video hides system bars. */
    private fun observeWindowFlags() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        lifecycleScope.launch {
            combine(container.store.state, container.settings.state) { state, settings ->
                Triple(
                    state.selectedTab?.isPrivate == true && settings.secureScreenInPrivate,
                    state.selectedTab?.fullscreen == true,
                    Unit,
                )
            }.distinctUntilChanged().collect { (secure, fullscreen, _) ->
                if (secure) {
                    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                if (fullscreen) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}
