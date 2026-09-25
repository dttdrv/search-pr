package app.pane.browser.ui.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import app.pane.browser.ui.components.ChromeButton
import app.pane.browser.ui.components.pressDim
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.ContinuousRoundedShape
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.rememberHaptics
import app.pane.core.tabs.SecurityState
import app.pane.core.tabs.TabState
import app.pane.core.url.UrlDisplay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Heights of the bottom chrome, excluding the navigation bar inset. */
object BarMetrics {
    val expanded: Dp = 60.dp
    val collapsed: Dp = 24.dp
    val dynamic: Dp get() = expanded - collapsed
}

/**
 * Safari-style bottom bar. The address pill shrinks into a slim host label as the page scrolls,
 * swipes sideways to move between tabs (past the last tab it opens a new one), and a swipe up
 * opens the tab overview.
 */
@Composable
fun BottomBar(
    tab: TabState?,
    tabs: List<TabState>,
    chrome: BrowserChrome,
    navBarHeight: Dp,
    onAddress: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onTabs: () -> Unit,
    onNewTab: () -> Unit,
    onMenu: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onReader: () -> Unit,
    onSiteInfo: () -> Unit,
    onSwipeCommit: (targetTabId: String?) -> Unit,
    onSwipeStart: (neighborId: String?) -> Unit,
    onSwipeCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PaneTheme.colors
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val c = chrome.collapse.value
    val barHeight = BarMetrics.collapsed + BarMetrics.dynamic * (1f - c)

    val index = tabs.indexOfFirst { it.id == tab?.id }
    val previous = tabs.getOrNull(index - 1)
    val next = tabs.getOrNull(index + 1)

    val swipe = remember { Animatable(0f) }
    val thresholdPassed = remember { booleanArrayOf(false) }

    Box(
        modifier
            .fillMaxWidth()
            .height(barHeight + navBarHeight)
            .background(colors.chrome)
            .drawBehind {
                drawLine(colors.chromeBorder, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 0.5.dp.toPx())
            }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { },
                onDragStopped = { velocity -> if (velocity < -600f) onTabs() },
            ),
    ) {
        // Collapsed: just the host, tap to bring the bar back.
        if (c > 0.02f) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(BarMetrics.collapsed)
                    .graphicsLayer { alpha = ((c - 0.4f) / 0.6f).coerceIn(0f, 1f) }
                    .pressDim { chrome.expand() },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (tab?.security == SecurityState.Secure) {
                        Icon(PaneIcons.LockFill, null, tint = colors.secondaryLabel, modifier = Modifier.size(10.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        tab?.let { UrlDisplay.toolbarText(it.url) }.orEmpty(),
                        style = PaneTheme.type.caption.copy(fontWeight = FontWeight.Medium),
                        color = colors.label,
                        maxLines = 1,
                    )
                }
            }
        }

        // Expanded row.
        val expandedAlpha = (1f - c * 1.8f).coerceIn(0f, 1f)
        if (expandedAlpha > 0f) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .padding(horizontal = 4.dp)
                    .graphicsLayer {
                        alpha = expandedAlpha
                        val s = 1f - 0.12f * c
                        scaleX = s
                        scaleY = s
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChromeButton(PaneIcons.Back, "Back", onBack, enabled = tab?.canGoBack == true || tab?.parentId != null, tint = colors.label)
                AnimatedVisibility(
                    visible = tab?.canGoForward == true,
                    enter = expandHorizontally(Motion.snappy()) + fadeIn(Motion.fade()),
                    exit = shrinkHorizontally(Motion.snappy()) + fadeOut(Motion.fade()),
                ) {
                    ChromeButton(PaneIcons.Forward, "Forward", onForward, tint = colors.label)
                }
                BoxWithConstraints(Modifier.weight(1f).height(44.dp)) {
                    val width = constraints.maxWidth.toFloat()
                    val stride = width + with(density) { 12.dp.toPx() }
                    val fraction = swipe.value / stride
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
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .draggable(
                                orientation = Orientation.Horizontal,
                                state = dragState,
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
                    ) {
                        // Neighbours slide in alongside the current pill.
                        if (fraction > 0f && previous != null) {
                            AddressPill(previous, Modifier.offset { IntOffset((swipe.value - stride).roundToInt(), 0) }, collapse = 0f)
                        }
                        if (fraction < 0f) {
                            if (next != null) {
                                AddressPill(next, Modifier.offset { IntOffset((swipe.value + stride).roundToInt(), 0) }, collapse = 0f)
                            } else {
                                NewTabPill(Modifier.offset { IntOffset((swipe.value + stride).roundToInt(), 0) })
                            }
                        }
                        AddressPill(
                            tab = tab,
                            collapse = c,
                            modifier = Modifier.offset { IntOffset(swipe.value.roundToInt(), 0) },
                            onClick = onAddress,
                            onReload = onReload,
                            onStop = onStop,
                            onReader = onReader,
                            onSiteInfo = onSiteInfo,
                        )
                    }
                }
                TabsButton(count = tabs.size, onClick = onTabs, onLongClick = onNewTab)
                ChromeButton(PaneIcons.More, "Menu", onMenu, tint = colors.label)
            }
        }
    }
}

