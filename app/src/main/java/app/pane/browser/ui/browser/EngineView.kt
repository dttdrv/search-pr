package app.pane.browser.ui.browser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.core.tabs.TabState
import org.mozilla.geckoview.GeckoView

/**
 * The single GeckoView that renders whichever tab is selected. Sessions are swapped in and out
 * rather than creating a view per tab, which keeps memory flat regardless of tab count.
 */
@Composable
fun EngineView(
    tab: TabState?,
    modifier: Modifier = Modifier,
    coverColor: Int,
    onViewCreated: (GeckoView) -> Unit = {},
) {
    val container = LocalAppContainer.current
    val version by container.sessions.sessionsVersion.collectAsStateWithLifecycle()
    AndroidView(
        modifier = modifier,
        factory = { context ->
            GeckoView(context).also {
                it.setAutofillEnabled(true)
                onViewCreated(it)
            }
        },
        update = { view ->
            @Suppress("UNUSED_EXPRESSION") version
            val session = container.sessions.session(tab?.id)?.takeIf { it.isOpen }
            if (view.session !== session) {
                if (view.session != null) view.releaseSession()
                if (session != null) {
                    view.coverUntilFirstPaint(coverColor)
                    view.setSession(session)
                }
            }
        },
        onRelease = { view -> if (view.session != null) view.releaseSession() },
    )
}
