package app.pane.browser.ui.browser

import android.app.Activity
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import app.pane.browser.ui.theme.DarkColors
import app.pane.browser.ui.theme.LightColors
import app.pane.browser.ui.theme.LocalPaneColors
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.EdgeFade
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.PrivateColors
import app.pane.core.tabs.TabState
import app.pane.core.url.UrlDisplay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The browser itself: page, start page, bottom chrome, and the overlays that grow out of it
 * (address editor, tab overview, menu, find bar, site info). Themed dark-violet in private mode.
 */
@OptIn(FlowPreview::class)
@Composable
fun BrowserScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val state by container.store.state.collectAsStateWithLifecycle()
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val chrome = remember { BrowserChrome(scope) }
    val tab = state.selectedTab
    val private = tab?.isPrivate == true
    var privateUnlocked by remember { mutableStateOf(false) }
    // The screen lock can change while Pane is in the background, so this is checked again on return.
    var deviceCanLock by remember { mutableStateOf(BiometricGate.canLock(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { deviceCanLock = BiometricGate.canLock(context) }
    // Private tabs stay hidden until the owner unlocks them, unless this device has no way to ask.
    val privateLocked = settings.lockPrivateTabs && !privateUnlocked && deviceCanLock
    val locked = private && privateLocked
    val currentLocked by rememberUpdatedState(locked)
    val unlockPrivate = rememberPrivateUnlock { privateUnlocked = true }
    var editText by remember { mutableStateOf("") }

    // A link from another app (or the library) must reveal its page, not leave an old
    // address editor, menu, or tab overview covering the new navigation.
    LaunchedEffect(navigator.closeAllVersion) {
        editText = ""
        chrome.editing = false
        chrome.showMenu = false
        chrome.showTabs = false
        chrome.showPrivateTabs = false
        chrome.findInPage = false
        chrome.siteInfo = false
        chrome.overlay = null
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        privateUnlocked = false
        // The editor may hold a private page's address, which mustn't be waiting once the tabs relock.
        if (private && settings.lockPrivateTabs) chrome.editing = false
        container.privacyStats.flush()
    }

    // Locking hides everything about the page, including keyboard focus inside it.
    LaunchedEffect(locked) {
        BrowserChrome.pageLocked.value = locked
        if (locked) {
            editText = ""
            chrome.editing = false
            chrome.overlay = null
            chrome.findInPage = false
            chrome.siteInfo = false
            chrome.geckoView?.clearFocus()
        }
    }
    LaunchedEffect(private) { chrome.overlay = null }
    DisposableEffect(chrome) {
        onDispose {
            BrowserChrome.privateTabsShowing.value = false
            BrowserChrome.pageLocked.value = false
        }
    }

    val swipeCapture = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // The floating bar covers this much of the page; Gecko keeps fixed footers above it until it melts away.
    val dynamicPx = with(density) { (BarMetrics.zone + navBottom).toPx() }
    val fullscreen = tab?.fullscreen == true

    // Toolbar collapses with the page.
    LaunchedEffect(tab?.id, dynamicPx) {
        chrome.restoreEdges(tab?.id)
        chrome.scrolled = false
        chrome.expand()
        val id = tab?.id ?: return@LaunchedEffect
        var last = -1
        container.sessions.scroll.collect { update ->
            if (update.tabId != id) return@collect
            val previous = last
            last = update.scrollY
            chrome.scrolled = update.scrollY > 8
            when {
                update.scrollY <= 0 -> chrome.expand()
                previous >= 0 && container.settings.current.hideToolbarOnScroll -> chrome.onScroll(update.scrollY - previous, dynamicPx)
            }
        }
    }
    // Once the page stops moving: finish the bar's motion, then read the colours the page is showing
    // so the chrome can follow them.
    LaunchedEffect(tab?.id, dynamicPx) {
        val id = tab?.id ?: return@LaunchedEffect
        container.sessions.scroll
            .filter { it.tabId == id }
            .debounce(150)
            .collect {
                chrome.settle()
                delay(120)
                if (chrome.overlay == null && !chrome.showTabs) {
                    chrome.sampleEdges(id, dynamicPx.toInt()) { container.store.state.value.selectedTabId == id }
                }
            }
    }
    LaunchedEffect(tab?.loading, tab?.url) { if (tab?.loading == true) chrome.expand() }
    LaunchedEffect(settings.hideToolbarOnScroll) { if (!settings.hideToolbarOnScroll) chrome.expand() }

    // Gecko needs to know how much of the page the toolbar can cover, and how much it covers now.
    LaunchedEffect(chrome.geckoView, fullscreen, dynamicPx) {
        val view = chrome.geckoView ?: return@LaunchedEffect
        view.setDynamicToolbarMaxHeight(dynamicPx.toInt())
        snapshotFlow { chrome.collapse.value }.collect { c ->
            // Gecko keeps the page's viewport (fixed footers, 100vh) clear of the toolbar by its full
            // height, and the clipping says how much of that the toolbar has slid away: 0 with the
            // bar out, minus its whole height once it has melted, so the page then fills the screen.
            view.setVerticalClipping(if (fullscreen) -dynamicPx.toInt() else -(dynamicPx * c).toInt())
        }
    }

    // Snapshots for the tab overview and back gesture, and clearing covers on first paint.
    LaunchedEffect(dynamicPx) {
        container.sessions.events.collect { event ->
            when (event) {
                is EngineEvent.PageSettled -> scope.launch {
                    delay(350)
                    val current = container.store.state.value.selectedTab
                    if (current?.id == event.tabId && chrome.overlay == null && !chrome.showTabs) {
                        captureInto(container, chrome, current, dynamicPx.toInt())
                    }
                }
                is EngineEvent.FirstPaint -> if (event.tabId == container.store.state.value.selectedTabId) {
                    if (chrome.overlay?.kind == PageOverlay.Kind.Cover) {
                        delay(60)
                        chrome.overlay = null
                    }
                    scope.launch {
                        delay(160)
                        chrome.sampleEdges(event.tabId, dynamicPx.toInt()) { container.store.state.value.selectedTabId == event.tabId }
                    }
                }
                is EngineEvent.ShowToolbar -> if (event.tabId == container.store.state.value.selectedTabId) chrome.expand()
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
                Shortcut.FocusAddress -> if (currentLocked) {
                    unlockPrivate()
                } else {
                    editText = current?.url?.let { container.browser.searchTermsFor(it) ?: UrlDisplay.editableText(it) }.orEmpty()
                    chrome.editing = true
                }
                Shortcut.Reload -> if (!currentLocked) container.browser.reload()
                Shortcut.Find -> if (!currentLocked && current?.url?.isNotEmpty() == true) chrome.findInPage = true
                Shortcut.NextTab -> tabs.getOrNull((i + 1).mod(tabs.size.coerceAtLeast(1)))?.let { container.browser.select(it.id) }
                Shortcut.PreviousTab -> tabs.getOrNull((i - 1).mod(tabs.size.coerceAtLeast(1)))?.let { container.browser.select(it.id) }
                Shortcut.Back -> if (!currentLocked) container.browser.goBack()
                Shortcut.Forward -> if (!currentLocked) container.browser.goForward()
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
    BackHandler(enabled = fullscreen && navigator.isEmpty) { tab?.id?.let { container.sessions.session(it)?.exitFullScreen() } }
    val canGoBack = tab != null && (tab.canGoBack || tab.parentId != null) && !locked && !chrome.anyOverlay && navigator.isEmpty && !fullscreen
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
        val onPage = tab != null && tab.url.isNotEmpty()

        // The status area wears whatever the page shows along its top edge, so it always matches the
        // site. Until the page has painted, its theme-colour (or the plain background) stands in.
        val themeTint = tab?.themeColor?.takeIf { onPage }?.let { Color(it) }
        val statusTarget = when {
            !onPage || !settings.tintToolbarWithPage -> colors.background
            else -> chrome.edges?.top ?: themeTint ?: colors.background
        }
        // Read in the draw phase, so the colour can glide without recomposing the screen each frame.
        val statusColorState = animateColorAsState(statusTarget, Motion.fade(280), label = "statusColor")
        val lightStatusIcons by remember { derivedStateOf { statusColorState.value.luminance() < 0.5f } }

        // Glass reads best when its tone follows the page behind it: dark glass over dark pages.
        val behindLuma = chrome.edges?.bottomLuma?.takeIf { onPage }
        var barDark by remember { mutableStateOf(colors.isDark) }
        LaunchedEffect(behindLuma, colors.isDark) {
            barDark = when {
                behindLuma == null -> colors.isDark
                behindLuma < 0.40f -> true
                behindLuma > 0.52f -> false
                else -> barDark
            }
        }
        val barColors = when {
            private -> PrivateColors
            barDark -> DarkColors
            else -> LightColors
        }
        StatusBarAppearance(
            lightStatusIcons = if (navigator.isEmpty) lightStatusIcons else colors.isDark,
            lightNavIcons = if (navigator.isEmpty) barDark else colors.isDark,
        )

        val sameMode = state.tabsIn(private)
        val index = sameMode.indexOfFirst { it.id == tab?.id }

        run {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            // Page: truly edge to edge, under the status bar and the floating bar.
            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { chrome.pageRect = it.boundsInRoot() },
            ) {
                EngineView(
                    tab = tab,
                    modifier = Modifier.fillMaxSize(),
                    coverColor = colors.background.toArgb(),
                    hidden = locked,
                    onViewCreated = { view ->
                        chrome.geckoView = view
                        view.setDynamicToolbarMaxHeight(dynamicPx.toInt())
                    },
                )
                if (tab == null || tab.url.isEmpty()) {
                    HomePage(
                        private = private,
                        contentPadding = PaddingValues(bottom = BarMetrics.zone + navBottom + 16.dp),
                        onOpen = { container.browser.submit(it) },
                        onSearch = {
                            if (locked) {
                                unlockPrivate()
                            } else {
                                editText = ""
                                chrome.editing = true
                            }
                        },
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
                    PrivateLockCover(onUnlock = unlockPrivate)
                }
            }

            // Status area: the page runs underneath it, and a soft veil of the page's own top colour
            // (strongest at the very top, gone a little below the icons) keeps the clock and battery
            // legible without a solid band or a shadow, as in the native apps.
            if (!fullscreen) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(statusTop + 16.dp)
                        .drawBehind {
                            val tint = statusColorState.value
                            drawRect(
                                Brush.verticalGradient(
                                    0f to tint.copy(alpha = 0.9f),
                                    (statusTop.toPx() / size.height).coerceIn(0.2f, 0.9f) to tint.copy(alpha = 0.55f),
                                    1f to tint.copy(alpha = 0f),
                                ),
                            )
                        },
                )
            }

            // Floating bar.
            AnimatedVisibility(
                visible = !fullscreen && !chrome.findInPage && !chrome.editing,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(Motion.bouncy()) { it } + fadeIn(Motion.fade()),
                exit = slideOutVertically(Motion.smooth()) { it } + fadeOut(Motion.fade()),
            ) {
                CompositionLocalProvider(LocalPaneColors provides barColors) {
                    BottomBar(
                        tab = tab,
                        tabs = sameMode,
                        chrome = chrome,
                        navBarHeight = navBottom,
                        locked = locked,
                        private = private,
                        onAddress = {
                            // A locked page's address is never shown; tapping the bar offers to unlock instead.
                            if (locked) {
                                unlockPrivate()
                            } else {
                                editText = tab?.url?.let { url -> container.browser.searchTermsFor(url) ?: UrlDisplay.editableText(url) }.orEmpty()
                                chrome.editing = true
                            }
                        },
                        onBack = {
                            tab?.let { t ->
                                if (t.canGoBack) container.browser.goBack() else if (t.parentId != null) container.browser.close(t.id)
                            }
                        },
                        onTabs = { chrome.showTabs = true },
                        onNewTab = {
                            container.browser.newTab(private)
                            editText = ""
                            chrome.editing = true
                        },
                        onMenu = { chrome.showMenu = true },
                        onReload = { container.browser.reload() },
                        onStop = { container.browser.stop() },
                        onSiteInfo = { chrome.siteInfo = true },
                        onSwipeStart = { _ ->
                            swipeCapture[0]?.cancel()
                            swipeCapture[0] = scope.launch {
                                val snap = chrome.capture(120)
                                // The swipe may be over by now: a snapshot arriving late must not be left on screen.
                                if (isActive) chrome.overlay = PageOverlay(snap, null, kind = PageOverlay.Kind.TabSwipe)
                            }
                        },
                        onSwipeCommit = { target ->
                            swipeCapture[0]?.cancel()
                            tab?.takeIf { it.url.isNotEmpty() }?.let { t ->
                                chrome.overlay?.current?.let { bmp -> scope.launch { container.thumbnails.put(t.id, bmp, t.isPrivate) } }
                            }
                            if (target == null) {
                                // A blank tab already is the new tab; don't pile up empty ones.
                                if (tab?.url.isNullOrEmpty().not()) container.browser.newTab(private)
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
                        onSwipeCancel = {
                            swipeCapture[0]?.cancel()
                            if (chrome.overlay?.kind == PageOverlay.Kind.TabSwipe) chrome.overlay = null
                        },
                    )
                }
            }

            if (chrome.findInPage && tab != null && !locked) {
                FindInPageBar(tabId = tab.id, onClose = { chrome.findInPage = false }, modifier = Modifier.align(Alignment.BottomCenter))
            }

            AddressEditor(
                visible = chrome.editing && !locked,
                origin = chrome.pillRect,
                initialText = if (locked) "" else editText,
                private = private,
                showOpenTabs = !locked,
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

            androidx.compose.runtime.CompositionLocalProvider(
                app.pane.browser.ui.components.LocalSheetOrigin provides chrome.menuRect.takeIf { it.width > 1f }?.center,
            ) {
            MenuSheet(
                visible = chrome.showMenu,
                tab = tab,
                locked = locked,
                onDismiss = { chrome.showMenu = false },
                onFindInPage = { chrome.findInPage = true },
                onNewTab = { p ->
                    container.browser.newTab(p)
                    editText = ""
                    chrome.editing = true
                },
            )
            }

            SiteInfoSheet(visible = chrome.siteInfo && !locked, tabId = tab?.id, onDismiss = { chrome.siteInfo = false })
        }
        }

        TabSwitcher(
            visible = chrome.showTabs,
            chrome = chrome,
            privateLocked = privateLocked,
            onRequestUnlock = unlockPrivate,
            onClosed = {
                chrome.showTabs = false
                // Start from the normal tabs next time, so opening from one never flips screenshot blocking.
                chrome.showPrivateTabs = false
            },
            onNewTab = { p ->
                container.browser.newTab(p)
                editText = ""
                chrome.editing = true
            },
        )

        Onboarding(visible = !settings.onboardingDone, onDone = { })
    }
}

private suspend fun captureInto(container: app.pane.browser.AppContainer, chrome: BrowserChrome, tab: TabState, bandPx: Int) {
    val bitmap = chrome.capture() ?: return
    val current = container.store.state.value.selectedTab
    if (current?.id != tab.id || current.url != tab.url) return
    chrome.applyEdges(tab.id, bitmap, bandPx)
    container.snapshots.put(tab.id, tab.url, bitmap)
    container.thumbnails.put(tab.id, bitmap, tab.isPrivate)
    container.store.updateTab(tab.id) { it.copy(thumbnailVersion = it.thumbnailVersion + 1) }
}

/** Light or dark system bar icons to suit whatever is behind them. */
@Composable
private fun StatusBarAppearance(lightStatusIcons: Boolean, lightNavIcons: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !lightStatusIcons
        controller.isAppearanceLightNavigationBars = !lightNavIcons
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
