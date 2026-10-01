package app.pane.browser.ui.browser

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
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
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import app.pane.browser.ui.components.StatusDot
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import app.pane.browser.ui.components.FloatingCircle
import app.pane.browser.ui.components.pressDim
import app.pane.browser.ui.components.pressScale
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.LocalReduceMotion
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
import kotlin.math.max
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
 * page morphs the bar into a small host label: the round buttons tuck into the pill and the pill
 * shrinks around the host; tapping that brings it back. While private tabs are [locked] it names no
 * page and doesn't swipe.
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
    val reduce = LocalReduceMotion.current
    // every part of the bar's morph is a function of this one progress, read in layout and layer
    // lambdas, so scrolling never recomposes the bar; reduced motion swaps the two ends instead
    val morph = { collapse.value.let { if (!reduce) it else if (it < LABEL) 0f else 1f } }
    // whole elements change only at these marks
    val label by remember { derivedStateOf { collapse.value >= LABEL } }
    val folded by remember { derivedStateOf { collapse.value >= 1f } }

    val index = tabs.indexOfFirst { it.id == tab?.id }
    val previous = tabs.getOrNull(index - 1)
    val next = tabs.getOrNull(index + 1)

    // The finger-driven offset of the pill, a plain float so dragging writes it directly (a coroutine
    // per drag event would read stale values and drop deltas); the settle animation writes it too.
    var offset by remember { mutableFloatStateOf(0f) }
    val swipeJob = remember { arrayOfNulls<Job>(1) }
    val canGoBack = tab?.canGoBack == true || tab?.parentId != null
    // back comes out of the pill when there is somewhere to go, and tucks back into it when there isn't
    val back = animateFloatAsState(if (canGoBack) 1f else 0f, if (reduce) snap() else Motion.smooth(), label = "back")
    val backShown by remember { derivedStateOf { back.value > 0f } }

    Layout(
        modifier = modifier.fillMaxWidth().padding(bottom = navBarHeight),
        content = {
            BoxWithConstraints(
                // Neighbouring tabs slide in from the sides, so clip there but leave room for shadows.
                Modifier.drawWithContent {
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
                // overview. Two nested draggables fought over every diagonal touch. The label has its
                // own flick and leaves the rest of the strip to the page; so do the gaps above and below the pill.
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(vertical = BarMetrics.gap)
                        .then(if (label) Modifier else Modifier.pointerInput(stride) {
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
                        }),
                    // the pill keeps its bottom edge as it shrinks into the label
                    contentAlignment = Alignment.BottomCenter,
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
                            // the open pill is where the editor grows from
                            .onGloballyPositioned { if (morph() == 0f) chrome.pillRect = it.boundsInRoot() }
                            .then(if (!label) Modifier else Modifier.pointerInput(locked, previous?.id, next?.id) {
                                // A flick sideways works on the label as well: next tab, or a new one.
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
                            }),
                        locked = locked,
                        morph = morph,
                        onClick = if (label) chrome::expand else onAddress,
                        onReload = onReload,
                        onStop = onStop,
                        onSiteInfo = onSiteInfo,
                    )
                }
            }
            // drawn under the pill, so they tuck in behind it
            if (!folded) {
                FloatingCircle(onClick = onMenu, size = BarMetrics.pill, contentDescription = "Menu") {
                    Icon(PaneIcons.More, null, tint = PaneTheme.colors.label, modifier = Modifier.size(22.dp))
                }
                if (backShown) {
                    FloatingCircle(onClick = onBack, size = BarMetrics.pill, contentDescription = "Back") {
                        Icon(PaneIcons.Back, null, tint = PaneTheme.colors.label, modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val gap = BarMetrics.gap.roundToPx()
        val circle = BarMetrics.pill.roundToPx()
        val zone = BarMetrics.zone.roundToPx()
        val width = constraints.maxWidth
        // the pill's slot between the round buttons; back's place opens as it comes out
        val start = gap + (back.value * (circle + gap)).roundToInt()
        val end = width - 2 * gap - circle
        val slot = measurables[0].measure(Constraints.fixed(end - start, zone))
        val round = Constraints.fixed(circle, circle)
        val menu = measurables.getOrNull(1)?.measure(round)
        val backButton = measurables.getOrNull(2)?.measure(round)
        layout(width, zone) {
            val p = morph()
            // the pill's centre travels from its slot to the middle; its bottom edge stays put
            val x = lerp((start + end) / 2f, width / 2f, p)
            val y = zone - gap - lerp(BarMetrics.pill, BarMetrics.mini, p).toPx() / 2
            // a round button rides the pill's axis and shrinks into its centre as [t] goes to 1, placed
            // first so it's drawn behind
            fun tuck(button: Placeable?, home: Float, t: Float) = button?.placeWithLayer(
                (lerp(home, x, t) - circle / 2f).roundToInt(),
                (y - circle / 2f).roundToInt(),
            ) {
                scaleX = 1f - t
                scaleY = 1f - t
            }
            tuck(menu, width - gap - circle / 2f, p)
            tuck(backButton, gap + circle / 2f, max(p, 1f - back.value))
            slot.place((x - slot.width / 2f).roundToInt(), 0)
        }
    }
}

/**
 * stages of the collapse morph, as fractions of [BrowserChrome.collapse]: the pill's size, its travel
 * to the middle, the host's size and the round buttons' tuck run the whole way; the padlock and reload
 * have faded by [DETAILS_GONE], before the shrinking ends could reach the host; from [LABEL] on the bar
 * is its label (a tap expands it, a flick switches tabs), and reduced motion swaps the two ends there.
 */
private const val DETAILS_GONE = 0.3f
private const val LABEL = 0.5f

/** What the bar says instead of the page while private tabs are locked. */
private const val LOCKED_LABEL = "Private Tabs Locked"

/**
 * The address pill: the site's host, centred, with loading shown as a soft fill that sweeps across
 * the glass. A small lock marks a secure page, a dot marks one that isn't; reload and stop sit at
 * the end. [locked] shows none of the page. As [morph] goes to 1 the pill becomes the collapsed bar's
 * label: it shrinks around the host, the host shrinks from body to caption size, the ends fade.
 */
@Composable
fun AddressPill(
    tab: TabState?,
    private: Boolean,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    morph: () -> Float = { 0f },
    onClick: (() -> Unit)? = null,
    onReload: () -> Unit = {},
    onStop: () -> Unit = {},
    onSiteInfo: () -> Unit = {},
) {
    val colors = PaneTheme.colors
    val type = PaneTheme.type
    // Both are read in the draw phase below, so loading never recomposes the pill per frame.
    val progress = animateFloatAsState(
        targetValue = if (tab?.loading == true && !locked) (tab.progress / 100f).coerceIn(0.08f, 1f) else 1f,
        animationSpec = Motion.smooth(),
        label = "progress",
    )
    val progressAlpha = animateFloatAsState(if (tab?.loading == true && !locked) 1f else 0f, Motion.fade(400), label = "progressAlpha")
    val host = if (locked) "" else tab?.let { UrlDisplay.toolbarText(it.url) }.orEmpty()
    val loaded = !locked && tab != null && tab.url.isNotEmpty()
    // the label sets the host in caption where the pill sets it in body
    val small = type.caption.fontSize.value / type.body.fontSize.value
    Layout(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.pressScale(pressedScale = 0.975f, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .frosted(PaneShapes.pill)
            // Loading draws a thin ink line along the bottom of the pill, growing with the page.
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
            }
            .semantics { contentDescription = if (locked) LOCKED_LABEL else "Address: $host" },
        content = {
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
                // the host's width follows the text, and a longer host isn't cut off while it grows
                transitionSpec = {
                    (slideInVertically(Motion.smooth()) { it / 2 } + fadeIn(Motion.fade(160)))
                        .togetherWith(slideOutVertically(Motion.smooth()) { -it / 2 } + fadeOut(Motion.fade(120)))
                        .using(SizeTransform(clip = false))
                },
                contentAlignment = Alignment.Center,
                label = "host",
            ) { text ->
                Text(
                    text = text.ifEmpty { if (private) "Search privately" else "Search or enter address" },
                    style = type.body.copy(fontWeight = if (text.isEmpty()) FontWeight.Normal else FontWeight.Medium),
                    color = if (text.isEmpty()) colors.secondaryLabel else colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
        },
    ) { (lead, text, trail), constraints ->
        val p = morph()
        val inset = 6.dp.roundToPx()
        val leading = lead.measure(Constraints())
        val trailing = trail.measure(Constraints())
        // measured once against the open pill, so the host never re-wraps as the pill shrinks around it
        val hostLine = text.measure(Constraints(maxWidth = (constraints.maxWidth - 2 * inset - leading.width - trailing.width).coerceAtLeast(0)))
        val scale = lerp(1f, small, p)
        val width = lerp(constraints.maxWidth.toFloat(), hostLine.width * small + BarMetrics.mini.toPx(), p).roundToInt()
        val height = lerp(BarMetrics.pill, BarMetrics.mini, p).roundToPx()
        layout(width, height) {
            // the ends ride the pill's edges and fade; gone, they take no touches
            val shown = 1f - p / DETAILS_GONE
            if (shown > 0f) {
                leading.placeWithLayer(inset, (height - leading.height) / 2) { alpha = shown }
                trailing.placeWithLayer(width - inset - trailing.width, (height - trailing.height) / 2) { alpha = shown }
            }
            hostLine.placeWithLayer((width - hostLine.width) / 2, (height - hostLine.height) / 2) {
                scaleX = scale
                scaleY = scale
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