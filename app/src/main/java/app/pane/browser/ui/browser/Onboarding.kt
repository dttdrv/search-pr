package app.pane.browser.ui.browser

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.pane.browser.LocalAppContainer
import app.pane.browser.R
import app.pane.browser.ui.components.EngineIcon
import app.pane.browser.ui.components.PaneSwitch
import app.pane.browser.ui.components.SectionLabel
import app.pane.browser.ui.components.Separator
import app.pane.browser.ui.components.pressDim
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.LocalReduceMotion
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.Spacing
import app.pane.browser.ui.theme.entrance
import app.pane.browser.ui.theme.rememberHaptics
import app.pane.core.search.SearchEngines
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * first launch, as a carousel you swipe, with nothing to press: a welcome, three small films of the gestures
 * worth knowing, then the two choices that matter. the app's name is one element for the whole flow, big in
 * the middle of the welcome and following the swipe up into the header. swiping up on the last page lifts the
 * carousel away. everything chosen here can be changed in settings.
 */
@Composable
fun Onboarding(visible: Boolean, onDone: () -> Unit) {
    val state = remember { MutableTransitionState(visible) }
    state.targetState = visible
    if (!state.currentState && !state.targetState && state.isIdle) return
    // it carries on up from wherever the finger left it
    val exit = if (LocalReduceMotion.current) fadeOut(Motion.fade(0)) else fadeOut(Motion.fade(260)) + slideOutVertically(Motion.pushOffset) { -it / 6 }
    AnimatedVisibility(visibleState = state, exit = exit) { OnboardingContent(onDone) }
}

private val choices = SearchEngines.defaults

private class Intro(val scene: Scene, val title: String, val line: String)

private val intro = listOf(
    Intro(Scene.Bar, "Just the page", "The bar steps aside as you scroll."),
    Intro(Scene.Sideways, "Swipe the bar", "Left for the next tab, then a new one."),
    Intro(Scene.Up, "Swipe it up", "All your tabs, at a glance."),
)

// the welcome, the films, then the choices
private val pageCount = intro.size + 2
private val lastPage = pageCount - 1

private val HeaderHeight = 56.dp

/** the name in the welcome, as a multiple of its size in the header */
private const val HeroScale = 2.4f

/** one loop of the prompt's shimmer, and the share of it the band spends crossing the hint */
private const val PromptMs = 2400
private const val Sweep = 0.7f