/** The rounded address field: security glyph, host, and reload/stop. */
@Composable
fun AddressPill(
    tab: TabState?,
    modifier: Modifier = Modifier,
    collapse: Float = 0f,
    onClick: (() -> Unit)? = null,
    onReload: () -> Unit = {},
    onStop: () -> Unit = {},
    onReader: () -> Unit = {},
    onSiteInfo: () -> Unit = {},
) {
    val colors = PaneTheme.colors
    val shape = ContinuousRoundedShape(14.dp)
    val progress by animateFloatAsState(
        targetValue = if (tab?.loading == true) (tab.progress / 100f).coerceIn(0.05f, 1f) else 1f,
        animationSpec = Motion.smooth(),
        label = "progress",
    )
    val progressAlpha by animateFloatAsState(if (tab?.loading == true) 1f else 0f, Motion.fade(350), label = "progressAlpha")
    val host = tab?.let { UrlDisplay.toolbarText(it.url) }.orEmpty()
    Box(
        modifier
            .fillMaxWidth()
            .height(lerp(44.dp, 24.dp, collapse))
            .clip(shape)
            .background(colors.fill.copy(alpha = colors.fill.alpha * (1f - collapse)))
            .then(
                if (onClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .semantics { contentDescription = "Address: $host" },
    ) {
        Row(
            Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Leading: reader mode when available, otherwise the security state.
            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                when {
                    tab == null || tab.url.isEmpty() -> Icon(PaneIcons.Search, null, tint = colors.secondaryLabel, modifier = Modifier.size(17.dp))
                    tab.readerable || tab.inReaderMode -> Icon(
                        PaneIcons.Reader,
                        contentDescription = "Reader view",
                        tint = if (tab.inReaderMode) colors.accent else colors.label,
                        modifier = Modifier.size(19.dp).pressDim(onClick = onReader),
                    )
                    tab.security == SecurityState.Insecure || tab.security == SecurityState.Broken -> Icon(
                        PaneIcons.Warning,
                        contentDescription = "Not secure",
                        tint = colors.warning,
                        modifier = Modifier.size(17.dp).pressDim(onClick = onSiteInfo),
                    )
                    else -> Icon(
                        PaneIcons.LockFill,
                        contentDescription = "Site information",
                        tint = colors.secondaryLabel,
                        modifier = Modifier.size(14.dp).pressDim(onClick = onSiteInfo),
                    )
                }
            }
            Text(
                text = host.ifEmpty { "Search or enter website" },
                style = PaneTheme.type.body.copy(
                    fontSize = lerp(15.sp, 12.sp, collapse),
                    letterSpacing = (-0.01).em,
                    fontWeight = if (host.isEmpty()) FontWeight.Normal else FontWeight.Medium,
                ),
                color = if (host.isEmpty()) colors.secondaryLabel else colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                if (tab != null && tab.url.isNotEmpty()) {
                    if (tab.loading) {
                        Icon(PaneIcons.Stop, "Stop", tint = colors.label, modifier = Modifier.size(17.dp).pressDim(onClick = onStop))
                    } else {
                        Icon(PaneIcons.Reload, "Reload", tint = colors.label, modifier = Modifier.size(18.dp).pressDim(onClick = onReload))
                    }
                }
            }
        }
        // Loading progress hugs the bottom edge of the pill.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(progress)
                .height(2.5.dp)
                .graphicsLayer { alpha = progressAlpha * (1f - collapse) }
                .background(colors.accent),
        )
    }
}

@Composable
private fun NewTabPill(modifier: Modifier) {
    val colors = PaneTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(ContinuousRoundedShape(14.dp))
            .background(colors.fill),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(PaneIcons.Plus, null, tint = colors.secondaryLabel, modifier = Modifier.size(16.dp))
            Text("New Tab", style = PaneTheme.type.body, color = colors.secondaryLabel)
        }
    }
}

/** Tab count in a rounded square, like a stack of pages. Long-press opens a new tab. */
@Composable
private fun TabsButton(count: Int, onClick: () -> Unit, onLongClick: () -> Unit) {
    val colors = PaneTheme.colors
    val haptics = rememberHaptics()
    Box(
        Modifier
            .size(44.dp)
            .semantics { contentDescription = "$count tabs" }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClick = {
                    haptics.longPress()
                    onLongClick()
                },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .border(1.8.dp, colors.label, ContinuousRoundedShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (count > 99) ":)" else count.toString(),
                style = PaneTheme.type.caption2.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                color = colors.label,
            )
        }
    }
}

