package app.pane.browser.ui.findinpage

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.ChromeButton
import app.pane.browser.ui.components.Separator
import app.pane.browser.ui.components.TextButton
import app.pane.browser.ui.components.pressDim
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.ContinuousRoundedShape
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.rememberHaptics
import app.pane.core.prompts.PromptText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.SessionFinder
import kotlin.coroutines.resume

private class Match(val found: Boolean, val current: Int, val total: Int)

/** Typing pauses this long before searching, so a fast typist doesn't restart the search per key. */
private const val DEBOUNCE_MS = 120L

/**
 * Find-in-page docked above the keyboard, as in Safari: a search capsule with a live "3 of 12"
 * counter, previous/next chevrons and Done. Every match is highlighted; highlights are cleared
 * when the bar goes away.
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

    val hasMatches = (match?.found == true) && (match?.total ?: 0) != 0
    Column(modifier.fillMaxWidth().background(colors.chrome)) {
        Separator()
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .height(52.dp)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(ContinuousRoundedShape(10.dp))
                    .background(colors.fill)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(PaneIcons.Search, null, tint = colors.secondaryLabel, modifier = Modifier.size(17.dp))
                Box(Modifier.weight(1f).padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text("Find on Page", style = PaneTheme.type.body, color = colors.secondaryLabel, maxLines = 1)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = PaneTheme.type.body.copy(color = colors.label),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { step(false) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                val current = match
                AnimatedVisibility(
                    visible = query.isNotEmpty() && current != null,
                    enter = fadeIn(Motion.fade(120)),
                    exit = fadeOut(Motion.fade(120)),
                ) {
                    if (current != null) {
                        Text(
                            PromptText.findCounter(current.found, current.current, current.total),
                            style = PaneTheme.type.footnote,
                            color = colors.secondaryLabel,
                            maxLines = 1,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
                if (query.isNotEmpty()) {
                    Icon(
                        PaneIcons.CloseCircle,
                        contentDescription = "Clear",
                        tint = colors.secondaryLabel,
                        modifier = Modifier.size(18.dp).pressDim { query = "" },
                    )
                }
            }
            ChromeButton(PaneIcons.ChevronUp, "Previous match", { step(true) }, enabled = hasMatches, size = 22.dp)
            ChromeButton(PaneIcons.ChevronDown, "Next match", { step(false) }, enabled = hasMatches, size = 22.dp)
            TextButton("Done", onClick = close, bold = true)
        }
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