@Composable
private fun OnboardingContent(onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val colors = PaneTheme.colors
    val type = PaneTheme.type
    val name = stringResource(R.string.app_name)
    val still = rememberStill()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val pager = rememberPagerState(pageCount = { pageCount })
    var engine by remember { mutableIntStateOf(0) }
    var blocker by remember { mutableStateOf(true) }
    var request by remember { mutableStateOf(defaultRoleIntent(context)) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { request = defaultRoleIntent(context) }

    fun finish() {
        container.settings.update { it.copy(searchEngineId = choices[engine].id, blockAds = blocker, onboardingDone = true) }
        onDone()
    }

    val last = pager.currentPage == lastPage
    fun advance() {
        if (last) finish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
    }

    // swiping up on the last page pulls the carousel up with the finger; let go past the threshold and it finishes
    var pull by remember { mutableFloatStateOf(0f) }
    val threshold = with(density) { 64.dp.toPx() }
    val finishNow by rememberUpdatedState(::finish)
    val connection = remember(pager) {
        object : NestedScrollConnection {
            // swiping back down gives the pull up before the page scrolls
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val back = min(pull, max(available.y, 0f))
                pull -= back
                return Offset(0f, back)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (pager.currentPage == lastPage && source == NestedScrollSource.UserInput && available.y < 0f) {
                    val before = pull
                    pull -= available.y
                    if (before < threshold && pull >= threshold) haptics.tick()
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull >= threshold) finishNow() else if (pull > 0f) scope.launch { animate(pull, 0f, animationSpec = Motion.smooth()) { v, _ -> pull = v } }
                return Velocity.Zero
            }
        }
    }

    // the name starts in the middle of the pager and ends in the header; both are read once laid out
    var origin by remember { mutableStateOf(Offset.Zero) }
    var stage by remember { mutableStateOf(Rect.Zero) }
    var header by remember { mutableStateOf(Rect.Zero) }
    var word by remember { mutableStateOf(IntSize.Zero) }
    // as big as HeroScale, unless a longer name would not fit between the gutters
    val hero = if (word.width == 0) HeroScale else min(HeroScale, (stage.width - 2 * with(density) { Spacing.gutter.toPx() }) / word.width)
    val small = type.title3.fontSize.value / type.largeTitle.fontSize.value
    val arrival = rememberArrival(NameAt, still)
    val footer = rememberArrival(FooterAt, still)

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .clickable(remember { MutableInteractionSource() }, indication = null) { }
            .onGloballyPositioned { origin = it.positionInRoot() },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .nestedScroll(connection)
                .graphicsLayer { translationY = -pull / 2 },
        ) {
            // the name's place in the header; it is drawn above, once for every page, and read from here (a layer's
            // transform does not move a node's accessibility bounds)
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(HeaderHeight)
                    .semantics { heading(); contentDescription = name }
                    .onGloballyPositioned { header = it.boundsInRoot() },
            )

            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { stage = it.boundsInRoot() },
                beyondViewportPageCount = 1,
            ) { page ->
                // How far this page is from being the one in view, read while drawing.
                val away = { (page - pager.currentPage) - pager.currentPageOffsetFraction }
                when {
                    page == 0 -> WelcomePage(name, with(density) { (word.height * hero / 2).toDp() }, away, still)
                    page < lastPage -> IntroPage(intro[page - 1], pager.settledPage == page, away)
                    else -> SetupPage(engine, { engine = it }, blocker, { blocker = it }, name, request?.let { { roleLauncher.launch(it) } })
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.gutter / 2)
                    .graphicsLayer { alpha = footer.value },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Indicator(pager)
                Spacer(Modifier.height(Spacing.gutter / 2))
                SwipePrompt(
                    hint = when {
                        pager.currentPage == 0 -> "Swipe to begin"
                        last -> "Swipe up to start"
                        else -> "Swipe"
                    },
                    up = last,
                    still = still,
                    description = if (last) "Finish" else "Next",
                    state = "Page ${pager.currentPage + 1} of $pageCount",
                    onTap = ::advance,
                )
            }
        }

        Text(
            name,
            style = type.largeTitle,
            color = colors.label,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .clearAndSetSemantics { }
                .onSizeChanged { word = it }
                .graphicsLayer {
                    // driven by the pager, not a clock: it follows the finger from the welcome to the next page, and back
                    val travelled = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                    val a = arrival.value
                    val scale = hero * (small / hero).pow(travelled) * (0.94f + 0.06f * a)
                    scaleX = scale
                    scaleY = scale
                    // laid out at the top left and scaled about its middle, so the middle is what travels
                    val from = stage.center - origin
                    val to = header.center - origin
                    translationX = lerp(from.x, to.x, travelled) - size.width / 2
                    translationY = lerp(from.y, to.y, travelled) - size.height / 2 + (1f - a) * 12.dp.toPx() - pull / 2
                    alpha = if (stage == Rect.Zero) 0f else a
                },
        )
    }
}

/**
 * a clock that runs 0 to 1 every [ms] while [run], and rests at 0 otherwise. it follows the frames, not
 * the animation scale, so the films keep showing how to use the browser when a phone has its animations off
 */
@Composable
internal fun rememberLoop(ms: Int, run: Boolean): State<Float> {
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(ms, run) {
        clock.floatValue = 0f
        if (!run) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) withFrameNanos { clock.floatValue = ((it - start) / (ms * 1_000_000f)) % 1f }
    }
    return clock
}

/** no looping decoration: the app's reduce-motion setting. the films that teach are not decoration, they keep playing */
@Composable
internal fun rememberStill(): Boolean = LocalReduceMotion.current

/** when each piece of the welcome arrives, in ms: the greeting, the name a beat later, the closing line, the prompt */
private const val GreetingAt = 0
private const val NameAt = 160
private const val ClosingAt = 340
private const val FooterAt = 760

