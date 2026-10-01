package app.pane.browser.ui.browser

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.pressDim
import app.pane.browser.ui.components.pressScale
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.canScroll
import app.pane.browser.ui.theme.entrance
import app.pane.browser.ui.theme.floating
import app.pane.browser.ui.theme.stretch
import app.pane.core.settings.StartTitle
import app.pane.core.url.UrlDisplay
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The start page for new tabs: everything centred, the block sitting a little above the middle. An
 * optional title (the name, the user's own words, or the time), a search pill and a grid of favourite
 * flat tiles, never outlined. What shows is up to the user (see
 * Settings > Start page). Each piece arrives after the one before it, once per tab. Tapping the pill
 * calls [onSearch] (the address editor). Private tabs get the title "Private", one muted line and the
 * same pill.
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
    val container = LocalAppContainer.current
    val state by container.store.state.collectAsStateWithLifecycle()
    // Arrivals play once per tab, not each time the page comes back into view.
    val tabKey: Any = state.selectedTab?.id ?: private
    BoxWithConstraints(modifier.fillMaxSize().background(colors.background)) {
        val minContent = (maxHeight - contentPadding.calculateTopPadding() - contentPadding.calculateBottomPadding()).coerceAtLeast(0.dp)
        val scroll = rememberScrollState()
        Column(
            Modifier
                .fillMaxSize()
                .stretch()
                .verticalScroll(scroll, enabled = scroll.canScroll)
                .padding(contentPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Centred in the space there is, then lifted: the spacer below pushes the middle of the block up.
            Column(
                Modifier.widthIn(max = 420.dp).fillMaxWidth().heightIn(min = minContent),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (private) PrivateStart(tabKey, onSearch) else NormalStart(tabKey, onOpen, onSearch)
                Spacer(Modifier.height(minContent * 0.16f))
            }
        }
    }
}

@Composable
private fun NormalStart(tabKey: Any, onOpen: (String) -> Unit, onSearch: () -> Unit) {
    val container = LocalAppContainer.current
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val state by container.store.state.collectAsStateWithLifecycle()
    val sites = rememberFavoriteSites(
        limit = settings.startFavoritesLimit,
        source = settings.startFavoritesSource,
    )

    // Everything takes its place in one sequence, capped by `entrance` itself.
    var next = 0
    val title = settings.startTitle
    val custom = settings.startTitleCustom
    if (title == StartTitle.Clock) {
        StartClock(Modifier.entrance(next, key = tabKey))
        next += 1
    } else if (title == StartTitle.Name || (title == StartTitle.Custom && custom.isNotEmpty())) {
        Text(
            if (title == StartTitle.Name) "Pane" else custom,
            Modifier.entrance(next, key = tabKey),
            style = PaneTheme.type.largeTitle,
            color = PaneTheme.colors.label,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        next += 1
    }

    if (settings.startShowSearch) {
        if (next > 0) Spacer(Modifier.height(28.dp))
        StartSearchPill("Search or enter address", onSearch, Modifier.entrance(next, key = tabKey))
        next += 1
    }

    if (settings.showHomeFavorites && sites.isNotEmpty()) {
        if (next > 0) Spacer(Modifier.height(36.dp))
        FavoritesGrid(sites, onOpen, indexOffset = next, entranceKey = tabKey)
        next += sites.size
    }

    val closed = state.recentlyClosed.filter { it.tab.url.isNotBlank() }.take(3)
    if (closed.isNotEmpty()) {
        Spacer(Modifier.height(if (next > 0) 36.dp else 0.dp))
        Icon(
            PaneIcons.Clock,
            contentDescription = "Recently closed",
            tint = PaneTheme.colors.tertiaryLabel,
            modifier = Modifier.entrance(next, key = tabKey).size(16.dp),
        )
        Spacer(Modifier.height(4.dp))
        Column(Modifier.entrance(next + 1, key = tabKey).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            closed.forEach { c -> ClosedRow(c.tab.url, c.tab.title, onClick = { onOpen(c.tab.url) }) }
        }
    }
}

@Composable
private fun PrivateStart(tabKey: Any, onSearch: () -> Unit) {
    val colors = PaneTheme.colors
    Text("Private", Modifier.entrance(0, key = tabKey), style = PaneTheme.type.largeTitle, color = colors.label)
    Spacer(Modifier.height(8.dp))
    Text(
        "Nothing is saved when you close these tabs.",
        style = PaneTheme.type.body,
        color = colors.secondaryLabel,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().entrance(1, key = tabKey),
    )
    Spacer(Modifier.height(28.dp))
    StartSearchPill("Search privately", onSearch, Modifier.entrance(2, key = tabKey))
}

/** The time and, beneath it, the date, both centred. The time moves on by itself each minute. */
@Composable
private fun StartClock(modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    val context = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000L - value % 60_000L + 50L)
        }
    }
    val time = remember(now) { DateFormat.getTimeFormat(context).format(Date(now)) }
    val date = remember(now) {
        val locale = Locale.getDefault()
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEMMMd"), locale).format(Date(now))
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            time,
            style = PaneTheme.type.largeTitle.copy(fontSize = 44.sp, lineHeight = 52.sp),
            color = colors.label,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(date, style = PaneTheme.type.body, color = colors.secondaryLabel, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** A centred flat pill with a muted placeholder; tapping it opens the address editor. It is not itself a field. */
@Composable
private fun StartSearchPill(placeholder: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .pressScale(pressedScale = 0.985f, haptic = true, onClick = onClick)
            .floating(PaneShapes.pill, 0.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(PaneIcons.Magnifier, contentDescription = null, tint = colors.secondaryLabel, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(placeholder, style = PaneTheme.type.body, color = colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A closed tab as one quiet centred line: its title, or its host if it had none. */
@Composable
private fun ClosedRow(url: String, title: String, onClick: () -> Unit) {
    Text(
        title.ifBlank { UrlDisplay.toolbarText(url) },
        style = PaneTheme.type.subheadline,
        color = PaneTheme.colors.secondaryLabel,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .pressDim(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
