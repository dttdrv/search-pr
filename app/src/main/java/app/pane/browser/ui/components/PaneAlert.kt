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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.theme.ContinuousRoundedShape
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.rememberHaptics

enum class AlertStyle { Default, Cancel, Destructive }

data class AlertAction(val label: String, val style: AlertStyle = AlertStyle.Default, val onClick: () -> Unit)

/**
 * UIAlertController(.alert): a 270pt card that pops in from 1.15× scale. Two actions sit side by
 * side, more stack vertically. [body] can host fields for text and auth prompts.
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
    val haptics = rememberHaptics()
    val state = remember { MutableTransitionState(false) }
    state.targetState = visible
    if (!state.currentState && !state.targetState && state.isIdle) return

    BackHandler(enabled = visible, onBack = onDismissRequest)

    Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visibleState = state, enter = fadeIn(Motion.fade(150)), exit = fadeOut(Motion.fade(200))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.scrim.copy(alpha = colors.scrim.alpha * 0.6f))
                    .clickable(remember { MutableInteractionSource() }, indication = null) { },
            )
        }
        AnimatedVisibility(
            visibleState = state,
            enter = scaleIn(Motion.spring(0.35f, 0.9f), initialScale = 1.15f) + fadeIn(Motion.fade(120)),
            exit = fadeOut(Motion.fade(180)) + scaleOut(Motion.fade(180), targetScale = 0.95f),
        ) {
            Column(
                Modifier
                    .width(280.dp)
                    .clip(ContinuousRoundedShape(16.dp))
                    .background(if (colors.isDark) colors.elevatedSurface else colors.surface.copy(alpha = 0.98f)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (title != null) {
                        Text(title, style = PaneTheme.type.headline, color = colors.label, textAlign = TextAlign.Center)
                    }
                    if (message != null) {
                        Text(
                            message,
                            style = PaneTheme.type.footnote,
                            color = colors.label,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (body != null) Box(Modifier.padding(top = 12.dp)) { body() }
                }
                Separator()
                val buttonModifier = Modifier.height(44.dp)
                if (actions.size == 2) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        actions.forEachIndexed { i, action ->
                            AlertButton(action, buttonModifier.weight(1f)) { haptics.tap(); action.onClick() }
                            if (i == 0) Box(Modifier.width(0.5.dp).fillMaxHeight().background(colors.separator))
                        }
                    }
                } else {
                    actions.forEachIndexed { i, action ->
                        AlertButton(action, buttonModifier.fillMaxWidth()) { haptics.tap(); action.onClick() }
                        if (i < actions.lastIndex) Separator()
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertButton(action: AlertAction, modifier: Modifier, onClick: () -> Unit) {
    val colors = PaneTheme.colors
    Box(modifier.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(
            action.label,
            style = PaneTheme.type.body.copy(fontWeight = if (action.style == AlertStyle.Cancel) FontWeight.SemiBold else FontWeight.Normal),
            color = if (action.style == AlertStyle.Destructive) colors.destructive else colors.accent,
            maxLines = 1,
        )
    }
}
