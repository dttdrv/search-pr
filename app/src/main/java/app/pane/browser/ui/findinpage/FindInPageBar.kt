package app.pane.browser.ui.findinpage

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.TextButton
import app.pane.browser.ui.components.pressDim
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.LocalReduceMotion
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.floating
import app.pane.browser.ui.theme.rememberHaptics
import app.pane.core.prompts.PromptText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.SessionFinder
import kotlin.coroutines.resume
import app.pane.browser.ui.components.excludeFromAutofill

private class Match(val found: Boolean, val current: Int, val total: Int)

/** Typing pauses this long before searching, so a fast typist doesn't restart the search per key. */
private const val DEBOUNCE_MS = 120L

/**
 * Find-in-page as a flat floating pill above the keyboard: the field, a live "3 of 12" counter,
 * previous/next chevrons and a text "Done". The pill rises into place when it appears (the caller
 * shows and hides it). Every match is highlighted; highlights are cleared when the bar goes away.
 */
@Composable
fun FindInPageBar(tabId: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val colors = PaneTheme.colors
    val haptics = rememberHaptics()
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    var query by rememberSaveable(tabId) { mutableStateOf("") }
    var match by remember(tabId) { mutableStateOf<Match?>(null) }
    val reduceMotion = LocalReduceMotion.current
    val risePx = with(LocalDensity.current) { 48.dp.toPx() }
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduceMotion) rise.animateTo(1f, Motion.fade(160)) else rise.animateTo(1f, Motion.bouncy())
    }

    fun finder(): SessionFinder? = container.sessions.session(tabId)?.finder

    LaunchedEffect(tabId) {
        finder()?.let(::highlightAll)
        delay(60)
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    DisposableEffect(tabId) {
        onDispose { container.sessions.session(tabId)?.finder?.clear() }
    }
    LaunchedEffect(tabId, query) {
        val finder = finder() ?: return@LaunchedEffect
        if (query.isEmpty()) {
            finder.clear()
            match = null
            return@LaunchedEffect
        }
        delay(DEBOUNCE_MS)
        match = finder.find(query, GeckoSession.FINDER_FIND_FORWARD).awaitMatch()
    }

    val step: (backwards: Boolean) -> Unit = step@{ backwards ->
        val finder = finder()
        if (finder == null || query.isEmpty()) return@step
        haptics.tick()
        // Same string as the last search, so Gecko moves on from the current match rather than restarting.
        val result = finder.find(query, if (backwards) GeckoSession.FINDER_FIND_BACKWARDS else GeckoSession.FINDER_FIND_FORWARD)
        scope.launch { match = result.awaitMatch() ?: match }
    }
    val close: () -> Unit = {
        finder()?.clear()
        keyboard?.hide()
        onClose()
    }
    BackHandler(onBack = close)

    val hasMatches = (match?.found == true) && (match?.total ?: 0) != 0
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .graphicsLayer {
                val p = rise.value
                alpha = p.coerceIn(0f, 1f)
                if (!reduceMotion) translationY = (1f - p) * risePx
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .floating(PaneShapes.pill, shadow = 6.dp)
                .padding(start = 20.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text("Find on page", style = PaneTheme.type.body, color = colors.secondaryLabel, maxLines = 1)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = PaneTheme.type.body.copy(color = colors.label),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { step(false) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).excludeFromAutofill(),
                )
            }
            val current = match
            AnimatedVisibility(
                visible = query.isNotEmpty() && current != null,
                enter = fadeIn(Motion.fade(120)) + scaleIn(Motion.snappy(), initialScale = 0.8f),
                exit = fadeOut(Motion.fade(100)) + scaleOut(Motion.snappy(), targetScale = 0.8f),
            ) {
                if (current != null) {
                    Text(
                        PromptText.findCounter(current.found, current.current, current.total),
                        style = PaneTheme.type.caption,
                        color = colors.secondaryLabel,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
            StepButton(PaneIcons.ChevronUp, "Previous match", enabled = hasMatches) { step(true) }
            StepButton(PaneIcons.ChevronDown, "Next match", enabled = hasMatches) { step(false) }
            TextButton("Done", onClick = close, bold = true)
        }
    }
}

/** A chevron button that dims when there is nothing to step to. */
@Composable
private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .semantics { contentDescription = description }
            .pressDim(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = PaneTheme.colors.label, modifier = Modifier.size(20.dp))
    }
}

// The display flags are a bit field; GeckoView's annotation just doesn't say so.
@SuppressLint("WrongConstant")
private fun highlightAll(finder: SessionFinder) {
    finder.displayFlags = GeckoSession.FINDER_DISPLAY_HIGHLIGHT_ALL or GeckoSession.FINDER_DISPLAY_DRAW_LINK_OUTLINE
}

private suspend fun GeckoResult<GeckoSession.FinderResult>.awaitMatch(): Match? = suspendCancellableCoroutine { cont ->
    accept({ result ->
        if (cont.isActive) cont.resume(result?.let { Match(it.found, it.current, it.total) })
    }, { _ ->
        if (cont.isActive) cont.resume(null)
    })
}
