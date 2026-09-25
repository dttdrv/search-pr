package app.pane.browser.ui.browser

import android.view.View
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
    hidden: Boolean = false,
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
            // A locked private page is covered on screen; screen readers mustn't walk into it either.
            view.importantForAccessibility =
                if (hidden) View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
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
