package app.pane.browser.ui.browser

import android.app.role.RoleManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.DotText
import app.pane.browser.ui.components.EngineIcon
import app.pane.browser.ui.components.PaneSwitch
import app.pane.browser.ui.components.PrimaryButton
import app.pane.browser.ui.components.SectionLabel
import app.pane.browser.ui.components.Separator
import app.pane.browser.ui.components.TextButton
import app.pane.browser.ui.components.pressDim
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.entrance
import app.pane.core.extensions.Amo
import app.pane.core.search.SearchEngines
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * First launch, as a short carousel: what the bar is, then the two gestures worth knowing (swipe it
 * sideways, swipe it up), then the two choices that matter (search engine, content blocker). One
 * drawing and a few words a page, never a paragraph. Everything here can be changed in Settings.
 */
@Composable
fun Onboarding(visible: Boolean, onDone: () -> Unit) {
    val state = remember { MutableTransitionState(visible) }
    state.targetState = visible
    if (!state.currentState && !state.targetState && state.isIdle) return
    AnimatedVisibility(
        visibleState = state,
        exit = fadeOut(Motion.fade(260)) + scaleOut(Motion.smooth(), targetScale = 1.06f),
    ) {
        OnboardingContent(onDone)
    }
}

private val choices = SearchEngines.defaults

private class Intro(val scene: Scene, val title: String, val line: String)

private val intro = listOf(
    Intro(Scene.Bar, "Just the page", "One floating bar does the rest."),
    Intro(Scene.Sideways, "Swipe the bar", "Left for the next tab, then a new one."),
    Intro(Scene.Up, "Swipe it up", "All your tabs, at a glance."),
)

private val pageCount = intro.size + 1
private val lastPage = pageCount - 1

@Composable
private fun OnboardingContent(onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val colors = PaneTheme.colors
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { pageCount })
    var engine by remember { mutableIntStateOf(0) }
    var blocker by remember { mutableStateOf(true) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    fun finish() {
        container.settings.update { it.copy(searchEngineId = choices[engine].id, onboardingDone = true) }
        if (blocker) {
            // The add-on's permissions are confirmed through the normal install sheet.
            container.extensions.install(Amo.latestXpiUrl("ublock-origin"), slug = "ublock-origin")
        }
        onDone()
    }

    val onLast = pager.currentPage == lastPage

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .clickable(remember { MutableInteractionSource() }, indication = null) { },
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            // The mark on the left; a way out on the right until there is nothing left to skip.
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DotText("PANE", dot = 3.dp)
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(visible = !onLast, enter = fadeIn(Motion.fade()), exit = fadeOut(Motion.fade())) {
                    TextButton("Skip", onClick = { scope.launch { pager.animateScrollToPage(lastPage) } }, color = colors.secondaryLabel)
                }
            }

            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                beyondViewportPageCount = 1,
            ) { page ->
                // How far this page is from being the one in view, read while drawing.
                val away = { (page - pager.currentPage) - pager.currentPageOffsetFraction }
                if (page < intro.size) {
                    IntroPage(intro[page], away)
                } else {
                    SetupPage(engine, { engine = it }, blocker, { blocker = it })
                }
            }

            Indicator(pager, Modifier.align(Alignment.CenterHorizontally).padding(bottom = 20.dp))

            // The action stays put at the bottom; only its words change.
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PrimaryButton(
                    if (onLast) "Start Browsing" else "Next",
                    onClick = { if (onLast) finish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    modifier = Modifier.widthIn(max = 480.dp),
                )
                // Reserve the line so the button never jumps between pages.
                Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                    if (onLast && Build.VERSION.SDK_INT >= 29) {
                        val roles = context.getSystemService(RoleManager::class.java)
                        if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_BROWSER) && !roles.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                            TextButton(
                                "Make Pane your default browser",
                                onClick = { roleLauncher.launch(roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER)) },
                                color = colors.secondaryLabel,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A drawing, a title and one line. The drawing drifts and fades as the page leaves. */
@Composable
private fun IntroPage(page: Intro, away: () -> Float) {
    val colors = PaneTheme.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer {
                    val a = away()
                    translationX = a * size.width * 0.35f
                    alpha = (1f - abs(a) * 1.1f).coerceIn(0f, 1f)
                },
            contentAlignment = Alignment.Center,
        ) {
            OnboardingArt(page.scene, Modifier.widthIn(max = 320.dp))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp)
                .graphicsLayer {
                    val a = away()
                    translationX = a * size.width * 0.12f
                    alpha = (1f - abs(a) * 1.4f).coerceIn(0f, 1f)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(page.title, style = PaneTheme.type.largeTitle, color = colors.label, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(page.line, style = PaneTheme.type.body, color = colors.secondaryLabel, textAlign = TextAlign.Center)
        }
    }
}

