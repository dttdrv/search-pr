package app.pane.browser.ui.browser

import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.TextButton
import app.pane.browser.ui.components.excludeFromAutofill
import app.pane.browser.ui.components.pressDim
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.ContinuousRoundedShape
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.rememberHaptics
import app.pane.core.search.SearchEngines
import app.pane.core.search.Suggestions
import app.pane.core.suggest.Autocomplete
import app.pane.core.suggest.Suggestion
import app.pane.core.suggest.SuggestionRanker
import app.pane.core.url.UrlDisplay
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The address bar in edit mode: a field that rides on top of the keyboard, with inline
 * completion of known sites and a merged list of open tabs, history, bookmarks and search
 * suggestions above it. With nothing typed it shows favourites. Open tabs are only offered when
 * [showOpenTabs] (never while private tabs are locked).
 */
@Composable
fun AddressEditor(
    visible: Boolean,
    initialText: String,
    private: Boolean,
    showOpenTabs: Boolean,
    onSubmit: (String) -> Unit,
    onSwitchToTab: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = visible
    if (!state.currentState && !state.targetState && state.isIdle) return

    BackHandler(enabled = visible, onBack = onDismiss)
    val colors = PaneTheme.colors

    AnimatedVisibility(visibleState = state, enter = fadeIn(Motion.fade(160)), exit = fadeOut(Motion.fade(160))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.groupedBackground)
                .clickable(interactionSource = null, indication = null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visibleState = state,
        enter = slideInVertically(Motion.smooth()) { it / 8 } + fadeIn(Motion.fade(140)),
        exit = slideOutVertically(Motion.smooth()) { it / 8 } + fadeOut(Motion.fade(120)),
    ) {
        EditorContent(initialText, private, showOpenTabs, onSubmit, onSwitchToTab, onDismiss)
    }
}

