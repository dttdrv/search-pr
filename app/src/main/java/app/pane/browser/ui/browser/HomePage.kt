package app.pane.browser.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.IconTile
import app.pane.browser.ui.components.pressScale
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.core.url.UrlDisplay
import java.text.NumberFormat

/**
 * The start page for new tabs: favourites, a weekly privacy report and recently closed tabs.
 * Private tabs get a quiet explanation of what private browsing does and doesn't do.
 */
@Composable
fun HomePage(private: Boolean, contentPadding: PaddingValues, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    Box(modifier.fillMaxSize().background(colors.groupedBackground)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                if (private) PrivateIntro() else NormalStart(onOpen)
            }
        }
    }
}

@Composable
private fun NormalStart(onOpen: (String) -> Unit) {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val colors = PaneTheme.colors
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val sites = rememberFavoriteSites()
    val blocked by container.privacyStats.weekTotal.collectAsStateWithLifecycle()
    val state by container.store.state.collectAsStateWithLifecycle()

    Spacer(Modifier.height(28.dp))
    if (settings.showHomeFavorites && sites.isNotEmpty()) {
        SectionTitle("Favorites")
        FavoritesGrid(sites, onOpen)
        Spacer(Modifier.height(30.dp))
    }

    SectionTitle("Privacy Report")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(PaneShapes.card)
            .background(colors.surface)
            .pressScale(pressedScale = 0.98f) { navigator.push(Route.PrivacySettings) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(PaneIcons.ShieldCheck, colors.positive)
        Column(Modifier.weight(1f)) {
            Text(
                NumberFormat.getIntegerInstance().format(blocked),
                style = PaneTheme.type.title2,
                color = colors.label,
            )
            Text(
                if (blocked == 1) "tracker blocked in the last seven days" else "trackers blocked in the last seven days",
                style = PaneTheme.type.footnote,
                color = colors.secondaryLabel,
            )
        }
    }

    val closed = state.recentlyClosed.take(3)
    if (closed.isNotEmpty()) {
        Spacer(Modifier.height(30.dp))
        SectionTitle("Recently Closed")
        Column(Modifier.fillMaxWidth().clip(PaneShapes.card).background(colors.surface)) {
            closed.forEachIndexed { i, c ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressScale(pressedScale = 0.98f) { onOpen(c.tab.url) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Monogram(UrlDisplay.toolbarText(c.tab.url), size = 30)
                    Column(Modifier.weight(1f)) {
                        Text(c.tab.title.ifBlank { UrlDisplay.toolbarText(c.tab.url) }, style = PaneTheme.type.body, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(UrlDisplay.toolbarText(c.tab.url), style = PaneTheme.type.footnote, color = colors.secondaryLabel, maxLines = 1)
                    }
                }
                if (i < closed.lastIndex) Box(Modifier.padding(start = 58.dp).fillMaxWidth().height(0.5.dp).background(colors.separator))
            }
        }
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = PaneTheme.type.title3.copy(fontWeight = FontWeight.Bold),
        color = PaneTheme.colors.label,
        modifier = Modifier.padding(start = 4.dp, bottom = 14.dp),
    )
}

@Composable
private fun ColumnScope.PrivateIntro() {
    val colors = PaneTheme.colors
    Spacer(Modifier.height(72.dp))
    Box(
        Modifier.align(Alignment.CenterHorizontally).size(76.dp).clip(PaneShapes.card).background(colors.accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(PaneIcons.Private, null, tint = colors.accent, modifier = Modifier.size(40.dp))
    }
    Spacer(Modifier.height(22.dp))
    Text(
        "Private Browsing",
        style = PaneTheme.type.title1,
        color = colors.label,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    Text(
        "Pages, searches, cookies and site data from private tabs are erased when you close them. " +
            "Nothing is written to your history.\n\nDownloads and bookmarks you create are kept. " +
            "Private browsing doesn't hide you from your network provider or the sites you visit.",
        style = PaneTheme.type.subheadline,
        color = colors.secondaryLabel,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

