package app.pane.browser.ui.browser

import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.SiteIcon
import app.pane.browser.ui.components.EngineIcon
import app.pane.browser.ui.components.TextButton
import app.pane.browser.ui.components.excludeFromAutofill
import app.pane.browser.ui.components.pressDim
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.GlassStrength
import app.pane.browser.ui.theme.LocalReduceMotion
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.entrance
import app.pane.browser.ui.theme.glass
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
 * The address bar in edit mode: the page frosted over, a glass pill that rises onto the keyboard
 * with inline completion of known sites, and a merged list of open tabs, history, bookmarks and
 * search suggestions above it. With nothing typed it shows favourites. Open tabs are only offered
 * when [showOpenTabs] (never while private tabs are locked).
 */
@Composable
fun AddressEditor(
    visible: Boolean,
    origin: Rect = Rect.Zero,
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

    // The page, blurred and veiled: a paper-coloured haze that is densest at the top and clears
    // towards the keyboard, so the top stays calm and the list has the contrast. Tapping it closes
    // the editor.
    val paper = PaneTheme.colors.background
    AnimatedVisibility(visibleState = state, enter = fadeIn(Motion.fade(200)), exit = fadeOut(Motion.fade(160))) {
        val veil = remember(paper) {
            Brush.verticalGradient(
                0f to paper.copy(alpha = 0.72f),
                0.5f to paper.copy(alpha = 0.16f),
                1f to Color.Transparent,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .glass(RectangleShape, GlassStrength.Thick, lifted = false)
                .drawBehind { drawRect(veil) }
                .clickable(interactionSource = null, indication = null, onClick = onDismiss),
        )
    }
    // Stays composed a beat after closing, so the field can travel back into the bar.
    AnimatedVisibility(visibleState = state, enter = fadeIn(Motion.fade(120)), exit = fadeOut(Motion.fade(260))) {
        androidx.compose.runtime.key(private, initialText) {
            EditorContent(visible, origin, initialText, private, showOpenTabs, onSubmit, onSwitchToTab, onDismiss)
        }
    }
}

@Composable
private fun EditorContent(
    open: Boolean,
    origin: Rect,
    initialText: String,
    private: Boolean,
    showOpenTabs: Boolean,
    onSubmit: (String) -> Unit,
    onSwitchToTab: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val container = LocalAppContainer.current
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val colors = PaneTheme.colors
    val haptics = rememberHaptics()
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    val reduceMotion = LocalReduceMotion.current
    val risePx = with(LocalDensity.current) { 56.dp.toPx() }
    // The field grows out of the address pill (or rises from below if we don't know where it was),
    // overshoots a touch, and settles on the keyboard. Closing runs it backwards, into the bar.
    val rise = remember { Animatable(0f) }
    LaunchedEffect(open) {
        when {
            reduceMotion -> rise.animateTo(if (open) 1f else 0f, Motion.fade(160))
            open -> rise.animateTo(1f, Motion.bouncy())
            else -> rise.animateTo(0f, Motion.snappy())
        }
    }
    val hasOrigin = origin.width > 1f && origin.height > 1f
    var target by remember { mutableStateOf(Rect.Zero) }

    var field by remember { mutableStateOf(TextFieldValue(initialText, TextRange(0, initialText.length))) }
    /** What the user actually typed, without the inline completion. */
    var typed by remember { mutableStateOf(initialText) }
    var inlineAllowed by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<Suggestion>>(emptyList()) }
    val openTabsAllowed by rememberUpdatedState(showOpenTabs)

    LaunchedEffect(open) {
        if (open) {
            field = TextFieldValue(initialText, TextRange(0, initialText.length))
            typed = initialText
            delay(40)
            focus.requestFocus()
            keyboard?.show()
        } else {
            keyboard?.hide()
        }
    }

    // Suggestions: local results instantly, engine suggestions once typing pauses.
    LaunchedEffect(open, private, settings.rememberHistory, settings.searchEngineId, settings.searchSuggestions, settings.searchSuggestionsInPrivate, showOpenTabs) {
        if (!open) return@LaunchedEffect
        snapshotFlow { typed }.distinctUntilChanged().collectLatest { query ->
            if (query.isBlank() || query == initialText) {
                suggestions = emptyList()
                return@collectLatest
            }
            val now = System.currentTimeMillis()
            val places = (if (!private && settings.rememberHistory) container.history.candidates(query) else emptyList()) +
                container.bookmarks.candidates(query)
            val tabs = container.store.state.value.tabs
                .filter { openTabsAllowed && it.isPrivate == private && it.url.isNotEmpty() && it.id != container.store.state.value.selectedTabId }
                .map { Suggestion.OpenTab(it.id, it.url, it.title) }
            suggestions = SuggestionRanker.rank(query, places, tabs, emptyList(), now)

            // Inline completion only while appending characters.
            val completion = Autocomplete.complete(query, places, now)
            if (inlineAllowed && completion != null && field.composition == null && field.text == query && field.selection.collapsed && field.selection.end == query.length) {
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

    // Before anything is typed: the last few pages, so a return trip is one tap. Private tabs don't
    // offer history.
    val history by remember { container.history.observeRecent(40) }.collectAsStateWithLifecycle(emptyList())
    val recent = remember(history, private, initialText, settings.rememberHistory) {
        if (private || !settings.rememberHistory) {
            emptyList<FavoriteSite>()
        } else {
            history.asSequence()
                .filter { isSiteUrl(it.url) && it.url != initialText }
                .distinctBy { it.url }
                .take(4)
                .map { FavoriteSite(it.url, it.title) }
                .toList()
        }
    }
    val engineId = SearchEngines.byId(settings.searchEngineId).id

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .imePadding()
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        // Tapping the empty space above the field closes the editor; rows and icons take their own taps first.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(interactionSource = null, indication = null, onClickLabel = "Close", onClick = onDismiss),
        ) {
            if (typed.isBlank() || typed == initialText) {
                FavoritesPanel(
                    onOpen = onSubmit,
                    // The clipboard is only read when the user asks, so Android never flags a silent paste.
                    onPasteAndGo = {
                        val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                        if (!text.isNullOrEmpty() && text.length < 4000) onSubmit(text)
                    },
                    recent = recent,
                    includeHistory = !private,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = true,
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    // Reverse layout keeps the best match right above the field, under the thumb.
                    itemsIndexed(suggestions, key = { _, s -> s.key }) { index, s ->
                        SuggestionRow(
                            suggestion = s,
                            query = typed,
                            best = index == 0,
                            onClick = {
                                haptics.tap()
                                when (s) {
                                    is Suggestion.OpenTab -> onSwitchToTab(s.tabId)
                                    is Suggestion.Place -> onSubmit(s.url)
                                    is Suggestion.Search -> onSubmit(s.query)
                                }
                            },
                            onFill = { text ->
                                typed = "$text "
                                field = TextFieldValue("$text ", TextRange(text.length + 1))
                            },
                            modifier = Modifier.animateItem().entrance(index, key = s.key),
                        )
                    }
                }
            }
        }

        // The field: a glass pill docked on the keyboard, with a plain Cancel beside it.
        Row(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val p = rise.value
                    alpha = p.coerceIn(0f, 1f)
                    if (!reduceMotion && !hasOrigin) translationY = (1f - p) * risePx
                }
                .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .onGloballyPositioned { target = it.boundsInRoot() }
                    .graphicsLayer {
                        if (hasOrigin && target.width > 1f) {
                            // Start as the bar's pill: same place, same size; end as the field.
                            val p = rise.value
                            val sx = origin.width / target.width
                            val sy = origin.height / target.height
                            translationX = (origin.center.x - target.center.x) * (1f - p)
                            translationY = (origin.center.y - target.center.y) * (1f - p)
                            scaleX = sx + (1f - sx) * p
                            scaleY = sy + (1f - sy) * p
                        }
                    }
                    .glass(PaneShapes.pill, GlassStrength.Regular),
            ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // The pill stretches; what's written in it doesn't.
                        if (hasOrigin && target.width > 1f) {
                            val p = rise.value
                            val sx = origin.width / target.width + (1f - origin.width / target.width) * p
                            val sy = origin.height / target.height + (1f - origin.height / target.height) * p
                            scaleX = 1f / sx.coerceAtLeast(0.05f)
                            scaleY = 1f / sy.coerceAtLeast(0.05f)
                        }
                    }
                    .padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (field.text.isEmpty()) {
                        Text(
                            if (private) "Search privately" else "Search or enter address",
                            style = PaneTheme.type.body,
                            color = colors.secondaryLabel,
                            maxLines = 1,
                        )
                    }
                    BasicTextField(
                        value = field,
                        onValueChange = { v ->
                            inlineAllowed = v.composition == null && v.text.length > typed.length &&
                                v.text.startsWith(typed) && v.selection.collapsed && v.selection.end == v.text.length
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
                // While the field is empty, a quiet note of which engine a plain query goes to.
                AnimatedVisibility(
                    visible = field.text.isEmpty(),
                    enter = fadeIn(Motion.fade(120)),
                    exit = fadeOut(Motion.fade(100)),
                ) {
                    // The engine's own mark says where a plain query goes; no words needed.
                    EngineIcon(engineId, 26.dp, Modifier.padding(start = 8.dp, end = 10.dp))
                }
                // Clear only exists while there is something to clear.
                AnimatedVisibility(
                    visible = field.text.isNotEmpty(),
                    enter = fadeIn(Motion.fade(120)) + scaleIn(Motion.snappy(), initialScale = 0.5f),
                    exit = fadeOut(Motion.fade(100)) + scaleOut(Motion.snappy(), targetScale = 0.5f),
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .pressDim {
                                field = TextFieldValue("")
                                typed = ""
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(PaneIcons.CloseCircle, contentDescription = "Clear", tint = colors.secondaryLabel, modifier = Modifier.size(20.dp))
                    }
                }
            }
            }
            TextButton("Cancel", onClick = onDismiss)
        }
    }
}

