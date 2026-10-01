package app.pane.browser.ui.tabs

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.browser.BrowserChrome
import app.pane.browser.ui.browser.siteName
import app.pane.browser.ui.components.AlertAction
import app.pane.browser.ui.components.AlertStyle
import app.pane.browser.ui.components.DotText
import app.pane.browser.ui.components.FloatingCircle
import app.pane.browser.ui.components.SiteIcon
import app.pane.browser.ui.components.PaneAlert
import app.pane.browser.ui.components.PrimaryButton
import app.pane.browser.ui.components.SegmentedControl
import app.pane.browser.ui.components.pressScale
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.ContinuousRoundedShape
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.entrance
import app.pane.browser.ui.theme.floating
import app.pane.browser.ui.theme.rememberHaptics
import app.pane.core.tabs.TabState
import app.pane.core.url.UrlDisplay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** A snapshot flying between the full page and a card during open/close. */
private class Flight(val tabId: String?, val bitmap: Bitmap?, val card: Rect, val page: Rect, val isPrivate: Boolean)

/** Corner radius shared by the cards and the flying snapshot, so the hand-off has no seam. */
private val CardRadius = 16.dp

/** The floating control cluster: its height, the gap under it, and how far below the screen it waits. */
private val ClusterHeight = 58.dp
private val ClusterGap = 10.dp
private val ClusterRest = 140.dp

/**
 * The tab overview. Opening zooms the live page down into its card (the snapshot literally flies
 * there on a spring); choosing a card zooms it back up. Cards are flat (a hairline, the open one in
 * ink) and swipe sideways to close. A floating cluster (New · Private/Tabs · Done) rises over the
 * grid, which scrolls beneath it. While [privateLocked], private tabs show only a line saying so
 * and never fly in or out.
 */
