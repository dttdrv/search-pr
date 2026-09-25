package app.pane.browser.ui.settings

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.IconTile
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.components.ListRow
import app.pane.browser.ui.components.LocalToasts
import app.pane.browser.ui.components.PrimaryButton
import app.pane.browser.ui.components.ToggleRow
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.library.TileColors
import app.pane.browser.ui.library.rememberBackLabel
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.core.library.ProtectionSummary
import app.pane.core.search.SearchEngines
import app.pane.core.settings.ThemeMode

/** The settings root, laid out like iOS Settings: coloured glyph tiles, one row per area. */
@Composable
fun SettingsScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val settings by rememberSettingsState()
    val backLabel = rememberBackLabel(Route.Settings)
    val engine = SearchEngines.byId(settings.searchEngineId)
    val protection = remember(settings) { ProtectionSummary.of(settings) }
    val context = LocalContext.current
    var autofill by remember { mutableStateOf(AutofillStatus.read(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { autofill = AutofillStatus.read(context) }

    LargeTitleScaffold(title = "Settings", onBack = navigator::pop, backLabel = backLabel) {
        item(key = "default") { DefaultBrowserCard() }
        item(key = "general") {
            GroupedSection(header = "General") {
                row {
                    ListRow(
                        title = "Search Engine",
                        leading = { IconTile(PaneIcons.Search, TileColors.Blue) },
                        value = engine.name,
                        onClick = { navigator.push(Route.SearchSettings) },
                    )
                }
            }
        }
        item(key = "passwords") {
            GroupedSection(header = "Passwords") {
                row {
                    ListRow(
                        title = "Passwords & Autofill",
                        leading = { IconTile(PaneIcons.Key, TileColors.Gray) },
                        value = autofill.serviceLabel ?: if (autofill.enabled) "On" else "Off",
                        onClick = { navigator.push(Route.PasswordSettings) },
                    )
                }
            }
        }
        item(key = "privacy") {
            GroupedSection(header = "Privacy") {
                row {
                    ListRow(
                        title = "Privacy & Security",
                        leading = { IconTile(PaneIcons.ShieldCheck, TileColors.Green) },
                        value = protection.level.name,
                        onClick = { navigator.push(Route.PrivacySettings) },
                    )
                }
                row {
                    ListRow(
                        title = "Site Settings",
                        leading = { IconTile(PaneIcons.Globe, TileColors.Indigo) },
                        onClick = { navigator.push(Route.SiteSettings) },
                    )
                }
                row {
                    ListRow(
                        title = "Clear Browsing Data",
                        leading = { IconTile(PaneIcons.Trash, TileColors.Red) },
                        onClick = { navigator.push(Route.ClearData) },
                    )
                }
            }
        }
        item(key = "appearance") {
            GroupedSection(header = "Appearance") {
                row {
                    ListRow(
                        title = "Appearance",
                        leading = { IconTile(PaneIcons.Palette, TileColors.Orange) },
                        value = when (settings.theme) {
                            ThemeMode.System -> "Automatic"
                            ThemeMode.Light -> "Light"
                            ThemeMode.Dark -> "Dark"
                        },
                        onClick = { navigator.push(Route.AppearanceSettings) },
                    )
                }
            }
        }
        item(key = "tabs") {
            GroupedSection(header = "Tabs", footer = "Reopen your tabs where you left off. Private tabs are never restored.") {
                row {
                    ToggleRow(
                        title = "Restore Tabs on Launch",
                        checked = settings.restoreTabs,
                        onCheckedChange = { on -> container.settings.update { it.copy(restoreTabs = on) } },
                        leading = { IconTile(PaneIcons.Tabs, TileColors.Teal) },
                    )
                }
            }
        }
        item(key = "more") {
            GroupedSection {
                row {
                    ListRow(
                        title = "Extensions",
                        leading = { IconTile(PaneIcons.Puzzle, TileColors.Purple) },
                        onClick = { navigator.push(Route.Extensions) },
                    )
                }
                row {
                    ListRow(
                        title = "About Pane",
                        leading = { IconTile(PaneIcons.Info, TileColors.Gray) },
                        onClick = { navigator.push(Route.About) },
                    )
                }
            }
        }
    }
}

/**
 * Invites the user to make Pane the default browser, and disappears once it is. Uses the
 * browser role dialog where available, the system default-apps screen otherwise.
 */
@Composable
private fun DefaultBrowserCard() {
    val context = LocalContext.current
    val toasts = LocalToasts.current
    val colors = PaneTheme.colors
    var isDefault by remember { mutableStateOf(DefaultBrowser.isDefault(context)) }
    var launchedAt by remember { mutableLongStateOf(0L) }
    var usedRoleDialog by remember { mutableStateOf(false) }

    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = DefaultBrowser.isDefault(context)
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = DefaultBrowser.isDefault(context)
        if (isDefault) {
            toasts.show("Pane is now your default browser", PaneIcons.Check)
        } else if (usedRoleDialog && SystemClock.elapsedRealtime() - launchedAt < AUTO_DENIED_MS) {
            // The system stops showing the dialog after repeated refusals; fall back to Settings.
            runCatching { settingsLauncher.launch(DefaultBrowser.settingsIntent()) }
        }
    }

    AnimatedVisibility(visible = !isDefault, exit = shrinkVertically(Motion.sizeSpring) + fadeOut()) {
        Column(
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                .fillMaxWidth()
                .clip(PaneShapes.large)
                .background(colors.surface)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AppIcon(size = 52.dp)
                Column(Modifier.weight(1f)) {
                    Text("Make Pane Your Default Browser", style = PaneTheme.type.headline, color = colors.label)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Open links from other apps with trackers blocked and HTTPS-Only on.",
                        style = PaneTheme.type.subheadline,
                        color = colors.secondaryLabel,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            PrimaryButton(
                text = "Set as Default Browser",
                onClick = {
                    val roleIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) DefaultBrowser.roleIntent(context) else null
                    launchedAt = SystemClock.elapsedRealtime()
                    usedRoleDialog = roleIntent != null
                    try {
                        if (roleIntent != null) roleLauncher.launch(roleIntent) else settingsLauncher.launch(DefaultBrowser.settingsIntent())
                    } catch (_: ActivityNotFoundException) {
                        toasts.show("Choose Pane in Settings › Apps › Default apps", PaneIcons.Info)
                    }
                },
            )
        }
    }
}

private const val AUTO_DENIED_MS = 600L

/** Default-browser checks and requests; the role API exists only on Android 10+. */
private object DefaultBrowser {

    fun isDefault(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            roleHeld(context)?.let { return it }
        }
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        @Suppress("DEPRECATION")
        val handler = context.packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
        return handler?.activityInfo?.packageName == context.packageName
    }

    /** The system dialog asking to make Pane the browser, or null when the role can't be requested. */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun roleIntent(context: Context): Intent? {
        val roles = context.getSystemService(RoleManager::class.java) ?: return null
        if (!roles.isRoleAvailable(RoleManager.ROLE_BROWSER) || roles.isRoleHeld(RoleManager.ROLE_BROWSER)) return null
        return roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
    }

    fun settingsIntent(): Intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun roleHeld(context: Context): Boolean? {
        val roles = context.getSystemService(RoleManager::class.java) ?: return null
        return if (roles.isRoleAvailable(RoleManager.ROLE_BROWSER)) roles.isRoleHeld(RoleManager.ROLE_BROWSER) else null
    }
}
