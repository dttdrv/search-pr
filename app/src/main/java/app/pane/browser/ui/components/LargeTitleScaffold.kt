package app.pane.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.GlassStrength
import app.pane.browser.ui.theme.LocalHazeState
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.ProgressiveEdge
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private val BarHeight = 56.dp
private val BackSize = 48.dp

/**
 * A pushed screen with a large title that collapses as content scrolls. There is no bar: content
 * dissolves into the background through a progressive blur at the top and bottom edges, back is a
 * small frosted circle floating over that blur, and the small title takes over from the large one
 * once it has scrolled away. Exactly one of the two titles shows at any scroll position. Everything
 * that moves is read in draw-phase lambdas, so scrolling never recomposes the bar.
 */
@Composable
fun LargeTitleScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    backLabel: String = "Back",
    listState: LazyListState = rememberLazyListState(),
    actions: @Composable RowScope.() -> Unit = {},
    header: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val colors = PaneTheme.colors
    val density = LocalDensity.current
    val haze = rememberHazeState()
    val collapseDistance = with(density) { 44.dp.toPx() }
    // 0 while the large title shows, 1 once it has folded into the bar.
    val collapse by remember(listState, collapseDistance) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / collapseDistance).coerceIn(0f, 1f)
            }
        }
    }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier.fillMaxSize().background(colors.groupedBackground)) {
        // The list itself fills the screen and keeps the platform's stretch overscroll: nothing here
        // sets `overscrollEffect`, clips it, or puts a pointer-consuming layer on top. The edges and
        // the bar below only draw; touches fall through them to the list.
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().hazeSource(haze),
            contentPadding = PaddingValues(top = statusTop + BarHeight, bottom = navBottom + 32.dp),
        ) {
            item(key = "__large_title") {
                Text(
                    title,
                    style = PaneTheme.type.largeTitle,
                    color = colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp)
                        .graphicsLayer {
                            // Gone by the halfway point, before the small title starts to appear.
                            alpha = (1f - collapse * 2f).coerceIn(0f, 1f)
                            val s = 1f - 0.06f * collapse
                            scaleX = s
                            scaleY = s
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        },
                )
            }
            if (header != null) item(key = "__header") { header() }
            content()
        }

        // Content dissolves into the edges instead of being cut off by a bar.
        ProgressiveEdge(haze, top = true, height = statusTop + BarHeight, modifier = Modifier.align(Alignment.TopCenter))
        ProgressiveEdge(haze, top = false, height = navBottom + 28.dp, modifier = Modifier.align(Alignment.BottomCenter))

        Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(statusTop))
            Box(Modifier.fillMaxWidth().height(BarHeight)) {
                if (onBack != null) {
                    CompositionLocalProvider(LocalHazeState provides haze) {
                        GlassCircle(
                            onClick = onBack,
                            size = BackSize,
                            strength = GlassStrength.Thin,
                            contentDescription = if (backLabel == "Back") "Back" else "Back to $backLabel",
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
                        ) {
                            Icon(PaneIcons.Back, contentDescription = null, tint = colors.label, modifier = Modifier.size(22.dp))
                        }
                    }
                }
                Text(
                    title,
                    style = PaneTheme.type.headline,
                    color = colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 72.dp)
                        // Starts once the large title is gone; the two never show together.
                        .graphicsLayer { alpha = ((collapse - 0.5f) * 2f).coerceIn(0f, 1f) },
                )
                Row(
                    Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
    }
}

/** Standard vertical breathing room between sections at the end of a list. */
fun LazyListScope.bottomSpacer() {
    item { Spacer(Modifier.height(24.dp).windowInsetsPadding(WindowInsets.navigationBars)) }
}
