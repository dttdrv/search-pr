package app.pane.browser.ui.browser

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.Job
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.animation.core.animate
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
import androidx.compose.foundation.combinedClickable
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
import app.pane.browser.ui.components.StatusDot
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
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
import app.pane.browser.ui.components.FloatingCircle
import app.pane.browser.ui.components.pressDim
import app.pane.browser.ui.components.pressScale
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.frosted
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

    // The finger-driven offset of the pill, a plain float so dragging writes it directly (a coroutine
    // per drag event would read stale values and drop deltas); the settle animation writes it too.
    var offset by remember { mutableFloatStateOf(0f) }
    val swipeJob = remember { arrayOfNulls<Job>(1) }
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
                    // A flick sideways works on the collapsed label as well: next tab, or a new one.
                    .pointerInput(locked, previous?.id, next?.id) {
                        var sum = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { sum = 0f },
                            onDragEnd = {
                                if (!locked && abs(sum) > 56.dp.toPx()) {
                                    val toNext = sum < 0f
                                    val target = if (toNext) next else previous
                                    if (target != null || toNext) {
                                        haptics.confirm()
                                        chrome.expand()
                                        onSwipeCommit(target?.id)
                                    }
                                }
                            },
                            onHorizontalDrag = { change, delta ->
                                change.consume()
                                sum += delta
                            },
                        )
                    }
                    .pressScale(pressedScale = 0.94f) { chrome.expand() }
                    .frosted(PaneShapes.pill)
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
                    enter = expandHorizontally(Motion.snappy()) + scaleIn(Motion.smooth(), initialScale = 0.5f) + fadeIn(Motion.fade(140)),
                    exit = shrinkHorizontally(Motion.snappy()) + scaleOut(Motion.snappy(), targetScale = 0.5f) + fadeOut(Motion.fade(120)),
                ) {
                    Row {
                        FloatingCircle(onClick = onBack, size = BarMetrics.pill, contentDescription = "Back") {
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
                            if (offset != 0f) {
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
                    val showPrevious by remember { derivedStateOf { offset > 0f } }
                    val showNext by remember { derivedStateOf { offset < 0f } }
                    val currentPrevious by rememberUpdatedState(previous)
                    val currentNext by rememberUpdatedState(next)
                    val currentLocked by rememberUpdatedState(locked)
                    val currentOnTabs by rememberUpdatedState(onTabs)
                    val currentCommit by rememberUpdatedState(onSwipeCommit)
                    val currentStart by rememberUpdatedState(onSwipeStart)
                    val currentCancel by rememberUpdatedState(onSwipeCancel)

                    fun move(value: Float) {
                        offset = value
                        chrome.tabSwipe = (value / stride).coerceIn(-1f, 1f)
                    }

                    // One detector for both directions: sideways swipes the tabs, a flick up opens the
                    // overview. Two nested draggables fought over every diagonal touch.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(stride) {
                                val tracker = VelocityTracker()
                                var axis = 0 // 0 undecided, 1 sideways, 2 vertical
                                var totalX = 0f
                                var totalY = 0f
                                var passed = false
                                var started = false

                                fun finishSideways(vx: Float) {
                                    val flicked = abs(vx) > 900f
                                    val toNext = if (flicked) vx < 0f else offset < 0f
                                    val target = if (toNext) currentNext else currentPrevious
                                    val dragged = abs(offset) > stride * 0.28f && (offset < 0f) == toNext
                                    val commit = (dragged || flicked) && (target != null || toNext)
                                    swipeJob[0]?.cancel()
                                    swipeJob[0] = scope.launch {
                                        if (commit) {
                                            haptics.confirm()
                                            animate(offset, if (toNext) -stride else stride, vx, Motion.snappy()) { v, _ -> move(v) }
                                            currentCommit(target?.id)
                                            move(0f)
                                        } else {
                                            animate(offset, 0f, vx, Motion.smooth()) { v, _ -> move(v) }
                                            currentCancel()
                                        }
                                    }
                                }

                                detectDragGestures(
                                    onDragStart = {
                                        tracker.resetTracking()
                                        axis = 0
                                        totalX = 0f
                                        totalY = 0f
                                        passed = false
                                        started = false
                                        // Catching the pill mid-settle: the finger takes over from where it is.
                                        swipeJob[0]?.cancel()
                                    },
                                    onDragEnd = {
                                        val v = tracker.calculateVelocity()
                                        when {
                                            axis == 1 && started -> finishSideways(v.x)
                                            axis == 2 && (totalY < -48.dp.toPx() || v.y < -600f) -> currentOnTabs()
                                        }
                                    },
                                    onDragCancel = {
                                        if (axis == 1 && started) finishSideways(0f)
                                    },
                                    onDrag = { change, drag ->
                                        tracker.addPosition(change.uptimeMillis, change.position)
                                        totalX += drag.x
                                        totalY += drag.y
                                        if (axis == 0) axis = if (abs(totalX) >= abs(totalY)) 1 else 2
                                        if (axis == 1 && !currentLocked) {
                                            if (!started) {
                                                started = true
                                                currentStart(currentNext?.id ?: currentPrevious?.id)
                                            }
                                            // Past the first tab on the left, the pill pulls against you.
                                            val resisted = if (offset + drag.x > 0f && currentPrevious == null) drag.x * 0.25f else drag.x
                                            move(offset + resisted)
                                            val over = abs(offset) > stride * 0.28f
                                            if (over != passed) {
                                                passed = over
                                                if (over) haptics.tick()
                                            }
                                        }
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        // Neighbours slide in alongside the current pill.
                        if (showPrevious && previous != null) {
                            AddressPill(previous, private, Modifier.offset { IntOffset((offset - stride).roundToInt(), 0) })
                        }
                        if (showNext) {
                            if (next != null) {
                                AddressPill(next, private, Modifier.offset { IntOffset((offset + stride).roundToInt(), 0) })
                            } else {
                                NewTabPill(Modifier.offset { IntOffset((offset + stride).roundToInt(), 0) })
                            }
                        }
                        AddressPill(
                            tab = tab,
                            private = private,
                            modifier = Modifier
                                .offset { IntOffset(offset.roundToInt(), 0) }
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
                FloatingCircle(
                    onClick = onMenu,
                    size = BarMetrics.pill,
                    contentDescription = "Menu",
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
            .frosted(PaneShapes.pill)
            .semantics { contentDescription = if (locked) LOCKED_LABEL else "Address: $host" },
    ) {
        // Loading draws a thin ink line along the bottom of the pill, growing with the page.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val a = progressAlpha.value
                    if (a > 0.01f) {
                        val inset = size.height / 2f
                        val y = size.height - 3.dp.toPx()
                        val span = size.width - inset * 2f
                        drawLine(
                            colors.label,
                            Offset(inset, y),
                            Offset(inset + span * progress.value, y),
                            strokeWidth = 1.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            alpha = a,
                        )
                    }
                },
        )
        Row(
            Modifier.fillMaxSize().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Leading: the security mark, only where it means something.
            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                when {
                    locked -> Icon(PaneIcons.LockFill, null, tint = colors.label, modifier = Modifier.size(12.dp))
                    !loaded -> Unit
                    tab.loading -> StatusDot()
                    tab.security == SecurityState.Insecure || tab.security == SecurityState.Broken -> Box(
                        Modifier
                            .size(34.dp)
                            .pressDim(onClick = onSiteInfo),
                        contentAlignment = Alignment.Center,
                    ) {
                        StatusDot()
                    }
                    else -> Icon(
                        PaneIcons.LockFill,
                        contentDescription = "Site information",
                        tint = colors.label,
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
                            Icon(PaneIcons.Stop, "Stop", tint = colors.label, modifier = Modifier.size(16.dp).pressDim(onClick = onStop))
                        } else {
                            Icon(PaneIcons.Reload, "Reload", tint = colors.label, modifier = Modifier.size(17.dp).pressDim(onClick = onReload))
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
            .frosted(PaneShapes.pill),
        contentAlignment = Alignment.Center,
    ) {
        Text("New tab", style = PaneTheme.type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), color = colors.secondaryLabel)
    }
}