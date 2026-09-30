package app.pane.browser.ui.components

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.theme.GlassStrength
import app.pane.browser.ui.theme.LocalReduceMotion
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.glass

enum class AlertStyle { Default, Cancel, Destructive }

data class AlertAction(val label: String, val style: AlertStyle = AlertStyle.Default, val onClick: () -> Unit)

/**
 * A centred floating glass card that springs in from a touch under full size: title, message, an
 * optional [body] (fields for text and auth prompts) and the actions as stacked pills. The primary
 * action is solid ink, Cancel is tinted, Destructive is red on a red tint; a lone action is always
 * solid. Cancel sits last however the caller ordered it.
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
                scaleIn(Motion.bouncy(), initialScale = 0.92f) + fadeIn(Motion.fade(140))
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
                    .glass(PaneShapes.floating, GlassStrength.Thick)
                    .padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (title != null) {
                        Text(title, style = PaneTheme.type.title3, color = colors.label, textAlign = TextAlign.Center)
                    }
                    if (message != null) {
                        Text(
                            message,
                            style = PaneTheme.type.subheadline,
                            color = colors.secondaryLabel,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    if (body != null) Box(Modifier.padding(top = 16.dp)) { body() }
                }
                Column(Modifier.padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Cancel goes last; sortedBy is stable, so the caller's order holds otherwise.
                    actions.sortedBy { it.style == AlertStyle.Cancel }.forEach { action ->
                        PrimaryButton(
                            text = action.label,
                            onClick = action.onClick,
                            style = when {
                                actions.size == 1 -> ButtonStyle.Filled
                                action.style == AlertStyle.Cancel -> ButtonStyle.Tinted
                                action.style == AlertStyle.Destructive -> ButtonStyle.Destructive
                                else -> ButtonStyle.Filled
                            },
                        )
                    }
                }
            }
        }
    }
}
