package app.pane.browser.ui.browser

import android.app.Activity
import androidx.activity.BackEventCompat
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.engine.EngineEvent
import app.pane.browser.ui.findinpage.FindInPageBar
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.prompts.SiteInfoSheet
import app.pane.browser.ui.tabs.TabSwitcher
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneTheme
import app.pane.core.tabs.TabState
import app.pane.core.url.UrlDisplay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The browser itself: page, start page, bottom chrome, and the overlays that grow out of it
 * (address editor, tab overview, menu, find bar, site info). Themed dark-violet in private mode.
 */
@Composable
fun BrowserScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val state by container.store.state.collectAsStateWithLifecycle()
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val chrome = remember { BrowserChrome(scope) }
    val tab = state.selectedTab
    val private = tab?.isPrivate == true
    var privateUnlocked by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        privateUnlocked = false
        container.privacyStats.flush()
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val dynamicPx = with(density) { BarMetrics.dynamic.toPx() }
    val fullscreen = tab?.fullscreen == true

    // Toolbar collapses with the page.
    LaunchedEffect(tab?.id) {
        chrome.expand()
        val id = tab?.id ?: return@LaunchedEffect
        var last = -1
        container.sessions.scroll.collect { update ->
            if (update.tabId != id) return@collect
            val previous = last
            last = update.scrollY
            when {
                update.scrollY <= 0 -> chrome.expand()
                previous >= 0 && container.settings.current.hideToolbarOnScroll -> chrome.onScroll(update.scrollY - previous, dynamicPx)
            }
        }
    }
    LaunchedEffect(tab?.loading, tab?.url) { if (tab?.loading == true) chrome.expand() }

    // Gecko needs to know how much of the page the toolbar can cover, and how much it covers now.
    LaunchedEffect(chrome, fullscreen) {
        snapshotFlow { chrome.collapse.value }.collect { c ->
            val view = chrome.geckoView ?: return@collect
            view.setVerticalClipping(if (fullscreen) 0 else -(dynamicPx * (1f - c)).toInt())
        }
    }

    // Snapshots for the tab overview and back gesture, and clearing covers on first paint.
    LaunchedEffect(Unit) {
        container.sessions.events.collect { event ->
            when (event) {
                is EngineEvent.PageSettled -> scope.launch {
                    delay(350)
                    val current = container.store.state.value.selectedTab
                    if (current?.id == event.tabId && chrome.overlay == null && !chrome.showTabs) {
                        captureInto(container, chrome, current)
                    }
                }
                is EngineEvent.FirstPaint -> if (event.tabId == container.store.state.value.selectedTabId && chrome.overlay?.kind == PageOverlay.Kind.Cover) {
                    delay(60)
                    chrome.overlay = null
                }
                is EngineEvent.ShowToolbar -> chrome.expand()
                else -> Unit
            }
        }
    }
    // Hardware keyboard.
    LaunchedEffect(Unit) {
        KeyboardShortcuts.events.collect { shortcut ->
            val current = container.store.state.value.selectedTab
            val mode = current?.isPrivate == true
            val tabs = container.store.state.value.tabsIn(mode)
            val i = tabs.indexOfFirst { it.id == current?.id }
            when (shortcut) {
                Shortcut.NewTab, Shortcut.NewPrivateTab -> {
                    container.browser.newTab(shortcut == Shortcut.NewPrivateTab)
                    editText = ""
                    chrome.editing = true
                }
                Shortcut.CloseTab -> current?.let { container.browser.close(it.id) }
                Shortcut.FocusAddress -> {
                    editText = current?.url?.let { container.browser.searchTermsFor(it) ?: UrlDisplay.editableText(it) }.orEmpty()
                    chrome.editing = true
                }
                Shortcut.Reload -> container.browser.reload()
                Shortcut.Find -> if (current?.url?.isNotEmpty() == true) chrome.findInPage = true
                Shortcut.NextTab -> tabs.getOrNull((i + 1).mod(tabs.size.coerceAtLeast(1)))?.let { container.browser.select(it.id) }
                Shortcut.PreviousTab -> tabs.getOrNull((i - 1).mod(tabs.size.coerceAtLeast(1)))?.let { container.browser.select(it.id) }
                Shortcut.Back -> container.browser.goBack()
                Shortcut.Forward -> container.browser.goForward()
                Shortcut.ShowTabs -> chrome.showTabs = true
            }
        }
    }

    // Never leave a cover up for long, whatever happens to the page.
    LaunchedEffect(chrome.overlay) {
        val cover = chrome.overlay
        if (cover?.kind == PageOverlay.Kind.Cover) {
            delay(900)
            if (chrome.overlay === cover) chrome.overlay = null
        }
    }

    // Back swipe: slide the page away like iOS, revealing where you're going.
    val canGoBack = tab != null && (tab.canGoBack || tab.parentId != null) && !chrome.anyOverlay && navigator.isEmpty && !fullscreen
    PredictiveBackHandler(enabled = canGoBack) { events: Flow<BackEventCompat> ->
        val current = tab ?: return@PredictiveBackHandler
        val entry = container.sessions.backEntry(current.id)
        val behind = entry?.let { container.snapshots.get(current.id, it.url) }
            ?: current.parentId?.takeIf { !current.canGoBack }?.let { container.thumbnails.get(it) }
        val captureJob = scope.launch {
            val snap = chrome.capture(150)
            chrome.overlay = PageOverlay(snap, behind, entry?.title, PageOverlay.Kind.Back)
        }
        try {
            events.collect { e ->
                chrome.backFromRight = e.swipeEdge == BackEventCompat.EDGE_RIGHT
                chrome.back.snapTo(e.progress)
            }
            captureJob.join()
            chrome.back.animateTo(1f, Motion.snappy())
            if (current.canGoBack) container.browser.goBack() else container.browser.close(current.id)
            chrome.overlay = behind?.let { PageOverlay(it, null, kind = PageOverlay.Kind.Cover) }
            chrome.back.snapTo(0f)
        } catch (e: CancellationException) {
            captureJob.cancel()
            scope.launch {
                chrome.back.animateTo(0f, Motion.bouncy())
                if (chrome.overlay?.kind == PageOverlay.Kind.Back) chrome.overlay = null
            }
            throw e
        }
    }

    PaneTheme(mode = settings.theme, private = private, hapticsEnabled = settings.haptics, reduceMotion = settings.reduceMotion) {
        val colors = PaneTheme.colors
        val tint = tab?.themeColor?.takeIf { settings.tintToolbarWithPage && !private && tab.url.isNotEmpty() }?.let { Color(it) }
        val statusColor = tint ?: if (tab == null || tab.url.isEmpty()) colors.groupedBackground else colors.background
        StatusBarAppearance(lightIcons = statusColor.luminance() < 0.5f && navigator.isEmpty || (!navigator.isEmpty && colors.isDark))

        val sameMode = state.tabsIn(private)
        val index = sameMode.indexOfFirst { it.id == tab?.id }
        val locked = private && settings.lockPrivateTabs && !privateUnlocked && BiometricGate.isAvailable(LocalContext.current)
        val activity = LocalActivity.current

        Box(Modifier.fillMaxSize().background(colors.background)) {
            // Page.
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(
                        if (fullscreen) PaddingValues() else PaddingValues(top = statusTop, bottom = navBottom + BarMetrics.collapsed),
                    )
                    .onGloballyPositioned { chrome.pageRect = it.boundsInRoot() },
            ) {
                EngineView(
                    tab = tab,
                    modifier = Modifier.fillMaxSize(),
                    coverColor = colors.background.toArgb(),
                    onViewCreated = { view ->
                        chrome.geckoView = view
                        view.setDynamicToolbarMaxHeight(dynamicPx.toInt())
                    },
                )
                if (tab == null || tab.url.isEmpty()) {
                    HomePage(
                        private = private,
                        contentPadding = PaddingValues(bottom = BarMetrics.dynamic + 16.dp),
                        onOpen = { container.browser.submit(it) },
                    )
                }
                if (tab?.crashed == true) {
                    CrashedPage(onReload = { container.browser.reload() })
                }
                PageOverlayLayer(
                    chrome = chrome,
                    previousThumb = sameMode.getOrNull(index - 1)?.let { container.thumbnails.get(it.id) },
                    nextThumb = sameMode.getOrNull(index + 1)?.let { container.thumbnails.get(it.id) },
                )
                if (locked) {
                    PrivateLockCover(onUnlock = { activity?.let { BiometricGate.authenticate(it) { ok -> if (ok) privateUnlocked = true } } })
                }
            }

            // Status bar backdrop, tinted with the page's theme colour when it has one.
            if (!fullscreen) {
                Box(Modifier.fillMaxWidth().height(statusTop).background(statusColor))
            }

            // Bottom chrome.
            AnimatedVisibility(
                visible = !fullscreen && !chrome.findInPage,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(Motion.smooth()) { it } + fadeIn(Motion.fade()),
                exit = slideOutVertically(Motion.smooth()) { it } + fadeOut(Motion.fade()),
            ) {
                BottomBar(
                    tab = tab,
                    tabs = sameMode,
                    chrome = chrome,
                    navBarHeight = navBottom,
                    onAddress = {
                        editText = tab?.url?.let { url -> container.browser.searchTermsFor(url) ?: UrlDisplay.editableText(url) }.orEmpty()
                        chrome.editing = true
                    },
                    onBack = {
                        tab?.let { t ->
                            if (t.canGoBack) container.browser.goBack() else if (t.parentId != null) container.browser.close(t.id)
                        }
                    },
                    onForward = { container.browser.goForward() },
                    onTabs = { chrome.showTabs = true },
                    onNewTab = {
                        container.browser.newTab(private)
                        editText = ""
                        chrome.editing = true
                    },
                    onMenu = { chrome.showMenu = true },
                    onReload = { container.browser.reload() },
                    onStop = { container.browser.stop() },
                    onReader = { tab?.let { container.extensions.toggleReaderMode(it.id) } },
                    onSiteInfo = { chrome.siteInfo = true },
                    onSwipeStart = { _ ->
                        scope.launch {
                            val snap = chrome.capture(120)
                            chrome.overlay = PageOverlay(snap, null, kind = PageOverlay.Kind.TabSwipe)
                        }
                    },
                    onSwipeCommit = { target ->
                        tab?.takeIf { it.url.isNotEmpty() }?.let { t ->
                            chrome.overlay?.current?.let { bmp -> scope.launch { container.thumbnails.put(t.id, bmp, t.isPrivate) } }
                        }
                        if (target == null) {
                            container.browser.newTab(private)
                            chrome.overlay = null
                            editText = ""
                            chrome.editing = true
                        } else {
                            val thumb = container.thumbnails.get(target)
                            container.browser.select(target)
                            chrome.overlay = thumb?.takeIf { state.tab(target)?.url?.isNotEmpty() == true }
                                ?.let { PageOverlay(it, null, kind = PageOverlay.Kind.Cover) }
                        }
                    },
                    onSwipeCancel = { if (chrome.overlay?.kind == PageOverlay.Kind.TabSwipe) chrome.overlay = null },
                )
            }

            if (chrome.findInPage && tab != null) {
                FindInPageBar(tabId = tab.id, onClose = { chrome.findInPage = false }, modifier = Modifier.align(Alignment.BottomCenter))
            }

            AddressEditor(
                visible = chrome.editing,
                initialText = editText,
                private = private,
                onSubmit = { text ->
                    chrome.editing = false
                    container.browser.submit(text)
                },
                onSwitchToTab = { id ->
                    chrome.editing = false
                    container.browser.select(id)
                },
                onDismiss = { chrome.editing = false },
            )

            MenuSheet(
                visible = chrome.showMenu,
                tab = tab,
                onDismiss = { chrome.showMenu = false },
                onFindInPage = { chrome.findInPage = true },
                onNewTab = { p ->
                    container.browser.newTab(p)
                    editText = ""
                    chrome.editing = true
                },
            )

            SiteInfoSheet(visible = chrome.siteInfo, tabId = tab?.id, onDismiss = { chrome.siteInfo = false })
        }

        TabSwitcher(
            visible = chrome.showTabs,
            chrome = chrome,
            privateUnlocked = privateUnlocked,
            onRequestUnlock = { activity?.let { BiometricGate.authenticate(it) { ok -> if (ok) privateUnlocked = true } } },
            onClosed = { chrome.showTabs = false },
            onNewTab = { p ->
                container.browser.newTab(p)
                editText = ""
                chrome.editing = true
            },
        )

        Onboarding(visible = !settings.onboardingDone, onDone = { })
    }
}

private suspend fun captureInto(container: app.pane.browser.AppContainer, chrome: BrowserChrome, tab: TabState) {
    val bitmap = chrome.capture() ?: return
    container.snapshots.put(tab.id, tab.url, bitmap)
    container.thumbnails.put(tab.id, bitmap, tab.isPrivate)
    container.store.updateTab(tab.id) { it.copy(thumbnailVersion = it.thumbnailVersion + 1) }
}

/** Light or dark status bar icons to suit whatever is behind them. */
@Composable
private fun StatusBarAppearance(lightIcons: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !lightIcons
        controller.isAppearanceLightNavigationBars = !lightIcons
    }
}

@Composable
private fun CrashedPage(onReload: () -> Unit) {
    val colors = PaneTheme.colors
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxSize().background(colors.groupedBackground).padding(32.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.Text("This page stopped working", style = PaneTheme.type.title3, color = colors.label)
        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
        app.pane.browser.ui.components.PrimaryButton("Reload", onClick = onReload, modifier = Modifier.fillMaxWidth(0.6f))
    }
}