/**
 * One suggestion. Places and open tabs lead with the site's icon, then the title and the host
 * beneath; a quiet label marks tabs and bookmarks. Search suggestions are plain text with an arrow
 * that copies them into the field. The [best] match, the row nearest the field, is set larger and
 * bolder so the eye lands on it.
 */
@Composable
private fun SuggestionRow(
    suggestion: Suggestion,
    query: String,
    best: Boolean,
    onClick: () -> Unit,
    onFill: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PaneTheme.colors
    val url: String?
    val title: String
    val label: String?
    when (suggestion) {
        is Suggestion.OpenTab -> {
            url = suggestion.url
            title = suggestion.title.ifBlank { UrlDisplay.toolbarText(suggestion.url) }
            label = "Tab"
        }
        is Suggestion.Place -> {
            url = suggestion.url
            title = suggestion.title.ifBlank { UrlDisplay.toolbarText(suggestion.url) }
            label = if (suggestion.bookmarked) "Bookmark" else null
        }
        is Suggestion.Search -> {
            url = null
            title = suggestion.query
            label = null
        }
    }
    val host = url?.let { UrlDisplay.toolbarText(it) }
    Row(
        modifier
            .fillMaxWidth()
            .pressDim(onClick = onClick)
            .defaultMinSize(minHeight = if (best) 68.dp else 56.dp)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (url != null) SiteIcon(url, if (best) 44.dp else 32.dp)
        Column(Modifier.weight(1f)) {
            Text(
                highlight(title, query, colors.label),
                style = if (best) PaneTheme.type.headline else PaneTheme.type.body,
                // The best match is read in full ink; the rest recede until the typed part lights up.
                color = if (best) colors.label else colors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (host != null && host != title) {
                Text(host, style = PaneTheme.type.footnote, color = colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (label != null) {
            Text(label, style = PaneTheme.type.caption, color = colors.tertiaryLabel, maxLines = 1)
        }
        if (suggestion is Suggestion.Search) {
            Box(
                Modifier
                    .size(36.dp)
                    .pressDim { onFill(suggestion.query) },
                contentAlignment = Alignment.Center,
            ) {
                // Up and to the left: "put this in the field".
                Icon(
                    PaneIcons.ChevronUp,
                    contentDescription = "Use suggestion",
                    tint = colors.tertiaryLabel,
                    modifier = Modifier.size(18.dp).rotate(-45f),
                )
            }
        }
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
