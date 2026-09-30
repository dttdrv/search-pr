package app.pane.browser.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.SiteIcon
import app.pane.browser.ui.components.pressDim
import app.pane.browser.ui.components.pressScale
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.entrance
import app.pane.core.library.LetterTiles
import app.pane.core.url.UrlDisplay
import app.pane.core.url.UrlInput

/** A site shown as an icon on the start page and in the empty address editor. */
data class FavoriteSite(val url: String, val title: String)

/**
 * Favourites if the user has any, otherwise their most-visited sites. Only local data: icons come
 * from Pane's own cache, so the start page itself asks the network for nothing.
 */
@Composable
fun rememberFavoriteSites(limit: Int = 8, includeHistory: Boolean = true): List<FavoriteSite> {
    val container = LocalAppContainer.current
    val favorites by remember { container.bookmarks.observeFavorites() }.collectAsStateWithLifecycle(emptyList())
    val top by remember(limit) { container.history.observeTopSites(limit) }.collectAsStateWithLifecycle(emptyList())
    val settings by container.settings.state.collectAsStateWithLifecycle()
    return if (favorites.isNotEmpty()) favorites.take(limit).map { FavoriteSite(it.url, it.title) }
        else if (includeHistory && settings.rememberHistory) top.map { FavoriteSite(it.url, it.title) } else emptyList()
}

/**
 * Sites as a grid of icons with their names underneath, [columns] to a row. Icons arrive one after
 * another; [indexOffset] says how many things above them on the page have already been given a
 * place in that sequence (the cells are numbered row by row from there).
 */
@Composable
fun FavoritesGrid(
    sites: List<FavoriteSite>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 4,
    indexOffset: Int = 0,
    iconSize: Dp = 60.dp,
    labelLines: Int = 2,
) {
    val perRow = columns.coerceAtLeast(1)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        sites.chunked(perRow).forEachIndexed { row, chunk ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chunk.forEachIndexed { col, site ->
                    FavoriteTile(
                        site = site,
                        iconSize = iconSize,
                        labelLines = labelLines,
                        onClick = { onOpen(site.url) },
                        modifier = Modifier.weight(1f).entrance(indexOffset + row * perRow + col, key = site.url),
                    )
                }
                // A short last row keeps the same cell width as the rows above it.
                repeat(perRow - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun FavoriteTile(
    site: FavoriteSite,
    iconSize: Dp,
    labelLines: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.pressScale(pressedScale = 0.92f, haptic = true, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SiteIcon(site.url, iconSize)
        Spacer(Modifier.height(8.dp))
        Text(
            favoriteLabel(site),
            style = PaneTheme.type.caption,
            color = PaneTheme.colors.secondaryLabel,
            maxLines = labelLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

/** The short name under an icon: the page's own name if it is brief, otherwise the site's. */
private fun favoriteLabel(site: FavoriteSite): String {
    val title = cleanTitle(site.title)
    if (title.isNotBlank() && title.length <= 22) return title
    val name = siteName(UrlDisplay.toolbarText(site.url)).replaceFirstChar { it.uppercase() }
    return name.ifBlank { title.ifBlank { site.url } }
}

/** The part of a host people recognise: `en.m.wikipedia.org` → `wikipedia`. */
fun siteName(host: String): String = LetterTiles.siteName(host)

private fun cleanTitle(title: String): String = title.split(" - ", " | ", " · ", " — ").first().trim()

/**
 * Shown in the address editor before typing, bottom-aligned so it sits in thumb reach above the
 * field: favourites, then [recent] pages as rows, then "Paste and Go". If the keyboard leaves too
 * little room it scrolls, staying anchored to the bottom.
 */
@Composable
fun FavoritesPanel(
    onOpen: (String) -> Unit,
    onPasteAndGo: () -> Unit,
    modifier: Modifier = Modifier,
    recent: List<FavoriteSite> = emptyList(),
    includeHistory: Boolean = true,
) {
    val colors = PaneTheme.colors
    val sites = rememberFavoriteSites(limit = 4, includeHistory = includeHistory)
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState(), reverseScrolling = true)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        var next = 0
        if (sites.isNotEmpty()) {
            PanelLabel("Favorites", Modifier.entrance(next))
            FavoritesGrid(sites, onOpen, indexOffset = next + 1, iconSize = 52.dp, labelLines = 1)
            next += 1 + sites.size
            Spacer(Modifier.height(20.dp))
        }
        if (recent.isNotEmpty()) {
            PanelLabel("Recent", Modifier.entrance(next))
            next += 1
            recent.forEach { site ->
                RecentRow(site, onClick = { onOpen(site.url) }, modifier = Modifier.entrance(next, key = site.url))
                next += 1
            }
            Spacer(Modifier.height(12.dp))
        }
        Box(
            Modifier
                .entrance(next)
                .pressScale(pressedScale = 0.95f, haptic = true, onClick = onPasteAndGo)
                .clip(PaneShapes.pill)
                .background(colors.fill)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text("Paste and Go", style = PaneTheme.type.subheadline, color = colors.label)
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun PanelLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = PaneTheme.type.footnote,
        fontWeight = FontWeight.SemiBold,
        color = PaneTheme.colors.secondaryLabel,
        modifier = modifier.padding(start = 2.dp, bottom = 10.dp),
    )
}

/** A page as a row: its icon, its title and the host beneath. */
@Composable
private fun RecentRow(site: FavoriteSite, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    val host = UrlDisplay.toolbarText(site.url)
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .pressDim(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SiteIcon(site.url, 32.dp)
        Column(Modifier.weight(1f)) {
            Text(
                site.title.ifBlank { host },
                style = PaneTheme.type.body,
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (site.title.isNotBlank() && host != site.title) {
                Text(host, style = PaneTheme.type.footnote, color = colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** True for URLs worth showing as a site (not search results or internal pages). */
fun isSiteUrl(url: String) = UrlInput.hostOf(url) != null && (url.startsWith("https://") || url.startsWith("http://"))
