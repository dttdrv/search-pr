package app.pane.browser.ui.browser

import android.app.role.RoleManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.ButtonStyle
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.IconTile
import app.pane.browser.ui.components.PrimaryButton
import app.pane.browser.ui.components.SegmentedControl
import app.pane.browser.ui.components.ToggleRow
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.core.extensions.Amo
import app.pane.core.search.SearchEngines

/**
 * First launch: what Pane is, three choices that matter (search engine, content blocker, default
 * browser), then out of the way. Everything here can be changed later in Settings.
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

private val choices = listOf(SearchEngines.DuckDuckGo, SearchEngines.Startpage, SearchEngines.Brave)

@Composable
private fun OnboardingContent(onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val colors = PaneTheme.colors
    var engine by remember { mutableIntStateOf(0) }
    var blocker by remember { mutableStateOf(true) }
    val appear = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        androidx.compose.animation.core.animate(0f, 1f, animationSpec = Motion.spring(0.7f, 0.85f)) { v, _ -> appear.floatValue = v }
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    fun finish() {
        container.settings.update { it.copy(searchEngineId = choices[engine].id, onboardingDone = true) }
        if (blocker) {
            // The add-on's permissions are confirmed through the normal install sheet.
            container.extensions.install(Amo.latestXpiUrl("ublock-origin"), slug = "ublock-origin")
        }
        onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.groupedBackground)
            .clickable(remember { MutableInteractionSource() }, indication = null) { },
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                    Spacer(Modifier.height(36.dp))
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .graphicsLayer {
                                val a = appear.floatValue
                                alpha = a
                                scaleX = 0.7f + 0.3f * a
                                scaleY = 0.7f + 0.3f * a
                            }
                            .size(76.dp)
                            .clip(PaneShapes.card)
                            .background(Color(0xFF0B0B0F)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(PaneIcons.Globe, null, tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Welcome to Pane",
                        style = PaneTheme.type.largeTitle,
                        color = colors.label,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = appear.floatValue },
                    )
                    Spacer(Modifier.height(24.dp))
                    Column(
                        Modifier.padding(horizontal = 16.dp).graphicsLayer {
                            alpha = appear.floatValue
                            translationY = (1f - appear.floatValue) * 40f
                        },
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Feature(PaneIcons.ShieldCheck, colors.positive, "Private by default", "Strict tracking protection, isolated cookies, HTTPS-only and encrypted DNS. No telemetry, ever.")
                        Feature(PaneIcons.Puzzle, Color(0xFFFF9F0A), "Real extensions", "Install Firefox add-ons like uBlock Origin, Bitwarden and Dark Reader.")
                        Feature(PaneIcons.Tabs, colors.accent, "Built for your thumb", "The address bar lives at the bottom. Swipe it to switch tabs, swipe up for all tabs.")
                    }
                    Spacer(Modifier.height(8.dp))
                    GroupedSection(header = "Search engine") {
                        row {
                            Box(Modifier.padding(12.dp)) {
                                SegmentedControl(choices.map { it.name }, engine, { engine = it }, Modifier.fillMaxWidth())
                            }
                        }
                    }
                    GroupedSection(footer = "Downloads uBlock Origin from addons.mozilla.org. You'll be asked to approve its permissions.") {
                        row {
                            ToggleRow(
                                "Block ads with uBlock Origin",
                                checked = blocker,
                                onCheckedChange = { blocker = it },
                                leading = { IconTile(PaneIcons.Shield, Color(0xFF800000)) },
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
            // Actions stay put at the bottom, like an iOS setup screen.
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.groupedBackground)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PrimaryButton("Start Browsing", onClick = ::finish, modifier = Modifier.widthIn(max = 480.dp))
                if (Build.VERSION.SDK_INT >= 29) {
                    val roles = context.getSystemService(RoleManager::class.java)
                    if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_BROWSER) && !roles.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                        PrimaryButton(
                            "Make Pane Your Default Browser",
                            style = ButtonStyle.Plain,
                            onClick = { roleLauncher.launch(roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER)) },
                            modifier = Modifier.widthIn(max = 480.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, tint: Color, title: String, body: String) {
    val colors = PaneTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(30.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = PaneTheme.type.headline, color = colors.label)
            Text(body, style = PaneTheme.type.subheadline, color = colors.secondaryLabel)
        }
    }
}