/** 0 to 1 on the app's spring [after] a delay, or a quick fade where motion is off */
@Composable
private fun rememberArrival(after: Int, still: Boolean): Animatable<Float, AnimationVector1D> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (!still) delay(after.toLong())
        progress.animateTo(1f, if (still) Motion.fade(160) else Motion.smooth())
    }
    return progress
}

/** rises into place as [arrival] runs, and fades [fade] times as fast as the page leaves */
private fun Modifier.arriving(arrival: Animatable<Float, AnimationVector1D>, away: () -> Float, still: Boolean, fade: Float) = graphicsLayer {
    val a = away()
    alpha = (arrival.value * (1f - abs(a) * fade)).coerceIn(0f, 1f)
    translationX = a * size.width * 0.12f
    if (!still) translationY = (1f - arrival.value) * 8.dp.toPx()
}

/** the greeting either side of the name, which [OnboardingContent] draws; [half] is half its height, so both lines sit the same distance from it */
@Composable
private fun WelcomePage(name: String, half: Dp, away: () -> Float, still: Boolean) {
    val colors = PaneTheme.colors
    val greeting = rememberArrival(GreetingAt, still)
    val closing = rememberArrival(ClosingAt, still)
    val lines = PaneTheme.type.title3.copy(fontWeight = FontWeight.Normal)
    Column(
        Modifier
            .fillMaxSize()
            .clearAndSetSemantics { contentDescription = "Welcome to $name, let's show you the ropes" },
    ) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = half + Spacing.gutter / 2), contentAlignment = Alignment.BottomCenter) {
            // gone before the name, on its way up, has crossed it
            Text("Welcome to", style = lines, color = colors.secondaryLabel, modifier = Modifier.arriving(greeting, away, still, fade = 6f))
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(top = half + Spacing.gutter / 2), contentAlignment = Alignment.TopCenter) {
            Text("Let's show you the ropes", style = lines, color = colors.secondaryLabel, modifier = Modifier.arriving(closing, away, still, fade = 1.4f))
        }
    }
}

/** a film, a title and one line; the film drifts and fades as the page leaves, and plays while the page is the one in view */
@Composable
private fun IntroPage(page: Intro, active: Boolean, away: () -> Float) {
    val colors = PaneTheme.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = Spacing.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // the film takes all the height the words leave
        OnboardingArt(
            page.scene,
            active,
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = Spacing.gutter)
                .graphicsLayer {
                    val a = away()
                    translationX = a * size.width * 0.35f
                    alpha = (1f - abs(a) * 1.1f).coerceIn(0f, 1f)
                },
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.gutter)
                .graphicsLayer {
                    val a = away()
                    translationX = a * size.width * 0.12f
                    alpha = (1f - abs(a) * 1.4f).coerceIn(0f, 1f)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(page.title, style = PaneTheme.type.largeTitle, color = colors.label, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Spacing.gutter / 2))
            Text(page.line, style = PaneTheme.type.body, color = colors.secondaryLabel, textAlign = TextAlign.Center)
        }
    }
}

/** the search engine as plain rows, one switch, and the default browser row while this isn't it */
@Composable
private fun SetupPage(
    engine: Int,
    onEngine: (Int) -> Unit,
    blocker: Boolean,
    onBlocker: (Boolean) -> Unit,
    name: String,
    onDefault: (() -> Unit)?,
) {
    val colors = PaneTheme.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = Spacing.gutter)) {
            Spacer(Modifier.height(Spacing.gutter))
            Text("Make it yours", style = PaneTheme.type.largeTitle, color = colors.label, modifier = Modifier.entrance(0))
            Spacer(Modifier.height(Spacing.gutter * 2))
            SectionLabel("Search with", Modifier.entrance(1).padding(bottom = Spacing.gutter / 2))
            Column(Modifier.entrance(2).selectableGroup()) {
                Separator()
                choices.forEachIndexed { i, c ->
                    EngineRow(c.id, c.name, selected = i == engine, onClick = { onEngine(i) })
                    Separator()
                }
            }
            Spacer(Modifier.height(Spacing.gutter * 2))
            Column(Modifier.entrance(3)) {
                Separator()
                ToggleLine("Block ads", checked = blocker, onCheckedChange = onBlocker)
                Separator()
                if (onDefault != null) {
                    DefaultRow("Make $name your default browser", onDefault)
                    Separator()
                }
            }
            Spacer(Modifier.height(Spacing.gutter))
        }
    }
}

