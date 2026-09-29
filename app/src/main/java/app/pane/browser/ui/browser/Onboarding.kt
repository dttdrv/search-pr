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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.PrimaryButton
import app.pane.browser.ui.components.SegmentedControl
import app.pane.browser.ui.components.TextButton
import app.pane.browser.ui.components.ToggleRow
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.entrance
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

private val choices = SearchEngines.defaults

@Composable
private fun OnboardingContent(onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val colors = PaneTheme.colors
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

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .clickable(remember { MutableInteractionSource() }, indication = null) { },
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                    Spacer(Modifier.height(56.dp))
                    Text("Welcome to Pane", style = PaneTheme.type.largeTitle, color = colors.label, modifier = Modifier.entrance(0))
                    Spacer(Modifier.height(32.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        Statement(
                            "Private by default",
                            "Strict tracking protection, isolated cookies, HTTPS-only and encrypted DNS. No telemetry, ever.",
                            Modifier.entrance(1),
                        )
                        Statement(
                            "Real extensions",
                            "Install Firefox add-ons like uBlock Origin and Dark Reader.",
                            Modifier.entrance(2),
                        )
                        Statement(
                            "Built for your thumb",
                            "The address bar lives at the bottom. Swipe it to switch tabs, swipe up for all tabs.",
                            Modifier.entrance(3),
                        )
                    }
                    Spacer(Modifier.height(36.dp))
                    Column(Modifier.entrance(4)) {
                        Text(
                            "Search engine",
                            style = PaneTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.secondaryLabel,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                        )
                        SegmentedControl(choices.map { it.name }, engine, { engine = it }, Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(24.dp))
                    Column(
                        Modifier
                            .entrance(5)
                            .fillMaxWidth()
                            .clip(PaneShapes.large)
                            .background(colors.surface),
                    ) {
                        ToggleRow(
                            "Block ads with uBlock Origin",
                            checked = blocker,
                            onCheckedChange = { blocker = it },
                            subtitle = "Downloads uBlock Origin from addons.mozilla.org. You'll be asked to approve its permissions.",
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
            // Actions stay put at the bottom, like a setup screen.
            Column(
                Modifier
                    .entrance(6)
                    .fillMaxWidth()
                    .background(colors.background)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PrimaryButton("Start Browsing", onClick = ::finish, modifier = Modifier.widthIn(max = 480.dp))
                if (Build.VERSION.SDK_INT >= 29) {
                    val roles = context.getSystemService(RoleManager::class.java)
                    if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_BROWSER) && !roles.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                        TextButton(
                            "Make Pane Your Default Browser",
                            onClick = { roleLauncher.launch(roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER)) },
                        )
                    }
                }
            }
        }
    }
}

/** A short statement: one bold line, one quiet paragraph. No icon. */
@Composable
private fun Statement(title: String, body: String, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    Column(modifier.fillMaxWidth()) {
        Text(title, style = PaneTheme.type.title3, color = colors.label)
        Spacer(Modifier.height(4.dp))
        Text(body, style = PaneTheme.type.body, color = colors.secondaryLabel)
    }
}
