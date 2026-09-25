package app.pane.browser.ui.tabs

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.browser.BrowserChrome
import app.pane.browser.ui.browser.Monogram
import app.pane.browser.ui.components.AlertAction
import app.pane.browser.ui.components.AlertStyle
import app.pane.browser.ui.components.ChromeButton
import app.pane.browser.ui.components.PaneAlert
import app.pane.browser.ui.components.SegmentedControl
import app.pane.browser.ui.components.TextButton
import app.pane.browser.ui.components.pressDim
import app.pane.browser.ui.components.pressScale
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.ContinuousRoundedShape
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.rememberHaptics
import app.pane.core.tabs.TabState
import app.pane.core.url.UrlDisplay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** A snapshot flying between the full page and a card during open/close. */
private data class Flight(val tabId: String?, val bitmap: Bitmap?, val card: Rect, val page: Rect)

/**
 * The tab overview. Opening zooms the live page down into its card (the snapshot literally flies
 * there on a spring); choosing a card zooms it back up. Cards swipe sideways to close.
 */
@Composable
fun TabSwitcher(
    visible: Boolean,
    chrome: BrowserChrome,
    privateUnlocked: Boolean,
    onRequestUnlock: () -> Unit,
    onClosed: () -> Unit,
    onNewTab: (private: Boolean) -> Unit,
) {
    val container = LocalAppContainer.current
    val state by container.store.state.collectAsStateWithLifecycle()
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    val progress = remember { Animatable(0f) }
    var shown by remember { mutableStateOf(false) }
    var flight by remember { mutableStateOf<Flight?>(null) }
    var showPrivate by remember { mutableStateOf(false) }
    var confirmCloseAll by remember { mutableStateOf(false) }
    val cardRects = remember { mutableStateMapOf<String, Rect>() }
    val gridState = rememberLazyGridState()

    val tabs = state.tabsIn(showPrivate)
    val locked = showPrivate && settings.lockPrivateTabs && !privateUnlocked

    suspend fun awaitCard(id: String): Rect? {
        repeat(6) {
            cardRects[id]?.let { return it }
            withFrameNanos { }
        }
        return cardRects[id]
    }

    LaunchedEffect(visible) {
        if (visible && !shown) {
            val selected = state.selectedTab
            showPrivate = selected?.isPrivate ?: false
            shown = true
            val index = state.tabsIn(showPrivate).indexOfFirst { it.id == selected?.id }
            if (index >= 0) gridState.scrollToItem(index)
            val snapshot = if (selected != null && selected.url.isNotEmpty()) chrome.capture() else null
            if (selected != null && snapshot != null) {
                scope.launch {
                    container.thumbnails.put(selected.id, snapshot, selected.isPrivate)
                    container.store.updateTab(selected.id) { it.copy(thumbnailVersion = it.thumbnailVersion + 1) }
                }
            }
            val card = selected?.let { awaitCard(it.id) }
            flight = if (selected != null && card != null && chrome.pageRect != Rect.Zero) {
                Flight(selected.id, snapshot ?: container.thumbnails.get(selected.id), card, chrome.pageRect)
            } else {
                null
            }
            progress.snapTo(0f)
            progress.animateTo(1f, Motion.spring(0.44f, 0.9f))
            flight = null
        }
    }

    fun closeInto(tab: TabState?) {
        scope.launch {
            val card = tab?.let { cardRects[it.id] }
            if (tab != null) container.browser.select(tab.id)
            flight = if (tab != null && card != null && chrome.pageRect != Rect.Zero) {
                Flight(tab.id, container.thumbnails.get(tab.id), card, chrome.pageRect)
            } else {
                null
            }
            flight?.bitmap?.let { chrome.overlay = app.pane.browser.ui.browser.PageOverlay(it, null, kind = app.pane.browser.ui.browser.PageOverlay.Kind.Cover) }
            progress.animateTo(0f, Motion.spring(0.42f, 0.92f))
            flight = null
            shown = false
            onClosed()
        }
    }

    BackHandler(enabled = shown && visible) {
        closeInto(state.selectedTab?.takeIf { it.isPrivate == showPrivate } ?: tabs.lastOrNull())
    }

    if (!shown) return
    val p = progress.value

    PaneTheme(mode = settings.theme, private = showPrivate, hapticsEnabled = settings.haptics) {
        val colors = PaneTheme.colors
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = p }.background(colors.groupedBackground))
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = ((p - 0.15f) / 0.85f).coerceIn(0f, 1f)
                        val s = 0.94f + 0.06f * p
                        scaleX = s
                        scaleY = s
                    },
            ) {
                Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars))
                AnimatedContent(
                    targetState = showPrivate,
                    transitionSpec = { fadeIn(Motion.fade(220)) togetherWith fadeOut(Motion.fade(160)) },
                    modifier = Modifier.weight(1f),
                    label = "mode",
                ) { privateMode ->
                    val modeTabs = state.tabsIn(privateMode)
                    when {
                        privateMode && locked -> LockedPrivate(onRequestUnlock)
                        modeTabs.isEmpty() -> EmptyTabs(privateMode)
                        else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                            val columns = if (maxWidth > 600.dp) 4 else if (maxWidth > 420.dp) 3 else 2
                            val aspect = if (chrome.pageRect.width > 0f) (chrome.pageRect.height / chrome.pageRect.width).coerceIn(1f, 1.55f) else 1.4f
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                state = gridState,
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(18.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(modeTabs, key = { it.id }) { tab ->
                                    TabCard(
                                        tab = tab,
                                        selected = tab.id == state.selectedTabId,
                                        hidden = flight?.tabId == tab.id,
                                        aspect = aspect,
                                        onPositioned = { cardRects[tab.id] = it },
                                        onClick = {
                                            haptics.tap()
                                            closeInto(tab)
                                        },
                                        onClose = {
                                            haptics.confirm()
                                            container.browser.close(tab.id)
                                            container.thumbnails.remove(tab.id)
                                            cardRects.remove(tab.id)
                                        },
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        }
                    }
                }
                // Bottom bar: new tab · mode · done.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.chrome)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChromeButton(PaneIcons.Plus, "New tab", onClick = {
                        haptics.tap()
                        onNewTab(showPrivate)
                        shown = false
                        scope.launch { progress.snapTo(0f) }
                        onClosed()
                    }, tint = colors.label)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        val normalCount = state.normalTabs.size
                        SegmentedControl(
                            options = listOf("Private", if (normalCount == 1) "1 Tab" else "$normalCount Tabs"),
                            selectedIndex = if (showPrivate) 0 else 1,
                            onSelect = { showPrivate = it == 0 },
                            modifier = Modifier.width(220.dp),
                        )
                    }
                    // Tap: back to the page. Long-press: close everything in this mode.
                    Box(
                        Modifier
                            .height(44.dp)
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onLongClick = {
                                    if (tabs.isNotEmpty()) {
                                        haptics.longPress()
                                        confirmCloseAll = true
                                    }
                                },
                                onClick = { closeInto(state.selectedTab?.takeIf { it.isPrivate == showPrivate } ?: tabs.lastOrNull()) },
                            )
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Done", style = PaneTheme.type.headline, color = colors.label)
                    }
                }
            }

            // The flying snapshot.
            flight?.let { f ->
                val rect = lerp(f.page, f.card, p)
                val density = LocalDensity.current
                val radius = with(density) { (18.dp * p).toPx() }
                Box(
                    Modifier
                        .layout { measurable, _ ->
                            val w = rect.width.roundToInt().coerceAtLeast(1)
                            val h = rect.height.roundToInt().coerceAtLeast(1)
                            val placeable = measurable.measure(Constraints.fixed(w, h))
                            layout(w, h) { placeable.place(0, 0) }
                        }
                        .graphicsLayer {
                            translationX = rect.left
                            translationY = rect.top
                            shadowElevation = 12.dp.toPx() * (1f - abs(0.5f - p) * 2f)
                            shape = ContinuousRoundedShape(with(density) { radius.toDp() })
                            clip = true
                        }
                        .background(colors.surface),
                ) {
                    f.bitmap?.let {
                        Image(
                            it.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            PaneAlert(
                visible = confirmCloseAll,
                title = "Close all ${tabs.size} tabs?",
                message = null,
                actions = listOf(
                    AlertAction("Cancel", AlertStyle.Cancel) { confirmCloseAll = false },
                    AlertAction("Close All", AlertStyle.Destructive) {
                        confirmCloseAll = false
                        container.browser.closeAll(showPrivate)
                        if (showPrivate) container.thumbnails.clearPrivate()
                    },
                ),
                onDismissRequest = { confirmCloseAll = false },
            )
        }
    }
}

@Composable
private fun TabCard(
    tab: TabState,
    selected: Boolean,
    hidden: Boolean,
    aspect: Float,
    onPositioned: (Rect) -> Unit,
    onClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val colors = PaneTheme.colors
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val shape = ContinuousRoundedShape(18.dp)
    val thumb by produceState<Bitmap?>(container.thumbnails.get(tab.id), tab.id, tab.thumbnailVersion) {
        value = container.thumbnails.load(tab.id)
    }
    val swipe = remember { Animatable(0f) }

    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth.toFloat()
        Column(
            Modifier
                .graphicsLayer {
                    translationX = swipe.value
                    alpha = 1f - (abs(swipe.value) / width).coerceIn(0f, 1f) * 0.8f
                }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta -> scope.launch { swipe.snapTo(swipe.value + delta) } },
                    onDragStopped = { velocity ->
                        if (abs(swipe.value) > width * 0.35f || abs(velocity) > 1200f) {
                            swipe.animateTo(if (swipe.value + velocity * 0.1f > 0) width * 1.3f else -width * 1.3f, Motion.snappy(), initialVelocity = velocity)
                            onClose()
                        } else {
                            swipe.animateTo(0f, Motion.bouncy(), initialVelocity = velocity)
                        }
                    },
                ),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f / aspect)
                    .onGloballyPositioned { onPositioned(it.boundsInRoot()) }
                    .pressScale(pressedScale = 0.96f, onClick = onClick)
                    .graphicsLayer {
                        alpha = if (hidden) 0f else 1f
                        shadowElevation = 6.dp.toPx()
                        this.shape = shape
                        clip = true
                    }
                    .background(colors.surface)
                    .then(if (selected) Modifier.border(2.5.dp, colors.accent, shape) else Modifier),
            ) {
                val bitmap = thumb
                if (bitmap != null && tab.url.isNotEmpty()) {
                    Image(
                        bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (tab.url.isEmpty()) {
                            Icon(PaneIcons.Plus, null, tint = colors.tertiaryLabel, modifier = Modifier.size(28.dp))
                        } else {
                            Monogram(UrlDisplay.toolbarText(tab.url), size = 44)
                        }
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .clip(ContinuousRoundedShape(13.dp))
                        .background(colors.scrim.copy(alpha = 0.45f))
                        .pressDim {
                            haptics.confirm()
                            scope.launch {
                                swipe.animateTo(-width * 1.3f, Motion.snappy())
                                onClose()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(PaneIcons.Close, "Close tab", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(14.dp))
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, start = 2.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (tab.url.isNotEmpty()) Monogram(UrlDisplay.toolbarText(tab.url), size = 16)
                Text(
                    tab.title.ifBlank { if (tab.url.isEmpty()) "Start Page" else UrlDisplay.toolbarText(tab.url) },
                    style = PaneTheme.type.caption,
                    color = colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyTabs(private: Boolean) {
    val colors = PaneTheme.colors
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(if (private) PaneIcons.Private else PaneIcons.Tabs, null, tint = colors.tertiaryLabel, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(14.dp))
        Text(if (private) "Private Browsing" else "No Open Tabs", style = PaneTheme.type.title3, color = colors.label)
        Spacer(Modifier.height(6.dp))
        Text(
            if (private) "Private tabs leave no history, cookies or site data behind." else "Tap + to start browsing.",
            style = PaneTheme.type.subheadline,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LockedPrivate(onUnlock: () -> Unit) {
    val colors = PaneTheme.colors
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(PaneIcons.Lock, null, tint = colors.accent, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(14.dp))
        Text("Private Tabs Locked", style = PaneTheme.type.title3, color = colors.label)
        Spacer(Modifier.height(18.dp))
        TextButton("Unlock", onClick = onUnlock, bold = true)
    }
}