/** Page dots: faint grey, and the one in view stretches into an ink pill, handing over to the next as you swipe. */
@Composable
private fun Indicator(pager: PagerState, modifier: Modifier = Modifier) {
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
                        lerp(colors.faint, colors.label, near),
                        Offset(x, 0f),
                        Size(w, dot.toPx()),
                        CornerRadius(dot.toPx() / 2f),
                    )
                    x += w + gap.toPx()
                }
            },
    )
}

/**
 * what to do next, without a button: a few muted words with a band of the accent sweeping through them, like
 * a chat's "thinking" line, and a chevron drifting the way to swipe. both run off one clock, read while
 * drawing; with motion off they stand still. a tap, or the accessibility action, does what the swipe does.
 */
@Composable
private fun SwipePrompt(hint: String, up: Boolean, still: Boolean, description: String, state: String, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    val clock = rememberLoop(PromptMs, run = !still)
    Crossfade(
        targetState = hint to up,
        modifier = modifier
            .widthIn(min = 160.dp)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClickLabel = description, onClick = onTap)
            .clearAndSetSemantics {
                contentDescription = description
                stateDescription = state
                role = Role.Button
                onClick(description) { onTap(); true }
            }
            // SrcAtop paints the band onto the words only, so they need a layer of their own
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (still) return@drawWithContent
                val band = size.width * 0.6f
                val x = lerp(-band, size.width, (clock.value / Sweep).coerceAtMost(1f))
                drawRect(
                    Brush.horizontalGradient(listOf(colors.accent.copy(alpha = 0f), colors.accent, colors.accent.copy(alpha = 0f)), startX = x, endX = x + band),
                    blendMode = BlendMode.SrcAtop,
                )
            },
        animationSpec = Motion.fade(),
        label = "hint",
    ) { (text, vertical) ->
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        ) {
            Text(text, style = PaneTheme.type.callout, color = colors.secondaryLabel)
            Icon(
                if (vertical) PaneIcons.ChevronUp else PaneIcons.ChevronRight,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        val f = if (still) 0.5f else clock.value
                        val drift = lerp(-6.dp.toPx(), 6.dp.toPx(), f)
                        if (vertical) translationY = -drift else translationX = drift
                        alpha = if (still) 1f else sin(PI.toFloat() * f)
                    },
            )
        }
    }
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
        RadioButton(selected = selected, onClick = null)
    }
}

/** A single setting row: the title and a switch, the whole row toggles. */
@Composable
private fun ToggleLine(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 60.dp)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Switch) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = PaneTheme.type.body, color = colors.label, modifier = Modifier.weight(1f))
        PaneSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** asks the phone to make this the default browser */
@Composable
private fun DefaultRow(title: String, onClick: () -> Unit) {
    val colors = PaneTheme.colors
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 60.dp).pressDim(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(PaneIcons.Globe, null, tint = colors.secondaryLabel, modifier = Modifier.size(22.dp))
        Text(title, style = PaneTheme.type.body, color = colors.label, modifier = Modifier.weight(1f))
        Icon(PaneIcons.ChevronRight, null, tint = colors.tertiaryLabel, modifier = Modifier.size(18.dp))
    }
}

/** the request to make this the default browser; null where it already is, or the phone can't be asked */
private fun defaultRoleIntent(context: Context): Intent? {
    if (Build.VERSION.SDK_INT < 29) return null
    val roles = context.getSystemService(RoleManager::class.java) ?: return null
    return if (roles.isRoleAvailable(RoleManager.ROLE_BROWSER) && !roles.isRoleHeld(RoleManager.ROLE_BROWSER)) {
        roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
    } else {
        null
    }
}
