package app.pane.browser.ui.extensions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.extensions.AmoClient
import app.pane.browser.ui.components.ButtonStyle
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.components.PaneSheet
import app.pane.browser.ui.components.PrimaryButton
import app.pane.browser.ui.components.SearchField
import app.pane.browser.ui.components.TextButton
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.theme.PaneTheme
import app.pane.core.extensions.AddonMatching
import app.pane.core.extensions.Amo
import app.pane.core.extensions.AmoAddon
import app.pane.core.extensions.ExtensionFormat
import app.pane.core.extensions.InstalledAddonRef
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Searches addons.mozilla.org for Android-compatible extensions. Before anything is typed it shows
 * Mozilla's recommended add-ons; results page in as the list nears its end.
 */
@Composable
fun AddonStoreScreen() {
    val container = LocalAppContainer.current
    val manager = container.extensions
    val navigator = LocalNavigator.current
    val focus = LocalFocusManager.current
    val installed by manager.installed.collectAsStateWithLifecycle()
    val installing by manager.installing.collectAsStateWithLifecycle()
    val recorded by manager.recordedSlugs.collectAsStateWithLifecycle()
    val refs = remember(installed) { installed.map { InstalledAddonRef(it.id, it.name, it.amoListingUrl) } }
    val store = remember(manager) { StoreState(manager.amo) }
    val listState = rememberLazyListState()
    var query by rememberSaveable { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<AmoAddon?>(null) }

    // Debounced: every keystroke restarts the effect, so only a pause reaches the network.
    LaunchedEffect(query, retry) {
        // Typing and then deleting back to the shown query needn't refetch.
        if (store.hasResultsFor(query)) return@LaunchedEffect
        if (query.isNotBlank()) delay(SEARCH_DEBOUNCE_MS)
        store.search(query)
    }
    LaunchedEffect(listState, store) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (info.totalItemsCount > 0 && last >= info.totalItemsCount - PREFETCH_DISTANCE) info.totalItemsCount else -1
        }
            .distinctUntilChanged()
            .collect { if (it >= 0) store.loadMore() }
    }

    fun stateOf(addon: AmoAddon): GetState = when {
        AddonMatching.isInstalled(addon.slug, addon.name, refs, guid = addon.guid, recorded = recorded) -> GetState.Installed
        isInstalling(installing, addon.slug, addon.xpiUrl) -> GetState.Installing
        else -> GetState.Get
    }

    Box(Modifier.fillMaxSize()) {
        LargeTitleScaffold(
            title = "Add-ons",
            onBack = navigator::pop,
            backLabel = "Extensions",
            listState = listState,
            header = {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search add-ons",
                    onSubmit = { focus.clearFocus() },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            },
        ) {
            val results = store.results
            when {
                results.isEmpty() && store.loading -> item(key = "loading") { CenteredSpinner() }
                results.isEmpty() && store.failed -> item(key = "failed") {
                    Message(
                        title = "Couldn’t reach the add-on store",
                        body = "Check your connection and try again.",
                        action = "Try Again",
                        onAction = { retry++ },
                    )
                }
                results.isEmpty() && store.searched -> item(key = "none") {
                    Message(title = "No add-ons found", body = "Nothing matches “${query.trim()}”. Try another word.")
                }
                else -> {
                    item(key = "section") {
                        Text(
                            (if (store.resultsQuery.isBlank()) "Recommended for Android" else "Results").uppercase(),
                            style = PaneTheme.type.footnote,
                            color = PaneTheme.colors.secondaryLabel,
                            modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 16.dp, bottom = 7.dp),
                        )
                    }
                    itemsIndexed(results, key = { _, addon -> "addon:${addon.guid}" }) { index, addon ->
                        GroupedItem(index = index, count = results.size, separatorInset = ROW_INSET) {
                            AddonRow(
                                addon = addon,
                                state = stateOf(addon),
                                onGet = { manager.install(addon.installUrl(), addon.slug) },
                                onClick = {
                                    focus.clearFocus()
                                    selected = addon
                                },
                            )
                        }
                    }
                    if (store.next != null || store.loadingMore) {
                        item(key = "more") { CenteredSpinner(Modifier.height(64.dp)) }
                    }
                }
            }
        }
        AddonDetailSheet(addon = selected, onDismiss = { selected = null })
    }
}

private const val SEARCH_DEBOUNCE_MS = 350L
private const val PREFETCH_DISTANCE = 5
private val ROW_INSET = 84.dp

/** Search results and paging for the store. Survives recomposition, not the screen. */
@Stable
private class StoreState(private val amo: AmoClient) {
    var results by mutableStateOf<List<AmoAddon>>(emptyList())
        private set
    var next by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set

    /** True once a search finished, so "no results" isn't shown before the first answer. */
    var searched by mutableStateOf(false)
        private set

    /** The query [results] belong to. */
    var resultsQuery by mutableStateOf("")
        private set

    private var generation = 0

    fun hasResultsFor(query: String) = searched && !failed && resultsQuery == query.trim()

    suspend fun search(query: String) {
        val q = query.trim()
        val gen = ++generation
        loading = true
        failed = false
        try {
            val page = amo.page(Amo.searchUrl(q.ifEmpty { null }))
            if (gen != generation) return
            if (page == null) {
                failed = true
                if (resultsQuery != q) results = emptyList()
                return
            }
            results = page.addons.distinctBy { it.guid }
            next = page.next
            resultsQuery = q
            searched = true
        } finally {
            if (gen == generation) loading = false
        }
    }

    suspend fun loadMore() {
        val url = next ?: return
        if (loading || loadingMore) return
        val gen = generation
        loadingMore = true
        try {
            val page = amo.page(url) ?: return
            if (gen != generation) return
            val seen = results.mapTo(HashSet()) { it.guid }
            results = results + page.addons.filter { seen.add(it.guid) }
            next = page.next
        } finally {
            loadingMore = false
        }
    }
}

