package app.pane.browser.ui.components

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneTheme

/**
 * A UINavigationController screen with a large title that collapses into the bar as content
 * scrolls under it. The bar gains its material and hairline only once something is beneath it.
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
    val collapse by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / collapseDistance).coerceIn(0f, 1f)
        }
    }
    val barMaterial by animateFloatAsState(if (collapse >= 1f) 1f else 0f, Motion.fade(160), label = "bar")
    val statusBar = WindowInsets.statusBars.asPaddingValues()
    val navBar = WindowInsets.navigationBars.asPaddingValues()

    Box(modifier.fillMaxSize().background(colors.groupedBackground)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = statusBar.calculateTopPadding() + 44.dp, bottom = navBar.calculateBottomPadding() + 32.dp),
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
                            alpha = 1f - collapse
                            val s = 1f - 0.08f * collapse
                            scaleX = s
                            scaleY = s
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        },
                )
            }
            if (header != null) item(key = "__header") { header() }
            content()
        }

        // Navigation bar.
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = 1f }
                .background(colors.chrome.copy(alpha = colors.chrome.alpha * barMaterial)),
        ) {
            Spacer(Modifier.height(statusBar.calculateTopPadding()))
            Box(Modifier.fillMaxWidth().height(44.dp)) {
                if (onBack != null) {
                    Row(
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 4.dp)
                            .height(44.dp)
                            .pressDim(onClick = onBack)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(PaneIcons.Back, contentDescription = "Back", tint = colors.accent, modifier = Modifier.size(26.dp))
                        Text(backLabel, style = PaneTheme.type.body, color = colors.accent, maxLines = 1)
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
                        .padding(horizontal = 96.dp)
                        .graphicsLayer { alpha = ((collapse - 0.5f) * 2f).coerceIn(0f, 1f) },
                )
                Row(
                    Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
            Separator(Modifier.graphicsLayer { alpha = barMaterial })
        }
    }
}

/** Standard vertical breathing room between sections at the end of a list. */
fun LazyListScope.bottomSpacer() {
    item { Spacer(Modifier.height(24.dp).windowInsetsPadding(WindowInsets.navigationBars)) }
}