@Composable
private fun EditorContent(
    initialText: String,
    private: Boolean,
    showOpenTabs: Boolean,
    onSubmit: (String) -> Unit,
    onSwitchToTab: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val container = LocalAppContainer.current
    val colors = PaneTheme.colors
    val haptics = rememberHaptics()
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val focus = remember { FocusRequester() }

    var field by remember { mutableStateOf(TextFieldValue(initialText, TextRange(0, initialText.length))) }
    /** What the user actually typed, without the inline completion. */
    var typed by remember { mutableStateOf(initialText) }
    var suggestions by remember { mutableStateOf<List<Suggestion>>(emptyList()) }
    val openTabsAllowed by rememberUpdatedState(showOpenTabs)

    LaunchedEffect(Unit) {
        delay(40)
        focus.requestFocus()
        keyboard?.show()
    }

    // Suggestions: local results instantly, engine suggestions once typing pauses.
    LaunchedEffect(Unit) {
        snapshotFlow { typed }.distinctUntilChanged().collectLatest { query ->
            if (query.isBlank() || query == initialText) {
                suggestions = emptyList()
                return@collectLatest
            }
            val now = System.currentTimeMillis()
            val places = container.history.candidates(query) + container.bookmarks.candidates(query)
            val tabs = container.store.state.value.tabs
                .filter { openTabsAllowed && it.isPrivate == private && it.url.isNotEmpty() && it.id != container.store.state.value.selectedTabId }
                .map { Suggestion.OpenTab(it.id, it.url, it.title) }
            suggestions = SuggestionRanker.rank(query, places, tabs, emptyList(), now)

            // Inline completion only while appending characters.
            val completion = Autocomplete.complete(query, places, now)
            if (completion != null && field.text == query && field.selection.collapsed && field.selection.end == query.length) {
                val stripped = query.lowercase().removePrefix("https://").removePrefix("http://")
                val suffix = completion.removePrefix(stripped)
                if (suffix.isNotEmpty() && completion.startsWith(stripped)) {
                    field = TextFieldValue(query + suffix, TextRange(query.length, query.length + suffix.length))
                }
            }

            val settings = container.settings.current
            val allowed = settings.searchSuggestions && (!private || settings.searchSuggestionsInPrivate)
            if (!allowed || query.length < 2 || query.startsWith("@")) return@collectLatest
            delay(140)
            val engine = SearchEngines.byId(settings.searchEngineId)
            val url = engine.suggestUrl(query) ?: return@collectLatest
            val body = container.fetcher.text(url, private = private) ?: return@collectLatest
            val remote = Suggestions.parse(body, engine.suggestFormat)
            suggestions = SuggestionRanker.rank(query, places, tabs, remote, now)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .imePadding()
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (typed.isBlank() || typed == initialText) {
                FavoritesPanel(
                    onOpen = onSubmit,
                    // The clipboard is only read when the user asks, so Android never flags a silent paste.
                    onPasteAndGo = {
                        val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                        if (!text.isNullOrEmpty() && text.length < 4000) onSubmit(text)
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    // Reverse layout keeps the best match right above the field, under the thumb.
                    items(suggestions, key = { it.key }) { s ->
                        SuggestionRow(
                            suggestion = s,
                            query = typed,
                            onClick = {
                                haptics.tap()
                                when (s) {
                                    is Suggestion.OpenTab -> onSwitchToTab(s.tabId)
                                    is Suggestion.Place -> onSubmit(s.url)
                                    is Suggestion.Search -> onSubmit(s.query)
                                }
                            },
                            onFill = { text ->
                                typed = text
                                field = TextFieldValue("$text ", TextRange(text.length + 1))
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }

        // The field, docked on the keyboard.
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.chrome)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(ContinuousRoundedShape(14.dp))
                    .background(colors.fill)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (private) PaneIcons.Private else PaneIcons.Search, null, tint = colors.secondaryLabel, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (field.text.isEmpty()) {
                        Text("Search or enter website", style = PaneTheme.type.body, color = colors.secondaryLabel, maxLines = 1)
                    }
                    BasicTextField(
                        value = field,
                        onValueChange = { v ->
                            // Deleting while a completion is shown removes just the completion.
                            val hadCompletion = !field.selection.collapsed && field.selection.end == field.text.length && field.text.startsWith(typed)
                            if (hadCompletion && v.text == typed) {
                                field = TextFieldValue(typed, TextRange(typed.length))
                            } else {
                                field = v
                                typed = v.text
                            }
                        },
                        singleLine = true,
                        textStyle = PaneTheme.type.body.copy(color = colors.label),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go,
                        ),
                        keyboardActions = KeyboardActions(onGo = { if (field.text.isNotBlank()) onSubmit(field.text) }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus)
                            .excludeFromAutofill()
                            // The keyboard eats the first Back; the next one should close the editor, not just unfocus.
                            .onPreviewKeyEvent { e ->
                                if (e.key == Key.Back && e.type == KeyEventType.KeyUp) {
                                    onDismiss()
                                    true
                                } else {
                                    false
                                }
                            },
                    )
                }
                if (field.text.isNotEmpty()) {
                    Icon(
                        PaneIcons.CloseCircle,
                        contentDescription = "Clear",
                        tint = colors.secondaryLabel,
                        modifier = Modifier.size(20.dp).pressDim {
                            field = TextFieldValue("")
                            typed = ""
                        },
                    )
                }
            }
            TextButton("Cancel", onClick = onDismiss)
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: Suggestion,
    query: String,
    onClick: () -> Unit,
    onFill: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PaneTheme.colors
    val (icon, title, subtitle) = when (suggestion) {
        is Suggestion.OpenTab -> Triple(PaneIcons.Tabs, suggestion.title.ifBlank { UrlDisplay.toolbarText(suggestion.url) }, "Switch to Tab · ${UrlDisplay.toolbarText(suggestion.url)}")
        is Suggestion.Place -> Triple(
            if (suggestion.bookmarked) PaneIcons.Bookmark else PaneIcons.Clock,
            suggestion.title.ifBlank { UrlDisplay.toolbarText(suggestion.url) },
            UrlDisplay.toolbarText(suggestion.url),
        )
        is Suggestion.Search -> Triple(PaneIcons.Search, suggestion.query, null)
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(ContinuousRoundedShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SuggestionIcon(icon)
        Column(Modifier.weight(1f)) {
            Text(highlight(title, query, colors.label), style = PaneTheme.type.body, color = colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = PaneTheme.type.footnote, color = colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (suggestion is Suggestion.Search) {
            Icon(
                PaneIcons.ChevronUp,
                contentDescription = "Use suggestion",
                tint = colors.tertiaryLabel,
                modifier = Modifier.size(20.dp).pressDim { onFill(suggestion.query) },
            )
        }
    }
}

@Composable
private fun SuggestionIcon(icon: ImageVector) {
    val colors = PaneTheme.colors
    Box(
        Modifier.size(30.dp).clip(ContinuousRoundedShape(8.dp)).background(colors.fill),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = colors.secondaryLabel, modifier = Modifier.size(16.dp))
    }
}

/** Emphasises the parts of [text] the user has typed, iOS-style (typed text in full colour). */
private fun highlight(text: String, query: String, strong: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    val q = query.trim()
    val index = if (q.isEmpty()) -1 else text.indexOf(q, ignoreCase = true)
    if (index < 0) {
        withStyle(SpanStyle(color = strong)) { append(text) }
        return@buildAnnotatedString
    }
    append(text.substring(0, index))
    withStyle(SpanStyle(color = strong, fontWeight = FontWeight.SemiBold)) { append(text.substring(index, index + q.length)) }
    append(text.substring(index + q.length))
}

