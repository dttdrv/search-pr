package app.pane.browser.ui.browser

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.pane.browser.ui.components.GlassCircle
import app.pane.browser.ui.components.pressDim
import app.pane.browser.ui.components.pressScale
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.GlassStrength
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.glass
import app.pane.browser.ui.theme.rememberHaptics
import app.pane.core.tabs.SecurityState
import app.pane.core.tabs.TabState
import app.pane.core.url.UrlDisplay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Sizes of the floating bar. The page runs underneath it edge to edge. */
object BarMetrics {
    val pill: Dp = 50.dp
    val gap: Dp = 10.dp

    /** The strip the bar occupies above the navigation inset, gaps included. */
    val zone: Dp = pill + gap * 2
    val mini: Dp = 30.dp
}

/**
 * The floating bar: back (only when there is somewhere to go), the address pill, the tab count and
 * the menu, each a piece of frosted glass hovering over the page. The pill swipes sideways to move
 * between tabs (past the last tab it opens a new one) and up for the tab overview. Scrolling the
 * page melts the bar into a small host label; tapping that brings it back. While private tabs are
 * [locked] it names no page and doesn't swipe.
 */
@Composable
fun BottomBar(
    tab: TabState?,
    tabs: List<TabState>,
    chrome: BrowserChrome,
    navBarHeight: Dp,
    locked: Boolean,
    private: Boolean,
    onAddress: () -> Unit,
    onBack: () -> Unit,
    onTabs: () -> Unit,
    onNewTab: () -> Unit,
    onMenu: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onSiteInfo: () -> Unit,
    onSwipeCommit: (targetTabId: String?) -> Unit,
    onSwipeStart: (neighborId: String?) -> Unit,
    onSwipeCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val collapse = chrome.collapse
    // Whole-bar visibility flips rarely; the per-frame motion is read in graphicsLayer lambdas, so
    // scrolling never recomposes the bar.
    val miniShown by remember { derivedStateOf { collapse.value > 0.02f } }
    val rowShown by remember { derivedStateOf { collapse.value < 0.59f } }

    val index = tabs.indexOfFirst { it.id == tab?.id }
    val previous = tabs.getOrNull(index - 1)
    val next = tabs.getOrNull(index + 1)

    val swipe = remember { Animatable(0f) }
    val thresholdPassed = remember { booleanArrayOf(false) }
    val canGoBack = tab?.canGoBack == true || tab?.parentId != null

    Box(modifier.fillMaxWidth().padding(bottom = navBarHeight)) {
        // Collapsed: just the host, floating low. Tap to bring the bar back.
        if (miniShown) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .graphicsLayer {
                        val t = ((collapse.value - 0.25f) / 0.75f).coerceIn(0f, 1f)
                        alpha = t
                        val s = 0.86f + 0.14f * t
                        scaleX = s
                        scaleY = s
                    }
                    .height(BarMetrics.mini)
                    .pressScale(pressedScale = 0.94f) { chrome.expand() }
                    .glass(PaneShapes.pill, GlassStrength.Thin)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (locked) LOCKED_LABEL else tab?.let { UrlDisplay.toolbarText(it.url) }.orEmpty().ifEmpty { "New tab" },
                    style = PaneTheme.type.caption.copy(fontWeight = FontWeight.Medium),
                    color = PaneTheme.colors.label,
                    maxLines = 1,
                )
            }
        }

        // Expanded row.
        if (rowShown) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .graphicsLayer {
                        val c = collapse.value
                        alpha = (1f - c * 1.7f).coerceIn(0f, 1f)
                        translationY = c * 26.dp.toPx()
                        val s = 1f - 0.07f * c
                        scaleX = s
                        scaleY = s
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(
                    visible = canGoBack,
                    enter = expandHorizontally(Motion.snappy()) + scaleIn(Motion.bouncy(), initialScale = 0.5f) + fadeIn(Motion.fade(140)),
                    exit = shrinkHorizontally(Motion.snappy()) + scaleOut(Motion.snappy(), targetScale = 0.5f) + fadeOut(Motion.fade(120)),
                ) {
                    Row {
                        GlassCircle(onClick = onBack, size = BarMetrics.pill, contentDescription = "Back") {
                            Icon(PaneIcons.Back, null, tint = PaneTheme.colors.label, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                }
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .height(BarMetrics.zone)
                        // Neighbouring tabs slide in from the sides, so clip there but leave room for shadows.
                        .drawWithContent {
                            // Clip only while another tab is sliding in; at rest the pill's shadow may spill past the edge.
                            if (swipe.value != 0f) {
                                clipRect(left = 0f, top = -size.height, right = size.width, bottom = size.height * 2) {
                                    this@drawWithContent.drawContent()
                                }
                            } else {
                                drawContent()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val width = constraints.maxWidth.toFloat()
                    val stride = width + with(density) { 12.dp.toPx() }
                    val showPrevious by remember { derivedStateOf { swipe.value > 0f } }
                    val showNext by remember { derivedStateOf { swipe.value < 0f } }
                    val dragState = rememberDraggableState { delta ->
                        val hasTarget = if (swipe.value + delta > 0) previous != null else true
                        val resisted = if (hasTarget) delta else delta * 0.25f
                        scope.launch { swipe.snapTo(swipe.value + resisted) }
                        chrome.tabSwipe = (swipe.value / stride).coerceIn(-1f, 1f)
                        val past = abs(swipe.value) > stride * 0.3f
                        if (past != thresholdPassed[0]) {
                            thresholdPassed[0] = past
                            if (past) haptics.tick()
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .draggable(
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { },
                                onDragStopped = { velocity -> if (velocity < -600f) onTabs() },
                            )
                            .draggable(
                                orientation = Orientation.Horizontal,
                                state = dragState,
                                // Swiping would slide the neighbouring private tabs into view.
                                enabled = !locked,
                                onDragStarted = {
                                    thresholdPassed[0] = false
                                    onSwipeStart(next?.id ?: previous?.id)
                                },
                                onDragStopped = { velocity ->
                                    val toNext = swipe.value < 0
                                    val target = if (toNext) next else previous
                                    val commit = (abs(swipe.value) > stride * 0.3f || abs(velocity) > 900f) &&
                                        (target != null || toNext)
                                    if (commit) {
                                        haptics.confirm()
                                        swipe.animateTo(if (toNext) -stride else stride, Motion.snappy(), initialVelocity = velocity)
                                        onSwipeCommit(target?.id)
                                        swipe.snapTo(0f)
                                        chrome.tabSwipe = 0f
                                    } else {
                                        swipe.animateTo(0f, Motion.bouncy(), initialVelocity = velocity) {
                                            chrome.tabSwipe = (value / stride).coerceIn(-1f, 1f)
                                        }
                                        chrome.tabSwipe = 0f
                                        onSwipeCancel()
                                    }
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Neighbours slide in alongside the current pill.
                        if (showPrevious && previous != null) {
                            AddressPill(previous, private, Modifier.offset { IntOffset((swipe.value - stride).roundToInt(), 0) }, lifted = false)
                        }
                        if (showNext) {
                            if (next != null) {
                                AddressPill(next, private, Modifier.offset { IntOffset((swipe.value + stride).roundToInt(), 0) }, lifted = false)
                            } else {
                                NewTabPill(Modifier.offset { IntOffset((swipe.value + stride).roundToInt(), 0) })
                            }
                        }
                        AddressPill(
                            tab = tab,
                            private = private,
                            modifier = Modifier
                                .offset { IntOffset(swipe.value.roundToInt(), 0) }
                                .onGloballyPositioned { chrome.pillRect = it.boundsInRoot() },
                            locked = locked,
                            onClick = onAddress,
                            onReload = onReload,
                            onStop = onStop,
                            onSiteInfo = onSiteInfo,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                TabsButton(count = tabs.size, onClick = onTabs, onLongClick = onNewTab)
                Spacer(Modifier.width(8.dp))
                GlassCircle(
                    onClick = onMenu,
                    size = BarMetrics.pill,
                    contentDescription = "Menu",
                    modifier = Modifier.onGloballyPositioned { chrome.menuRect = it.boundsInRoot() },
                ) {
                    Icon(PaneIcons.More, null, tint = PaneTheme.colors.label, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

/** What the bar says instead of the page while private tabs are locked. */
private const val LOCKED_LABEL = "Private Tabs Locked"

/**
 * The address pill: the site's host, centred, with loading shown as a soft fill that sweeps across
 * the glass. A small lock marks a secure page, a dot marks one that isn't; reload and stop sit at
 * the end. [locked] shows none of the page.
 */
@Composable
fun AddressPill(
    tab: TabState?,
    private: Boolean,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    lifted: Boolean = true,
    onClick: (() -> Unit)? = null,
    onReload: () -> Unit = {},
    onStop: () -> Unit = {},
    onSiteInfo: () -> Unit = {},
) {
    val colors = PaneTheme.colors
    // Both are read in the draw phase below, so loading never recomposes the pill per frame.
    val progress = animateFloatAsState(
        targetValue = if (tab?.loading == true && !locked) (tab.progress / 100f).coerceIn(0.08f, 1f) else 1f,
        animationSpec = Motion.smooth(),
        label = "progress",
    )
    val progressAlpha = animateFloatAsState(if (tab?.loading == true && !locked) 1f else 0f, Motion.fade(400), label = "progressAlpha")
    val host = if (locked) "" else tab?.let { UrlDisplay.toolbarText(it.url) }.orEmpty()
    val loaded = !locked && tab != null && tab.url.isNotEmpty()
    Box(
        modifier
            .fillMaxWidth()
            .height(BarMetrics.pill)
            .then(
                if (onClick != null) {
                    Modifier.pressScale(pressedScale = 0.975f, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .glass(PaneShapes.pill, GlassStrength.Regular, lifted = lifted)
            .semantics { contentDescription = if (locked) LOCKED_LABEL else "Address: $host" },
    ) {
        // Loading sweeps a soft fill across the glass.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val a = progressAlpha.value
                    if (a > 0.01f) drawRect(colors.fill, size = Size(size.width * progress.value, size.height), alpha = a)
                },
        )
        Row(
            Modifier.fillMaxSize().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Leading: the security mark, only where it means something.
            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                when {
                    locked -> Icon(PaneIcons.LockFill, null, tint = colors.tertiaryLabel, modifier = Modifier.size(12.dp))
                    !loaded -> Unit
                    tab.security == SecurityState.Insecure || tab.security == SecurityState.Broken -> Box(
                        Modifier
                            .size(34.dp)
                            .pressDim(onClick = onSiteInfo),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(7.dp).clip(PaneShapes.pill).background(colors.warning))
                    }
                    else -> Icon(
                        PaneIcons.LockFill,
                        contentDescription = "Site information",
                        tint = colors.tertiaryLabel,
                        modifier = Modifier.size(12.dp).pressDim(onClick = onSiteInfo),
                    )
                }
            }
            AnimatedContent(
                targetState = if (locked) LOCKED_LABEL else host,
                transitionSpec = {
                    (slideInVertically(Motion.smooth()) { it / 2 } + fadeIn(Motion.fade(160)))
                        .togetherWith(slideOutVertically(Motion.smooth()) { -it / 2 } + fadeOut(Motion.fade(120)))
                },
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
                label = "host",
            ) { text ->
                Text(
                    text = text.ifEmpty { if (private) "Search privately" else "Search or enter address" },
                    style = PaneTheme.type.body.copy(
                        fontSize = 15.sp,
                        fontWeight = if (text.isEmpty()) FontWeight.Normal else FontWeight.Medium,
                    ),
                    color = if (text.isEmpty()) colors.secondaryLabel else colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                if (loaded) {
                    AnimatedContent(
                        targetState = tab.loading,
                        transitionSpec = { (scaleIn(Motion.snappy(), 0.5f) + fadeIn(Motion.fade(120))).togetherWith(scaleOut(Motion.snappy(), 0.5f) + fadeOut(Motion.fade(100))) },
                        label = "reloadStop",
                    ) { loading ->
                        if (loading) {
                            Icon(PaneIcons.Stop, "Stop", tint = colors.secondaryLabel, modifier = Modifier.size(16.dp).pressDim(onClick = onStop))
                        } else {
                            Icon(PaneIcons.Reload, "Reload", tint = colors.secondaryLabel, modifier = Modifier.size(17.dp).pressDim(onClick = onReload))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewTabPill(modifier: Modifier) {
    val colors = PaneTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(BarMetrics.pill)
            .glass(PaneShapes.pill, GlassStrength.Regular, lifted = false),
        contentAlignment = Alignment.Center,
    ) {
        Text("New tab", style = PaneTheme.type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), color = colors.secondaryLabel)
    }
}

/** The tab count in a rounded square, like a stack of pages. Long-press opens a new tab. */
@Composable
private fun TabsButton(count: Int, onClick: () -> Unit, onLongClick: () -> Unit) {
    val colors = PaneTheme.colors
    GlassCircle(onClick = onClick, size = BarMetrics.pill, contentDescription = "$count tabs", onLongClick = onLongClick) {
        Box(
            Modifier
                .size(24.dp)
                .border(1.7.dp, colors.label, PaneShapes.small),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically(Motion.bouncy()) { if (up) it else -it } + fadeIn(Motion.fade(120)))
                        .togetherWith(slideOutVertically(Motion.smooth()) { if (up) -it else it } + fadeOut(Motion.fade(100)))
                },
                label = "tabCount",
            ) { n ->
                Text(
                    if (n > 99) ":)" else n.toString(),
                    style = PaneTheme.type.caption2.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                    color = colors.label,
                )
            }
        }
    }
}