@Composable
fun TabSwitcher(
    visible: Boolean,
    chrome: BrowserChrome,
    privateLocked: Boolean,
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
    /** 0 = the cluster waits below the screen, 1 = it floats in place. */
    val cluster = remember { Animatable(0f) }
    var shown by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var flight by remember { mutableStateOf<Flight?>(null) }
    var confirmCloseAll by remember { mutableStateOf(false) }
    val cardRects = remember { mutableStateMapOf<String, Rect>() }
    val normalGrid = rememberLazyGridState()
    val privateGrid = rememberLazyGridState()
    // Cards already seen this time the overview is open, so scrolling never replays their entrance.
    val enteredIds = remember { mutableSetOf<String>() }

    // Which set is showing lives in the chrome, so the activity can block screenshots of private tabs.
    val showPrivate = chrome.showPrivateTabs
    val privacyTransition = updateTransition(showPrivate, label = "tabPrivacy")
    SideEffect {
        BrowserChrome.privateTabsShowing.value = shown &&
            (privacyTransition.currentState || privacyTransition.targetState)
    }
    val tabs = state.tabsIn(showPrivate)

    suspend fun awaitCard(id: String): Rect? {
        repeat(6) {
            cardRects[id]?.let { return it }
            withFrameNanos { }
        }
        return cardRects[id]
    }

    LaunchedEffect(visible) {
        if (visible && !shown) {
            closing = false
            val selected = state.selectedTab
            chrome.showPrivateTabs = selected?.isPrivate ?: false
            enteredIds.clear()
            shown = true
            val index = state.tabsIn(chrome.showPrivateTabs).indexOfFirst { it.id == selected?.id }
            if (index >= 0) (if (chrome.showPrivateTabs) privateGrid else normalGrid).scrollToItem(index)
            val snapshot = if (selected != null && selected.url.isNotEmpty() && !(selected.isPrivate && privateLocked)) {
                chrome.capture()?.takeIf {
                    val current = container.store.state.value.selectedTab
                    current?.id == selected.id && current.url == selected.url && !BrowserChrome.pageLocked.value
                }
            } else null
            if (selected != null && snapshot != null) {
                scope.launch {
                    container.thumbnails.put(selected.id, snapshot, selected.isPrivate)
                    container.store.updateTab(selected.id) { it.copy(thumbnailVersion = it.thumbnailVersion + 1) }
                }
            }
            // A locked private page never flies, not even to a card left over from before it locked.
            val card = selected?.takeUnless { it.isPrivate && privateLocked }?.let { awaitCard(it.id) }
            if (closing) return@LaunchedEffect
            flight = if (!settings.reduceMotion && selected != null &&
                selected.id == container.store.state.value.selectedTabId && card != null && chrome.pageRect != Rect.Zero
            ) {
                Flight(selected.id, snapshot ?: container.thumbnails.get(selected.id), card, chrome.pageRect, selected.isPrivate)
            } else {
                null
            }
            progress.snapTo(0f)
            cluster.snapTo(0f)
            launch { cluster.animateTo(1f, if (settings.reduceMotion) Motion.fade(0) else Motion.bouncy()) }
            progress.animateTo(1f, if (settings.reduceMotion) Motion.fade(0) else Motion.bouncy())
            flight = null
        } else if (!visible) {
            flight = null
            shown = false
            closing = false
            progress.snapTo(0f)
            cluster.snapTo(0f)
        }
    }

    fun closeInto(tab: TabState?) {
        if (closing) return
        closing = true
        scope.launch {
            val card = tab?.takeUnless { it.isPrivate && privateLocked }?.let { cardRects[it.id] }
            if (tab != null) container.browser.select(tab.id)
            flight = if (!settings.reduceMotion && tab != null && card != null && chrome.pageRect != Rect.Zero) {
                Flight(tab.id, container.thumbnails.get(tab.id), card, chrome.pageRect, tab.isPrivate)
            } else {
                null
            }
            flight?.bitmap?.let { chrome.overlay = app.pane.browser.ui.browser.PageOverlay(it, null, kind = app.pane.browser.ui.browser.PageOverlay.Kind.Cover) }
            launch { cluster.animateTo(0f, if (settings.reduceMotion) Motion.fade(0) else Motion.snappy()) }
            progress.animateTo(0f, if (settings.reduceMotion) Motion.fade(0) else Motion.push())
            flight = null
            shown = false
            onClosed()
        }
    }

    BackHandler(enabled = shown && visible) {
        closeInto(state.selectedTab?.takeIf { it.isPrivate == showPrivate } ?: tabs.lastOrNull())
    }

    if (!shown) return

    PaneTheme(mode = settings.theme, private = showPrivate, hapticsEnabled = settings.haptics, reduceMotion = settings.reduceMotion) {
        val colors = PaneTheme.colors
        // The last row of cards must be able to scroll clear of the floating cluster.
        val clusterSpace = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + ClusterHeight + ClusterGap + 18.dp

        run {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                        .background(colors.background),
                )
                Column(
                    Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = if (showPrivate) "Private tabs: ${tabs.size}" else "Open tabs: ${tabs.size}" }
                        .graphicsLayer {
                            val p = progress.value
                            alpha = ((p - 0.15f) / 0.85f).coerceIn(0f, 1f)
                            val s = 0.94f + 0.06f * p
                            scaleX = s
                            scaleY = s
                        },
                ) {
                    Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars))
                    privacyTransition.AnimatedContent(
                        transitionSpec = {
                            // Private is the left segment, so it arrives from the left and normal from the right.
                            val sign = if (targetState) -1 else 1
                            if (settings.reduceMotion) fadeIn(Motion.fade(0)) togetherWith fadeOut(Motion.fade(0)) else
                            (fadeIn(Motion.fade(220)) + slideInHorizontally(Motion.smooth()) { sign * it / 10 }) togetherWith
                                (fadeOut(Motion.fade(160)) + slideOutHorizontally(Motion.smooth()) { -sign * it / 10 })
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            ,
                    ) { privateMode ->
                        val modeTabs = state.tabsIn(privateMode)
                        when {
                            privateMode && privateLocked -> LockedPrivate(clusterSpace, onRequestUnlock)
                            modeTabs.isEmpty() -> EmptyTabs(privateMode, clusterSpace)
                            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                                val columns = if (maxWidth > 600.dp) 4 else if (maxWidth > 420.dp) 3 else 2
                                val aspect = if (chrome.pageRect.width > 0f) (chrome.pageRect.height / chrome.pageRect.width).coerceIn(1f, 1.55f) else 1.4f
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(columns),
                                    state = if (privateMode) privateGrid else normalGrid,
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = clusterSpace),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(18.dp),
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    itemsIndexed(modeTabs, key = { _, tab -> tab.id }) { index, tab ->
                                        // Cards arrive once, staggered. The selected card is the one the snapshot
                                        // flies into, so it stays put and the flight lands exactly on it.
                                        val arrive = remember { enteredIds.add(tab.id) && tab.id != state.selectedTabId }
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
                                            modifier = Modifier
                                                .animateItem(
                                                    fadeInSpec = Motion.fade<Float>(200),
                                                    placementSpec = Motion.pushOffset,
                                                    fadeOutSpec = Motion.fade<Float>(160),
                                                )
                                                .then(if (arrive) Modifier.entrance(index, tab.id) else Modifier),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // The flying snapshot. Every animated value is read inside the layout/graphicsLayer
                // lambdas, so the flight never recomposes per frame.
                // Locking must hide an in-flight private bitmap in the same composition,
                // even if capture or its animation began while the tabs were unlocked.
                flight?.takeUnless { privateLocked && it.isPrivate }?.let { f ->
                    Box(
                        Modifier
                            .layout { measurable, _ ->
                                val rect = lerp(f.page, f.card, progress.value)
                                val w = rect.width.roundToInt().coerceAtLeast(1)
                                val h = rect.height.roundToInt().coerceAtLeast(1)
                                val placeable = measurable.measure(Constraints.fixed(w, h))
                                layout(w, h) { placeable.place(0, 0) }
                            }
                            .graphicsLayer {
                                val p = progress.value
                                val rect = lerp(f.page, f.card, p)
                                translationX = rect.left
                                translationY = rect.top
                                shape = ContinuousRoundedShape((CardRadius.value * p.coerceIn(0f, 1f)).dp)
                                clip = true
                            }
                            .background(colors.background),
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

                // The floating cluster, echoing the browser's own bar (circle · pill · circle): a plus for a
                // new tab on the left, the Private / Tabs switch in the middle, and an ink check to go back
                // to the page on the right. It rises from below on a bouncy spring and hovers clear of the
                // screen edges; the grid scrolls beneath it.
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(start = 12.dp, end = 12.dp, bottom = ClusterGap)
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .graphicsLayer { translationY = (1f - cluster.value) * ClusterRest.toPx() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FloatingCircle(
                        onClick = {
                            onNewTab(showPrivate)
                            shown = false
                            scope.launch {
                                progress.snapTo(0f)
                                cluster.snapTo(0f)
                            }
                            onClosed()
                        },
                        size = ClusterHeight - 4.dp,
                        contentDescription = "New tab",
                    ) {
                        Icon(PaneIcons.Plus, null, tint = PaneTheme.colors.label, modifier = Modifier.size(24.dp))
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .height(ClusterHeight - 4.dp)
                            .floating(PaneShapes.pill, shadow = 6.dp)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val normalCount = state.normalTabs.size
                        SegmentedControl(
                            options = listOf("Private", if (normalCount == 1) "1 Tab" else "$normalCount Tabs"),
                            selectedIndex = if (showPrivate) 0 else 1,
                            onSelect = { chrome.showPrivateTabs = it == 0 },
                            modifier = Modifier.fillMaxWidth(),
                            height = ClusterHeight - 12.dp,
                        )
                    }
                    // Tap: back to the page. Long-press: close everything in this mode.
                    DoneCircle(
                        onClick = { closeInto(state.selectedTab?.takeIf { it.isPrivate == showPrivate } ?: tabs.lastOrNull()) },
                        onLongClick = { if (tabs.isNotEmpty()) confirmCloseAll = true },
                    )
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
}

/** The ink check that returns to the page. Long-press (haptic included) asks to close every tab in this mode. */
@Composable
private fun DoneCircle(onClick: () -> Unit, onLongClick: () -> Unit) {
    val colors = PaneTheme.colors
    Box(
        Modifier
            .size(ClusterHeight - 4.dp)
            .semantics { contentDescription = "Done" }
            .pressScale(pressedScale = 0.9f, haptic = true, onLongClick = onLongClick, onClick = onClick)
            .floating(CircleShape)
            .background(colors.accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(PaneIcons.Check, null, tint = colors.onAccent, modifier = Modifier.size(24.dp))
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
    val shape = remember { ContinuousRoundedShape(CardRadius) }
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
                    .pressScale(pressedScale = 0.97f, onClick = onClick)
                    .graphicsLayer {
                        alpha = if (hidden) 0f else 1f
                        this.shape = shape
                        clip = true
                    }
                    .background(colors.background)
                    // Flat: a hairline, and the open tab in ink. Drawn over the page image.
                    .border(if (selected) 2.dp else 1.dp, if (selected) colors.label else colors.hairline, shape),
            ) {
                val bitmap = thumb
                if (bitmap != null && tab.url.isNotEmpty()) {
                    val image = remember(bitmap) { bitmap.asImageBitmap() }
                    Image(
                        image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (tab.url.isNotEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            siteName(UrlDisplay.toolbarText(tab.url)),
                            style = PaneTheme.type.headline,
                            color = colors.tertiaryLabel,
                            maxLines = 1,
                        )
                    }
                }
                // A small hairline ring with a cross in the corner; the touch target is larger than it looks.
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(40.dp)
                        .semantics { contentDescription = "Close tab" }
                        .pressScale(pressedScale = 0.9f) {
                            scope.launch {
                                swipe.animateTo(-width * 1.3f, Motion.snappy())
                                onClose()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(colors.background)
                            .border(1.dp, colors.hairline, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(PaneIcons.Close, null, tint = colors.label, modifier = Modifier.size(11.dp))
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (tab.url.isNotEmpty()) SiteIcon(tab.url, 18.dp)
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

/** Nothing open: a short dot-matrix line, and for private browsing one quiet sentence. */
@Composable
private fun EmptyTabs(private: Boolean, bottomSpace: Dp) {
    val colors = PaneTheme.colors
    Column(
        Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 32.dp, bottom = bottomSpace),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DotText(if (private) "PRIVATE" else "NO TABS", dot = 3.dp, modifier = Modifier.entrance(0))
        if (private) {
            Spacer(Modifier.height(20.dp))
            Text(
                "Nothing is saved.",
                style = PaneTheme.type.subheadline,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.entrance(1),
            )
        }
    }
}

/** Private tabs are locked: a short dot-matrix line and a solid Unlock. */
@Composable
private fun LockedPrivate(bottomSpace: Dp, onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 32.dp, bottom = bottomSpace),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DotText("LOCKED", dot = 3.dp, modifier = Modifier.entrance(0))
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Unlock", onClick = onUnlock, modifier = Modifier.width(200.dp).entrance(1))
    }
}
