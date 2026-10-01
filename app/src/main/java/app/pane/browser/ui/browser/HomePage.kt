package app.pane.browser.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.DotText
import app.pane.browser.ui.components.SectionLabel
import app.pane.browser.ui.components.Separator
import app.pane.browser.ui.components.StatusDot
import app.pane.browser.ui.components.pressDim
import app.pane.browser.ui.components.pressScale
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.entrance
import app.pane.core.url.UrlDisplay

/**
 * The start page for new tabs: paper, the wordmark in dots, one quiet line, an outlined search
 * field, favourites as round marks, recently closed tabs as plain rows and, at the foot, one big
 * dot-matrix number. No cards. Each piece arrives after the one before it. Tapping the field calls
 * [onSearch] (the address editor). Private tabs get black ink, one sentence and the same field.
 */
@Composable
fun HomePage(
    private: Boolean,
    contentPadding: PaddingValues,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {},
) {
    val colors = PaneTheme.colors
    BoxWithConstraints(modifier.fillMaxSize().background(colors.background)) {
        // The wordmark sits about an eighth of the way down: upper third, never cramped or lost.
        val top = minOf(132.dp, maxOf(32.dp, maxHeight * 0.13f))
        val minContent = (maxHeight - contentPadding.calculateTopPadding() - contentPadding.calculateBottomPadding()).coerceAtLeast(0.dp)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().heightIn(min = minContent)) {
                Spacer(Modifier.height(top))
                if (private) PrivateStart(onSearch) else NormalStart(onOpen, onSearch)
            }
        }
    }
}

@Composable
private fun ColumnScope.NormalStart(onOpen: (String) -> Unit, onSearch: () -> Unit) {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val colors = PaneTheme.colors
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val sites = rememberFavoriteSites(limit = 8)
    val state by container.store.state.collectAsStateWithLifecycle()

    DotText("PANE", Modifier.entrance(0), dot = 5.5.dp)
    Spacer(Modifier.height(40.dp))
    HomeSearchField("Search or enter address", onSearch, Modifier.entrance(2))

    // Everything below takes its place in one sequence, capped by `entrance` itself.
    var next = 3
    if (settings.showHomeFavorites && sites.isNotEmpty()) {
        Spacer(Modifier.height(44.dp))
        SectionLabel("Favorites", Modifier.entrance(next).padding(bottom = 16.dp))
        FavoritesGrid(sites, onOpen, indexOffset = next + 1)
        next += 1 + sites.size
    }

    val closed = state.recentlyClosed.filter { it.tab.url.isNotBlank() }.take(3)
    if (closed.isNotEmpty()) {
        Spacer(Modifier.height(40.dp))
        SectionLabel("Recently closed", Modifier.entrance(next).padding(bottom = 6.dp))
        Column(Modifier.entrance(next + 1).fillMaxWidth()) {
            closed.forEachIndexed { i, c ->
                if (i > 0) Separator()
                ClosedRow(c.tab.url, c.tab.title, onClick = { onOpen(c.tab.url) })
            }
        }
        next += 2
    }

    Spacer(Modifier.height(24.dp))
}

@Composable
private fun PrivateStart(onSearch: () -> Unit) {
    val colors = PaneTheme.colors
    Row(Modifier.entrance(0), verticalAlignment = Alignment.Top) {
        DotText("PRIVATE", dot = 4.dp)
        Spacer(Modifier.width(12.dp))
        StatusDot(Modifier.padding(top = 2.dp), size = 8.dp)
    }
    Spacer(Modifier.height(14.dp))
    Text(
        "Nothing is saved when you close these tabs.",
        style = PaneTheme.type.body,
        color = colors.secondaryLabel,
        modifier = Modifier.fillMaxWidth().entrance(1),
    )
    Spacer(Modifier.height(36.dp))
    HomeSearchField("Search privately", onSearch, Modifier.entrance(2))
    Spacer(Modifier.height(24.dp))
}

/** A hairline-outlined pill that reads like a composer; tapping it opens the address editor. It is not itself a field. */
@Composable
private fun HomeSearchField(placeholder: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .pressScale(pressedScale = 0.98f, haptic = true, onClick = onClick)
            .clip(PaneShapes.pill)
            .border(1.dp, colors.label.copy(alpha = 0.85f), PaneShapes.pill)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(placeholder, style = PaneTheme.type.body, color = colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A closed tab as a plain row: its title and the host beneath. */
@Composable
private fun ClosedRow(url: String, title: String, onClick: () -> Unit) {
    val colors = PaneTheme.colors
    val host = UrlDisplay.toolbarText(url)
    Column(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .pressDim(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            title.ifBlank { host },
            style = PaneTheme.type.body,
            color = colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (title.isNotBlank() && host != title) {
            Text(host, style = PaneTheme.type.footnote, color = colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
