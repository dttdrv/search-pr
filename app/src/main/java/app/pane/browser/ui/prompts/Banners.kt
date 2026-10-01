package app.pane.browser.ui.prompts

import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.components.ChromeButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pane.browser.engine.prompts.PopupRequest
import app.pane.browser.ui.components.TextButton
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.dropIn
import app.pane.browser.ui.theme.floating
import app.pane.core.prompts.PermissionText
import kotlinx.coroutines.delay

/** Unanswered banners block the thing quietly after this long. */
private const val BANNER_TIMEOUT_MS = 8_000L

/** "Pop-up blocked" with Allow; the page keeps working while it's up. */
@Composable
internal fun PopupBanner(request: PopupRequest, visible: Boolean, onDone: () -> Unit) {
    val target = request.targetUri?.takeIf { !it.startsWith("about:") }?.let(PermissionText::displayHost)?.takeIf { it.isNotBlank() }
    BlockedBanner(
        visible = visible,
        title = "Pop-up blocked",
        subtitle = target?.let { "Tried to open $it" } ?: "Tried to open a window",
        onAllow = {
            request.answer(true)
            onDone()
        },
        onBlock = {
            request.answer(false)
            onDone()
        },
    )
}

/**
 * A non-modal flat pill floating under the status bar, a hairline round it and no icon: it drops
 * in, the page stays usable, and ignoring it keeps the thing blocked.
 */
@Composable
private fun BlockedBanner(
    visible: Boolean,
    title: String,
    subtitle: String,
    onAllow: () -> Unit,
    onBlock: () -> Unit,
) {
    val colors = PaneTheme.colors
    val state = remember { MutableTransitionState(false) }
    state.targetState = visible

    LaunchedEffect(Unit) {
        delay(BANNER_TIMEOUT_MS)
        onBlock()
    }

    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 12.dp, end = 12.dp, top = 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        // Arrival is dropIn()'s spring; only the way out is a transition.
        AnimatedVisibility(
            visibleState = state,
            enter = EnterTransition.None,
            exit = slideOutVertically(Motion.smooth()) { -it } + fadeOut(Motion.fade(160)),
        ) {
            Row(
                Modifier
                    .dropIn()
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .floating(PaneShapes.pill)
                    .padding(start = 24.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 6.dp)) {
                    Text(title, style = PaneTheme.type.headline, color = colors.label, maxLines = 1)
                    Text(
                        subtitle,
                        style = PaneTheme.type.footnote,
                        color = colors.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ChromeButton(PaneIcons.Close, "Dismiss", onClick = onBlock, tint = colors.secondaryLabel)
                ChromeButton(PaneIcons.Check, "Allow", onClick = onAllow)
            }
        }
    }
}