@Composable
private fun AddonRow(addon: AmoAddon, state: GetState, onGet: () -> Unit, onClick: () -> Unit) {
    val colors = PaneTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AmoIcon(addon.iconUrl, 56.dp)
        Column(Modifier.weight(1f)) {
            Text(
                addon.name,
                style = PaneTheme.type.body.copy(fontWeight = FontWeight.SemiBold),
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val line = addon.authors.joinToString(", ").ifBlank { addon.summary }
            if (line.isNotBlank()) {
                Text(line, style = PaneTheme.type.footnote, color = colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(
                Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (addon.ratingCount > 0) StarRating(addon.rating)
                Text(ExtensionFormat.users(addon.users), style = PaneTheme.type.caption, color = colors.secondaryLabel, maxLines = 1)
                if (addon.recommended) RecommendedBadge()
            }
        }
        GetButton(state, onClick = onGet)
    }
}

/**
 * An add-on store listing in a sheet: icon, ratings, users, summary, and "Add to Pane". Shared by
 * the store and the curated list on the Extensions screen.
 */
@Composable
internal fun AddonDetailSheet(addon: AmoAddon?, onDismiss: () -> Unit) {
    val shown = rememberRetained(addon)
    PaneSheet(visible = addon != null, onDismiss = onDismiss) {
        if (shown != null) AddonDetailContent(shown, onDismiss)
    }
}

@Composable
private fun ColumnScope.AddonDetailContent(addon: AmoAddon, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val manager = container.extensions
    val navigator = LocalNavigator.current
    val colors = PaneTheme.colors
    val installed by manager.installed.collectAsStateWithLifecycle()
    val installing by manager.installing.collectAsStateWithLifecycle()
    val recorded by manager.recordedSlugs.collectAsStateWithLifecycle()
    val refs = remember(installed) { installed.map { InstalledAddonRef(it.id, it.name, it.amoListingUrl) } }
    val state = when {
        AddonMatching.isInstalled(addon.slug, addon.name, refs, guid = addon.guid, recorded = recorded) -> GetState.Installed
        isInstalling(installing, addon.slug, addon.xpiUrl) -> GetState.Installing
        else -> GetState.Get
    }

    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AmoIcon(addon.iconUrl, 88.dp)
        Spacer(Modifier.height(14.dp))
        Text(addon.name, style = PaneTheme.type.title2, color = colors.label, textAlign = TextAlign.Center)
        if (addon.authors.isNotEmpty()) {
            Text(
                addon.authors.joinToString(", "),
                style = PaneTheme.type.subheadline,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Stat(
                top = if (addon.ratingCount > 0) ExtensionFormat.rating(addon.rating) else "—",
                bottom = if (addon.ratingCount > 0) "${ExtensionFormat.compactCount(addon.ratingCount)} ratings" else "No ratings",
                modifier = Modifier.weight(1f),
            ) {
                if (addon.ratingCount > 0) StarRating(addon.rating, starSize = 10.dp)
            }
            StatDivider()
            Stat(top = ExtensionFormat.compactCount(addon.users), bottom = if (addon.users == 1L) "User" else "Users", modifier = Modifier.weight(1f))
            if (addon.recommended) {
                StatDivider()
                Stat(top = "", bottom = "By Mozilla", modifier = Modifier.weight(1f)) { RecommendedBadge() }
            }
        }
        if (addon.summary.isNotBlank()) {
            Text(
                addon.summary,
                style = PaneTheme.type.body,
                color = colors.label,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
            )
        }
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        PrimaryButton(
            text = when (state) {
                GetState.Get -> "Add to Pane"
                GetState.Installing -> "Adding…"
                GetState.Installed -> "Installed"
            },
            icon = if (state == GetState.Installed) PaneIcons.Check else null,
            enabled = state == GetState.Get,
            onClick = { manager.install(addon.installUrl(), addon.slug) },
        )
        addon.listingUrl?.let { url ->
            Spacer(Modifier.height(4.dp))
            PrimaryButton(
                text = "View on addons.mozilla.org",
                style = ButtonStyle.Plain,
                onClick = {
                    onDismiss()
                    openInNewTab(container, navigator, url)
                },
            )
        }
    }
}

@Composable
private fun Stat(top: String, bottom: String, modifier: Modifier = Modifier, extra: (@Composable () -> Unit)? = null) {
    val colors = PaneTheme.colors
    Column(modifier.padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (top.isNotEmpty()) {
            Text(top, style = PaneTheme.type.title3, color = colors.secondaryLabel, maxLines = 1)
        }
        if (extra != null) Box(Modifier.padding(vertical = 3.dp)) { extra() }
        Text(bottom, style = PaneTheme.type.caption, color = colors.secondaryLabel, maxLines = 1)
    }
}

@Composable
private fun StatDivider() {
    Box(
        Modifier
            .width(0.5.dp)
            .fillMaxHeight()
            .padding(vertical = 6.dp)
            .background(PaneTheme.colors.separator),
    )
}

@Composable
private fun CenteredSpinner(modifier: Modifier = Modifier.height(160.dp)) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.padding(8.dp), color = PaneTheme.colors.secondaryLabel, strokeWidth = 2.5.dp)
    }
}

@Composable
private fun Message(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    val colors = PaneTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = PaneTheme.type.title3, color = colors.label, textAlign = TextAlign.Center)
        Text(
            body,
            style = PaneTheme.type.subheadline,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (action != null) TextButton(action, onClick = onAction, modifier = Modifier.padding(top = 8.dp))
    }
}
