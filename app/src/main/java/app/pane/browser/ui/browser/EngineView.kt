package app.pane.browser.ui.browser

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.core.tabs.TabState

/**
 * Shows whichever tab is selected. Each page is its own WebView, owned by the session manager;
 * this is only the frame that the selected one is placed in, so switching tabs moves a view
 * rather than building one.
 */
@Composable
fun EngineView(
    tab: TabState?,
    modifier: Modifier = Modifier,
    hidden: Boolean = false,
) {
    val container = LocalAppContainer.current
    val version by container.sessions.sessionsVersion.collectAsStateWithLifecycle()
    AndroidView(
        modifier = modifier,
        factory = { context -> FrameLayout(context).apply { clipChildren = true } },
        update = { frame ->
            @Suppress("UNUSED_EXPRESSION") version
            frame.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
            // A locked private page is covered on screen; screen readers mustn't walk into it either.
            frame.importantForAccessibility =
                if (hidden) View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            val page = container.sessions.session(tab?.id)
            val current = frame.getChildAt(0)
            if (current !== page) {
                frame.removeAllViews()
                if (page != null) {
                    (page.parent as? ViewGroup)?.removeView(page)
                    frame.addView(page, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                }
            }
        },
        onRelease = { frame -> frame.removeAllViews() },
    )
}