/** The last page: the two choices, as plain rows. */
@Composable
private fun SetupPage(engine: Int, onEngine: (Int) -> Unit, blocker: Boolean, onBlocker: (Boolean) -> Unit) {
    val colors = PaneTheme.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(20.dp))
            Text("Make it yours", style = PaneTheme.type.largeTitle, color = colors.label, modifier = Modifier.entrance(0))
            Spacer(Modifier.height(36.dp))
            SectionLabel("Search with", Modifier.entrance(1).padding(bottom = 10.dp))
            Column(Modifier.entrance(2).selectableGroup()) {
                Separator()
                choices.forEachIndexed { i, c ->
                    EngineRow(c.id, c.name, selected = i == engine, onClick = { onEngine(i) })
                    Separator()
                }
            }
            Spacer(Modifier.height(28.dp))
            Column(Modifier.entrance(3)) {
                Separator()
                ToggleLine("Block ads with uBlock Origin", checked = blocker, onCheckedChange = onBlocker)
                Separator()
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Page dots: the one in view stretches into a pill, and hands over to the next as you swipe. */
@Composable
private fun Indicator(pager: androidx.compose.foundation.pager.PagerState, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    val dot = 6.dp
    val wide = 22.dp
    val gap = 8.dp
    Box(
        modifier
            .size(width = wide + (dot + gap) * (pageCount - 1), height = dot)
            .drawBehind {
                val position = pager.currentPage + pager.currentPageOffsetFraction
                var x = 0f
                for (i in 0 until pageCount) {
                    val near = (1f - abs(position - i)).coerceIn(0f, 1f)
                    val w = dot.toPx() + (wide - dot).toPx() * near
                    drawRoundRect(
                        colors.label.copy(alpha = 0.22f + 0.78f * near),
                        Offset(x, 0f),
                        Size(w, dot.toPx()),
                        CornerRadius(dot.toPx() / 2f),
                    )
                    x += w + gap.toPx()
                }
            },
    )
}

/** A search engine: its mark, its name and a radio dot at the end. */
@Composable
private fun EngineRow(id: String, name: String, selected: Boolean, onClick: () -> Unit) {
    val colors = PaneTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .semantics { this.selected = selected }
            .pressDim(role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        EngineIcon(id, 28.dp)
        Text(name, style = PaneTheme.type.body, color = colors.label, modifier = Modifier.weight(1f))
        RadioDot(selected)
    }
}

/** A hairline circle that fills with ink, from the middle out, when chosen. */
@Composable
private fun RadioDot(selected: Boolean) {
    val colors = PaneTheme.colors
    val fill by animateFloatAsState(if (selected) 1f else 0f, Motion.snappy(), label = "radio")
    Box(
        Modifier
            .size(22.dp)
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawCircle(
                    colors.label.copy(alpha = 0.3f + 0.7f * fill),
                    radius = size.minDimension / 2f - stroke / 2f,
                    style = Stroke(stroke),
                )
                if (fill > 0f) {
                    drawCircle(colors.label, radius = (size.minDimension / 2f - 5.dp.toPx()) * fill)
                }
            },
    )
}

/** A single setting row: the title and a switch, the whole row toggles. */
@Composable
private fun ToggleLine(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Switch) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = PaneTheme.type.body, color = colors.label, modifier = Modifier.weight(1f))
        PaneSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
