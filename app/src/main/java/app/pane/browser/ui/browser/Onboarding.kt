package app.pane.browser.ui.browser

import android.app.role.RoleManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.DotText
import app.pane.browser.ui.components.EngineIcon
import app.pane.browser.ui.components.PaneSwitch
import app.pane.browser.ui.components.PrimaryButton
import app.pane.browser.ui.components.SectionLabel
import app.pane.browser.ui.components.Separator
import app.pane.browser.ui.components.StatusDot
import app.pane.browser.ui.components.TextButton
import app.pane.browser.ui.components.pressDim
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.entrance
import app.pane.core.extensions.Amo
import app.pane.core.search.SearchEngines

/**
 * First launch: what Pane is in three lines, then three choices that matter (search engine,
 * content blocker, default browser), then out of the way. Plain type and hairlines, no boxes.
 * Everything here can be changed later in Settings.
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

private val points = listOf(
    "One floating bar for everything",
    "Firefox extensions, like uBlock Origin",
    "Your passwords stay with your phone",
)

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
                    Spacer(Modifier.height(24.dp))
                    DotText("PANE", Modifier.entrance(0), dot = 3.dp)
                    Spacer(Modifier.height(44.dp))
                    Text("Welcome", style = PaneTheme.type.largeTitle, color = colors.label, modifier = Modifier.entrance(1))
                    Spacer(Modifier.height(24.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        points.forEachIndexed { i, line -> Point(line, Modifier.entrance(2 + i)) }
                    }

                    Spacer(Modifier.height(44.dp))
                    SectionLabel("Search with", Modifier.entrance(5).padding(bottom = 10.dp))
                    Column(Modifier.entrance(6).selectableGroup()) {
                        Separator()
                        choices.forEachIndexed { i, c ->
                            EngineRow(c.id, c.name, selected = i == engine, onClick = { engine = i })
                            Separator()
                        }
                    }

                    Spacer(Modifier.height(28.dp))
                    Column(Modifier.entrance(7)) {
                        Separator()
                        ToggleLine("Block ads with uBlock Origin", checked = blocker, onCheckedChange = { blocker = it })
                        Separator()
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
            // Actions stay put at the bottom, like a setup screen.
            Column(
                Modifier
                    .entrance(8)
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

/** One short line led by a small ink dot. */
@Composable
private fun Point(text: String, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StatusDot(color = colors.label, size = 6.dp)
        Text(text, style = PaneTheme.type.body, color = colors.label)
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
