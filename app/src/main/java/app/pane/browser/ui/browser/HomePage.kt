package app.pane.browser.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.Separator
import app.pane.browser.ui.components.SiteIcon
import app.pane.browser.ui.components.pressDim
import app.pane.browser.ui.components.pressScale
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.entrance
import app.pane.core.url.UrlDisplay
import java.text.NumberFormat

/**
 * The start page for new tabs: paper, a large wordmark in the upper third, a search field that
 * looks like a composer, favourites as icons, recently closed tabs as quiet rows and one line
 * about tracking. Each piece arrives after the one before it. Tapping the field calls [onSearch]
 * (the address editor). Private tabs get black ink, one sentence and the same field.
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
        // The wordmark sits about a seventh of the way down: upper third, never cramped or lost.
        val top = minOf(132.dp, maxOf(32.dp, maxHeight * 0.13f))
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                Spacer(Modifier.height(top))
                if (private) PrivateStart(onSearch) else NormalStart(onOpen, onSearch)
            }
        }
    }
}

@Composable
private fun NormalStart(onOpen: (String) -> Unit, onSearch: () -> Unit) {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val colors = PaneTheme.colors
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val sites = rememberFavoriteSites(limit = 8)
    val blocked by container.privacyStats.weekTotal.collectAsStateWithLifecycle()
    val state by container.store.state.collectAsStateWithLifecycle()

    Wordmark("Pane", Modifier.entrance(0))
    Spacer(Modifier.height(28.dp))
    HomeSearchField("Search or enter address", onSearch, Modifier.entrance(1))

    // Everything below takes its place in one sequence, capped by `entrance` itself.
    var next = 2
    if (settings.showHomeFavorites && sites.isNotEmpty()) {
        Spacer(Modifier.height(44.dp))
        SectionLabel("Favorites", Modifier.entrance(next))
        FavoritesGrid(sites, onOpen, indexOffset = next + 1)
        next += 1 + sites.size
    }

    val closed = state.recentlyClosed.filter { it.tab.url.isNotBlank() }.take(3)
    if (closed.isNotEmpty()) {
        Spacer(Modifier.height(40.dp))
        SectionLabel("Recently closed", Modifier.entrance(next))
        Column(
            Modifier
                .entrance(next + 1)
                .fillMaxWidth()
                .clip(PaneShapes.card)
                .background(colors.surface),
        ) {
            closed.forEachIndexed { i, c ->
                ClosedRow(c.tab.url, c.tab.title, onClick = { onOpen(c.tab.url) })
                if (i < closed.lastIndex) Separator(Modifier.padding(start = 56.dp))
            }
        }
        next += 2
    }

    Spacer(Modifier.height(36.dp))
    Box(
        Modifier
            .entrance(next)
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .pressDim { navigator.push(Route.PrivacySettings) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${NumberFormat.getIntegerInstance().format(blocked)} ${if (blocked == 1) "tracker" else "trackers"} blocked this week",
            style = PaneTheme.type.footnote,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun PrivateStart(onSearch: () -> Unit) {
    val colors = PaneTheme.colors
    Wordmark("Private", Modifier.entrance(0))
    Spacer(Modifier.height(12.dp))
    Text(
        "Nothing is saved once you close these tabs.",
        style = PaneTheme.type.body,
        color = colors.secondaryLabel,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().entrance(1),
    )
    Spacer(Modifier.height(32.dp))
    HomeSearchField("Search privately", onSearch, Modifier.entrance(2))
    Spacer(Modifier.height(24.dp))
}

/** The big name at the top of the page. */
@Composable
private fun Wordmark(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = PaneTheme.type.largeTitle.copy(
            fontSize = 44.sp,
            lineHeight = 52.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.035).em,
        ),
        color = PaneTheme.colors.label,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier.fillMaxWidth(),
    )
}

/** A capsule that reads like a composer; tapping it opens the address editor. It is not itself a field. */
@Composable
private fun HomeSearchField(placeholder: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .pressScale(pressedScale = 0.98f, haptic = true, onClick = onClick)
            .clip(PaneShapes.pill)
            .background(colors.fill)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(placeholder, style = PaneTheme.type.body, color = colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = PaneTheme.type.footnote,
        fontWeight = FontWeight.SemiBold,
        color = PaneTheme.colors.secondaryLabel,
        modifier = modifier.padding(start = 4.dp, bottom = 14.dp),
    )
}

/** A closed tab as a quiet row: its icon, its title and the host beneath. */
@Composable
private fun ClosedRow(url: String, title: String, onClick: () -> Unit) {
    val colors = PaneTheme.colors
    val host = UrlDisplay.toolbarText(url)
    Row(
        Modifier
            .fillMaxWidth()
            .pressDim(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SiteIcon(url, 28.dp)
        Column(Modifier.weight(1f)) {
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
}
