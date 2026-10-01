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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.EdgeFade
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.canScroll
import app.pane.browser.ui.theme.stretch

private val BarHeight = 56.dp

/**
 * A pushed screen with a large title that collapses as content scrolls. There is no bar and no
 * button chrome: the page is plain, content dissolves into the top and bottom edges, back is a
 * bare chevron (with the previous screen's name beside it), and a small centred title takes over
 * from the large one once it has scrolled away. Exactly one of the two titles shows at any scroll
 * position. Everything that moves is read in draw-phase lambdas, so scrolling never recomposes
 * the bar.
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

    Box(modifier.fillMaxSize().background(colors.background)) {
        // The list itself fills the screen and keeps the platform's stretch overscroll, also when it
        // is too short to scroll. The edges and the bar below only draw; touches fall through them.
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().stretch(),
            userScrollEnabled = listState.canScroll,
            contentPadding = PaddingValues(top = statusTop + BarHeight, bottom = navBottom + 32.dp),
        ) {
            item(key = "__large_title") {
                Text(
                    title,
                    style = PaneTheme.type.largeTitle.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = RowMargin, end = RowMargin, top = 6.dp, bottom = 4.dp)
                        .graphicsLayer {
                            // Gone by the halfway point, before the small title starts to appear.
                            alpha = (1f - collapse * 2f).coerceIn(0f, 1f)
                            val s = 1f - 0.06f * collapse
                            scaleX = s
                            scaleY = s
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        },
                )
            }
            if (header != null) item(key = "__header") { header() }
            content()
        }

        // Content dissolves into the edges instead of being cut off by a bar.
        EdgeFade(top = true, height = statusTop + BarHeight, modifier = Modifier.align(Alignment.TopCenter))
        EdgeFade(top = false, height = navBottom + 28.dp, modifier = Modifier.align(Alignment.BottomCenter))

        Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(statusTop))
            Box(Modifier.fillMaxWidth().height(BarHeight)) {
                if (onBack != null) {
                    BackButton(
                        label = backLabel,
                        onBack = onBack,
                        fade = { (1f - collapse * 2f).coerceIn(0f, 1f) },
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
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

/**
 * A bare chevron, with the name of the screen it returns to beside it when there is one. No
 * circle, no fill: it dims while pressed. The name fades out as the small title arrives.
 */
@Composable
private fun BackButton(label: String, onBack: () -> Unit, fade: () -> Float, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    val named = label != "Back"
    Row(
        modifier
            .padding(start = 8.dp)
            .height(48.dp)
            .semantics { contentDescription = if (named) "Back to $label" else "Back" }
            .pressDim(onClick = onBack)
            .padding(start = 4.dp, end = if (named) 12.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PaneIcons.Back, contentDescription = null, tint = colors.label, modifier = Modifier.size(24.dp))
        if (named) {
            Text(
                label,
                style = PaneTheme.type.body,
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 120.dp)
                    .graphicsLayer { alpha = fade() },
            )
        }
    }
}

/** Standard vertical breathing room between sections at the end of a list. */
fun LazyListScope.bottomSpacer() {
    item { Spacer(Modifier.height(24.dp).windowInsetsPadding(WindowInsets.navigationBars)) }
}
