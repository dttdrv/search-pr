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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.BuildConfig
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.components.LocalToasts
import app.pane.browser.ui.components.PrimaryButton
import app.pane.browser.ui.components.DotText
import app.pane.browser.ui.components.SearchField
import app.pane.browser.ui.components.rowPress
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.library.EmptyState
import app.pane.browser.ui.library.arrive
import app.pane.browser.ui.library.rememberBackLabel
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneTheme
import app.pane.core.search.SearchEngines
import app.pane.core.settings.ThemeMode
import app.pane.core.settings.TrackingProtection

/**
 * The settings root: a search over every
 * setting, then flat lists under small-caps headings. Nothing here is a control; each row opens the
 * screen that holds its settings, led by a bare line glyph.
 */
@Composable
fun SettingsScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val settings by rememberSettingsState()
    val backLabel = rememberBackLabel(Route.Settings)
    val engine = SearchEngines.byId(settings.searchEngineId)
    val installed by container.extensions.installed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var autofill by remember { mutableStateOf(AutofillStatus.read(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { autofill = AutofillStatus.read(context) }
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { searchSettings(query) }
    val protection = if (settings.trackingProtection == TrackingProtection.Strict) "Strict" else "Standard"

    LargeTitleScaffold(
        title = "Settings",
        onBack = navigator::pop,
        backLabel = backLabel,
        header = {
            Column(Modifier.arrive(0)) {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search settings",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        },
    ) {
        if (query.isNotBlank()) {
            if (results.isEmpty()) {
                item(key = "none") { EmptyState(title = "No matches", message = "Try another word.") }
            } else {
                item(key = "results") {
                    GroupedSection(modifier = Modifier.arrive(0), separatorInset = IconSeparatorInset) {
                        results.forEach { entry ->
                            row {
                                NavRow(
                                    entry.title,
                                    icon = settingsIconFor(entry.route),
                                    value = entry.screen,
                                    onClick = { navigator.push(entry.route) },
                                )
                            }
                        }
                    }
                }
            }
        } else {
            item(key = "default") { DefaultBrowserButton(Modifier.arrive(1)) }
            item(key = "browsing") {
                GroupedSection(modifier = Modifier.arrive(2), header = "Browsing", separatorInset = IconSeparatorInset) {
                    row {
                        NavRow(
                            "Search",
                            large = true,
                            icon = PaneIcons.Magnifier,
                            value = engine.name,
                            onClick = { navigator.push(Route.SearchSettings) },
                        )
                    }
                    row {
                        NavRow(
                            "Passwords",
                            large = true,
                            icon = PaneIcons.KeyRound,
                            // Whatever the phone has set up. Short names only: a long label would crowd the title.
                            value = autofill.serviceLabel?.takeIf { it.length <= 12 } ?: if (autofill.enabled) "On" else "Not set",
                            onClick = { navigator.push(Route.PasswordSettings) },
                        )
                    }
                    row {
                        NavRow(
                            "Site permissions",
                            large = true,
                            icon = PaneIcons.Globe,
                            onClick = { navigator.push(Route.SiteSettings) },
                        )
                    }
                    row {
                        NavRow(
                            "Extensions",
                            large = true,
                            icon = PaneIcons.Puzzle,
                            value = installed.size.takeIf { it > 0 }?.toString(),
                            onClick = { navigator.push(Route.Extensions) },
                        )
                    }
                }
            }
            item(key = "look") {
                GroupedSection(modifier = Modifier.arrive(3), header = "Look & feel", separatorInset = IconSeparatorInset) {
                    row {
                        NavRow(
                            "Appearance",
                            large = true,
                            icon = PaneIcons.Palette,
                            value = when (settings.theme) {
                                ThemeMode.System -> "Automatic"
                                ThemeMode.Light -> "Light"
                                ThemeMode.Dark -> "Dark"
                            },
                            onClick = { navigator.push(Route.AppearanceSettings) },
                        )
                    }
                    row {
                        NavRow(
                            "Tabs & toolbar",
                            large = true,
                            icon = PaneIcons.Tabs,
                            onClick = { navigator.push(Route.TabsSettings) },
                        )
                    }
                }
            }
            item(key = "privacy") {
                GroupedSection(modifier = Modifier.arrive(4), header = "Privacy & data", separatorInset = IconSeparatorInset) {
                    row {
                        NavRow(
                            "Privacy",
                            large = true,
                            icon = PaneIcons.Shield,
                            value = protection,
                            onClick = { navigator.push(Route.PrivacySettings) },
                        )
                    }
                    row {
                        NavRow(
                            "Clear data",
                            large = true,
                            icon = PaneIcons.Trash,
                            onClick = { navigator.push(Route.ClearData) },
                        )
                    }
                }
            }
            item(key = "about") {
                GroupedSection(modifier = Modifier.arrive(5), header = "About", separatorInset = IconSeparatorInset) {
                    row {
                        NavRow(
                            "Pane",
                            large = true,
                            icon = PaneIcons.Info,
                            value = BuildConfig.VERSION_NAME,
                            onClick = { navigator.push(Route.About) },
                        )
                    }
                }
            }
        }
    }
}


/**
 * Offers to make Pane the default browser, and disappears once it is. Uses the browser role
 * dialog where available, the system default-apps screen otherwise.
 */
@Composable
private fun DefaultBrowserButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val toasts = LocalToasts.current
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

    AnimatedVisibility(visible = !isDefault, modifier = modifier, exit = shrinkVertically(Motion.sizeSpring) + fadeOut(Motion.fade())) {
        PrimaryButton(
            text = "Make Pane your default browser",
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
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
