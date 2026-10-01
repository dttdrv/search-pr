package app.pane.browser.ui.browser

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.QuietButton
import app.pane.browser.ui.components.SectionLabel
import app.pane.browser.ui.components.Separator
import app.pane.browser.ui.components.SiteIcon
import app.pane.browser.ui.components.pressDim
import app.pane.browser.ui.components.pressScale
import app.pane.browser.ui.theme.ContinuousRoundedShape
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.canScroll
import app.pane.browser.ui.theme.entrance
import app.pane.browser.ui.theme.floating
import app.pane.core.library.LetterTiles
import app.pane.core.settings.StartFavorites
import app.pane.core.url.UrlDisplay
import app.pane.core.url.UrlInput

/** A site shown as a rounded tile on the start page and in the empty address editor. */
data class FavoriteSite(val url: String, val title: String)

/**
 * The sites to offer as tiles. With no [source] (the address editor): favourites if the user has
 * any, otherwise their most-visited sites. With a [source] (the start page): [StartFavorites.Frequent]
 * is the most-visited sites, falling back to favourites when there is no history to draw on, and
 * [StartFavorites.Bookmarks] is the user's bookmarks, starred ones first. Only local data: icons
 * come from Pane's own cache, so the start page itself asks the network for nothing.
 */
@Composable
fun rememberFavoriteSites(limit: Int = 8, includeHistory: Boolean = true, source: StartFavorites? = null): List<FavoriteSite> {
    val container = LocalAppContainer.current
    val favorites by remember { container.bookmarks.observeFavorites() }.collectAsStateWithLifecycle(emptyList())
    val bookmarks by remember { container.bookmarks.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val top by remember(limit) { container.history.observeTopSites(limit) }.collectAsStateWithLifecycle(emptyList())
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val history = if (includeHistory && settings.rememberHistory) top.map { FavoriteSite(it.url, it.title) } else emptyList()
    val starred = favorites.map { FavoriteSite(it.url, it.title) }
    val sites = when (source) {
        null -> starred.ifEmpty { history }
        StartFavorites.Frequent -> history.ifEmpty { starred }
        StartFavorites.Bookmarks -> (starred + bookmarks.map { FavoriteSite(it.url, it.title) }).distinctBy { it.url }
    }
    return sites.take(limit)
}

/**
 * Sites as a centred grid of rounded tiles with their names underneath, [columns] to a row; a short
 * last row is centred under the rows above it and keeps their cell width. Tiles arrive one after
 * another; [indexOffset] says how many things above them on the page have already been given a place
 * in that sequence (the cells are numbered row by row from there). [entranceKey] says when that
 * arrival plays again; by default each site has its own. [iconSize] is the tile's side.
 */
@Composable
fun FavoritesGrid(
    sites: List<FavoriteSite>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 4,
    indexOffset: Int = 0,
    iconSize: Dp = 60.dp,
    labelLines: Int = 1,
    entranceKey: Any? = null,
) {
    val perRow = columns.coerceAtLeast(1)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        sites.chunked(perRow).forEachIndexed { row, chunk ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val gap = (perRow - chunk.size) / 2f
                if (gap > 0f) Spacer(Modifier.weight(gap))
                chunk.forEachIndexed { col, site ->
                    FavoriteTile(
                        site = site,
                        iconSize = iconSize,
                        labelLines = labelLines,
                        onClick = { onOpen(site.url) },
                        modifier = Modifier.weight(1f).entrance(indexOffset + row * perRow + col, key = entranceKey ?: site.url),
                    )
                }
                if (gap > 0f) Spacer(Modifier.weight(gap))
            }
        }
    }
}

/** A site's own icon on a flat rounded tile: no outline. */
@Composable
internal fun SiteTile(url: String, size: Dp, modifier: Modifier = Modifier) {
    val shape = remember(size) { ContinuousRoundedShape(size * 0.32f) }
    Box(
        modifier
            .size(size)
            .floating(shape, 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        SiteIcon(url, size * 0.5f)
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
        SiteTile(site.url, iconSize)
        Spacer(Modifier.height(9.dp))
        Text(
            favoriteLabel(site),
            style = PaneTheme.type.caption2,
            color = PaneTheme.colors.secondaryLabel,
            maxLines = labelLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

/** The short name under a mark: the page's own name if it is brief, otherwise the site's. */
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
 * field: favourites, then [recent] pages as plain rows. If the keyboard leaves
 * too little room it scrolls, staying anchored to the bottom.
 */
@Composable
fun FavoritesPanel(
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    recent: List<FavoriteSite> = emptyList(),
    includeHistory: Boolean = true,
) {
    val sites = rememberFavoriteSites(limit = 4, includeHistory = includeHistory)
    val scroll = rememberScrollState()
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(scroll, enabled = scroll.canScroll, reverseScrolling = true)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        var next = 0
        if (sites.isNotEmpty()) {
            PanelLabel("Favorites", Modifier.entrance(next))
            FavoritesGrid(sites, onOpen, indexOffset = next + 1, iconSize = 52.dp, labelLines = 1)
            next += 1 + sites.size
            Spacer(Modifier.height(28.dp))
        }
        if (recent.isNotEmpty()) {
            PanelLabel("Recent", Modifier.entrance(next))
            next += 1
            recent.forEachIndexed { i, site ->
                RecentRow(site, line = i > 0, onClick = { onOpen(site.url) }, modifier = Modifier.entrance(next, key = site.url))
                next += 1
            }
            Spacer(Modifier.height(20.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** A small muted heading above a group in the empty address editor. */
@Composable
private fun PanelLabel(label: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) { SectionLabel(label) }
}

/** A page as a plain row: its icon, its title and the host beneath, a hairline above all but the first. */
@Composable
private fun RecentRow(site: FavoriteSite, line: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    val host = UrlDisplay.toolbarText(site.url)
    Column(modifier.fillMaxWidth()) {
        if (line) Separator()
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .pressDim(onClick = onClick)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SiteIcon(site.url, 28.dp)
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
}

/** True for URLs worth showing as a site (not search results or internal pages). */
fun isSiteUrl(url: String) = UrlInput.hostOf(url) != null && (url.startsWith("https://") || url.startsWith("http://"))
