package app.pane.browser.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.pressDim
import app.pane.browser.ui.components.pressScale
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.ContinuousRoundedShape
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.core.library.LetterTiles
import app.pane.core.url.UrlDisplay
import app.pane.core.url.UrlInput
import kotlin.math.abs

/** A site shown as a tile on the start page and in the empty address editor. */
data class FavoriteSite(val url: String, val title: String)

/**
 * Favourites if the user has any, otherwise their most-visited sites. Nothing is fetched from
 * the network for tiles; they're monograms, so the start page leaks nothing.
 */
@Composable
fun rememberFavoriteSites(limit: Int = 8): List<FavoriteSite> {
    val container = LocalAppContainer.current
    val favorites by remember { container.bookmarks.observeFavorites() }.collectAsStateWithLifecycle(emptyList())
    var top by remember { mutableStateOf(emptyList<FavoriteSite>()) }
    LaunchedEffect(favorites.isEmpty()) {
        if (favorites.isEmpty()) top = container.history.topSites(limit).map { FavoriteSite(it.url, it.title) }
    }
    return if (favorites.isNotEmpty()) favorites.take(limit).map { FavoriteSite(it.url, it.title) } else top
}

@Composable
fun FavoritesGrid(sites: List<FavoriteSite>, onOpen: (String) -> Unit, modifier: Modifier = Modifier, columns: Int = 4) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        sites.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { site -> FavoriteTile(site, onClick = { onOpen(site.url) }) }
                repeat(columns - row.size) { Spacer(Modifier.width(72.dp)) }
            }
        }
    }
}

@Composable
fun FavoriteTile(site: FavoriteSite, onClick: () -> Unit) {
    val colors = PaneTheme.colors
    val host = UrlDisplay.toolbarText(site.url)
    Column(
        Modifier.width(72.dp).pressScale(pressedScale = 0.92f, haptic = true, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Monogram(host, size = 60)
        Spacer(Modifier.height(6.dp))
        Text(
            site.title.ifBlank { host }.let { cleanTitle(it) },
            style = PaneTheme.type.caption,
            color = colors.label,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** The part of a host people recognise: `en.m.wikipedia.org` → `wikipedia`. */
fun siteName(host: String): String = LetterTiles.siteName(host)

/** First letter of the site on a colour derived from its name, so each site keeps its colour. */
@Composable
fun Monogram(host: String, size: Int, modifier: Modifier = Modifier) {
    val name = siteName(host)
    val letter = name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•"
    val palette = listOf(
        Color(0xFF5E5CE6), Color(0xFF0A84FF), Color(0xFF30B0C7), Color(0xFF34C759), Color(0xFFFF9F0A),
        Color(0xFFFF375F), Color(0xFFBF5AF2), Color(0xFF64D2FF), Color(0xFFAC8E68), Color(0xFF8E8E93),
    )
    val color = palette[abs(name.hashCode()) % palette.size]
    Box(
        modifier
            .size(size.dp)
            .clip(ContinuousRoundedShape((size * 0.24f).dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = Color.White, fontSize = (size * 0.42f).sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun cleanTitle(title: String): String = title.split(" - ", " | ", " · ", " — ").first().trim()

/** Shown in the address editor before typing: favourites and "Paste and Go". */
@Composable
fun FavoritesPanel(onOpen: (String) -> Unit, onPasteAndGo: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    val sites = rememberFavoriteSites()
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (sites.isNotEmpty()) {
            Text("Favorites", style = PaneTheme.type.title3, color = colors.label, modifier = Modifier.padding(start = 4.dp, bottom = 14.dp))
            FavoritesGrid(sites, onOpen)
            Spacer(Modifier.height(20.dp))
        }
        Row(
            Modifier
                .clip(PaneShapes.pill)
                .background(colors.fill)
                .pressDim(onClick = onPasteAndGo)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(PaneIcons.Copy, null, tint = colors.label, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Paste and Go", style = PaneTheme.type.subheadline, color = colors.label)
        }
    }
}

/** True for URLs worth showing as a site (not search results or internal pages). */
fun isSiteUrl(url: String) = UrlInput.hostOf(url) != null && (url.startsWith("https://") || url.startsWith("http://"))
