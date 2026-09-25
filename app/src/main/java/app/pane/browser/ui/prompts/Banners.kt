package app.pane.browser.ui.prompts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pane.browser.engine.prompts.PopupRequest
import app.pane.browser.engine.prompts.RedirectRequest
import app.pane.browser.ui.components.ChromeButton
import app.pane.browser.ui.components.IconTile
import app.pane.browser.ui.components.TextButton
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.core.prompts.PermissionText
import kotlinx.coroutines.delay

/** Unanswered banners block the thing quietly after this long. */
private const val BANNER_TIMEOUT_MS = 8_000L

/** "Pop-up blocked" with Allow; the page keeps working while it's up. */
@Composable
internal fun PopupBanner(request: PopupRequest, visible: Boolean, onDone: () -> Unit) {
    val target = request.targetUri?.let(PermissionText::displayHost)?.takeIf { it.isNotBlank() }
    BlockedBanner(
        visible = visible,
        icon = PaneIcons.Shield,
        tint = TileColors.orange,
        title = "Pop-up blocked",
        subtitle = target?.let { "This site tried to open $it" } ?: "This site tried to open a new window",
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

/** An embedded frame tried to send the whole tab elsewhere without a tap. */
@Composable
internal fun RedirectBanner(request: RedirectRequest, visible: Boolean, onDone: () -> Unit) {
    val target = request.targetUri?.let(PermissionText::displayHost)?.takeIf { it.isNotBlank() }
    BlockedBanner(
        visible = visible,
        icon = PaneIcons.OpenExternal,
        tint = TileColors.indigo,
        title = "Redirect blocked",
        subtitle = target?.let { "Something on this page tried to go to $it" } ?: "Something on this page tried to leave it",
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
 * A non-modal card floating above the toolbar, in the spirit of Safari's pop-up notice: the page
 * stays usable, and ignoring the card keeps the thing blocked.
 */
@Composable
private fun BlockedBanner(
    visible: Boolean,
    icon: ImageVector,
    tint: Color,
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
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 12.dp, end = 12.dp, bottom = 68.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visibleState = state,
            enter = slideInVertically(Motion.bouncy()) { it / 2 } + scaleIn(Motion.bouncy(), initialScale = 0.9f) + fadeIn(Motion.fade(150)),
            exit = slideOutVertically(Motion.smooth()) { it / 2 } + fadeOut(Motion.fade(180)),
        ) {
            Row(
                Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .shadow(18.dp, PaneShapes.large, ambientColor = colors.shadow, spotColor = colors.shadow)
                    .clip(PaneShapes.large)
                    .background(if (colors.isDark) colors.elevatedSurface else colors.surface)
                    .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(icon, tint)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(title, style = PaneTheme.type.headline, color = colors.label, maxLines = 1)
                    Text(
                        subtitle,
                        style = PaneTheme.type.footnote,
                        color = colors.secondaryLabel,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton("Allow", onClick = onAllow, bold = true)
                ChromeButton(PaneIcons.Close, "Keep blocked", onBlock, tint = colors.secondaryLabel, size = 18.dp)
            }
        }
    }
}
