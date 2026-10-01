package app.pane.browser.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.ImageVector
import app.pane.browser.ui.icons.PaneIcons
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.theme.ContinuousRoundedShape
import app.pane.browser.ui.theme.LocalReduceMotion
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.floating

enum class AlertStyle { Default, Cancel, Destructive }

data class AlertAction(val label: String, val style: AlertStyle = AlertStyle.Default, val onClick: () -> Unit)

/** The glyph in front of an alert's button: close to back out, a check to go ahead. */
private fun AlertAction.glyph(): ImageVector = when (style) {
    AlertStyle.Cancel -> PaneIcons.Close
    AlertStyle.Default -> PaneIcons.Check
    AlertStyle.Destructive -> PaneIcons.Check
}

/**
 * A centred flat floating card that springs in from a touch under full size: a title, an optional
 * short message, an optional [body] (fields for text and auth prompts) and the actions stacked: the
 * one real choice is a solid pill (accent blue, or red when it destroys something) and Cancel is a
 * quiet glyph-and-word line under it, never a second pill. A destructive alert opens with a small
 * red glyph. Cancel sits last however the caller ordered it.
 */
@Composable
fun PaneAlert(
    visible: Boolean,
    title: String?,
    message: String?,
    actions: List<AlertAction>,
    onDismissRequest: () -> Unit,
    body: (@Composable () -> Unit)? = null,
) {
    val colors = PaneTheme.colors
    val reduce = LocalReduceMotion.current
    val state = remember { MutableTransitionState(false) }
    state.targetState = visible
    if (!state.currentState && !state.targetState && state.isIdle) return

    BackHandler(enabled = visible, onBack = onDismissRequest)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visibleState = state, enter = fadeIn(Motion.fade(160)), exit = fadeOut(Motion.fade(160))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.scrim.copy(alpha = colors.scrim.alpha * 0.7f))
                    .clickable(remember { MutableInteractionSource() }, indication = null) { },
            )
        }
        AnimatedVisibility(
            visibleState = state,
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(vertical = 24.dp),
            enter = if (reduce) {
                fadeIn(Motion.fade(160))
            } else {
                scaleIn(Motion.smooth(), initialScale = 0.92f) + fadeIn(Motion.fade(140))
            },
            exit = if (reduce) {
                fadeOut(Motion.fade(140))
            } else {
                scaleOut(Motion.snappy(), targetScale = 0.94f) + fadeOut(Motion.fade(140))
            },
        ) {
            Column(
                Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 340.dp)
                    .fillMaxWidth()
                    .floating(AlertShape)
                    .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (actions.any { it.style == AlertStyle.Destructive }) {
                        Box(
                            Modifier.padding(bottom = 14.dp).size(48.dp).clip(CircleShape).background(colors.destructive.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(PaneIcons.Trash, null, tint = colors.destructive, modifier = Modifier.size(22.dp))
                        }
                    }
                    if (title != null) {
                        Text(title, style = PaneTheme.type.title3, color = colors.label, textAlign = TextAlign.Center)
                    }
                    if (message != null) {
                        Text(
                            message,
                            style = PaneTheme.type.subheadline,
                            color = colors.secondaryLabel,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    if (body != null) Box(Modifier.padding(top = 16.dp)) { body() }
                }
                Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val lone = actions.size == 1
                    // Cancel goes last; sortedBy is stable, so the caller's order holds otherwise.
                    actions.sortedBy { it.style == AlertStyle.Cancel }.forEach { action ->
                        when {
                            lone || action.style == AlertStyle.Default -> PrimaryButton(text = action.label, onClick = action.onClick, icon = action.glyph())
                            action.style == AlertStyle.Cancel -> QuietButton(action.label, action.onClick, Modifier.fillMaxWidth(), icon = action.glyph())
                            else -> PrimaryButton(action.label, action.onClick, style = ButtonStyle.Destructive, icon = action.glyph())
                        }
                    }
                }
            }
        }
    }
}

private val AlertShape = ContinuousRoundedShape(32.dp)
