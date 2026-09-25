package app.pane.browser.ui.settings

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.pane.browser.ui.components.ButtonStyle
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.IconTile
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.components.ListRow
import app.pane.browser.ui.components.LocalToasts
import app.pane.browser.ui.components.PrimaryButton
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.library.TileColors
import app.pane.browser.ui.library.rememberBackLabel
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.ContinuousRoundedShape
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme

/**
 * Pane keeps no passwords of its own: login forms are handed to Android's autofill service with
 * the site's address, so a password manager such as Bitwarden matches logins by website, fills
 * them, and offers to save new ones. This screen shows which service is doing that and links to
 * the system pickers for changing it.
 */
@Composable
fun PasswordsScreen() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val toasts = LocalToasts.current
    val backLabel = rememberBackLabel(Route.PasswordSettings)
    var status by remember { mutableStateOf(AutofillStatus.read(context)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { status = AutofillStatus.read(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        status = AutofillStatus.read(context)
    }

    fun launchFirst(vararg intents: Intent) {
        for (intent in intents) {
            try {
                launcher.launch(intent)
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        toasts.show("Choose it in Settings › Passwords & accounts", PaneIcons.Info)
    }

    val chooseService = { launchFirst(*AutofillStatus.pickerIntents(context)) }

    LargeTitleScaffold(title = "Passwords", onBack = navigator::pop, backLabel = backLabel) {
        item(key = "status") { AutofillStatusCard(status, onChoose = chooseService) }
        item(key = "service") {
            GroupedSection(
                header = "Autofill",
                footer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    "Your preferred service fills passwords and passkeys in Pane. Pane sends it the site's " +
                        "address, never your browsing history."
                } else {
                    "Your autofill service fills passwords in Pane. Pane sends it the site's address, never " +
                        "your browsing history."
                },
            ) {
                row {
                    ListRow(
                        title = "Autofill Service",
                        leading = { IconTile(PaneIcons.Key, TileColors.Gray) },
                        value = status.serviceLabel ?: if (status.enabled) "On" else "Off",
                        onClick = chooseService,
                    )
                }
            }
        }
        item(key = "bitwarden") {
            GroupedSection(
                header = "Bitwarden",
                footer = if (status.bitwardenInstalled) {
                    "When Bitwarden asks whether to trust Pane for passkeys, allow it once and passkeys work on every site."
                } else {
                    "Bitwarden is a free, open-source password manager that works with Pane's autofill."
                },
            ) {
                row {
                    if (status.bitwardenInstalled) {
                        ListRow(
                            title = "Open Bitwarden",
                            leading = { IconTile(PaneIcons.Shield, TileColors.Blue) },
                            onClick = {
                                context.packageManager.getLaunchIntentForPackage(AutofillStatus.BITWARDEN)
                                    ?.let { launchFirst(it) }
                            },
                        )
                    } else {
                        ListRow(
                            title = "Get Bitwarden",
                            leading = { IconTile(PaneIcons.Download, TileColors.Blue) },
                            onClick = {
                                launchFirst(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${AutofillStatus.BITWARDEN}")),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AutofillStatusCard(status: AutofillStatus, onChoose: () -> Unit) {
    val colors = PaneTheme.colors
    val (title, body) = when {
        !status.supported -> "Autofill Unavailable" to "This device doesn't offer an autofill service."
        status.serviceLabel != null -> "${status.serviceLabel} Fills Your Logins" to
            "Tap a login field on any site to pick an account. New passwords are offered for saving after you sign in."
        status.enabled -> "Autofill Is On" to
            "Tap a login field on any site to pick an account from your password manager."
        else -> "Autofill Is Off" to
            "Choose Bitwarden or another password manager so it can fill logins in Pane."
    }
    val ready = status.supported && (status.enabled || status.serviceLabel != null)
    Column(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .fillMaxWidth()
            .clip(PaneShapes.large)
            .background(colors.surface)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(ContinuousRoundedShape(12.dp))
                    .background(if (ready) TileColors.Green else TileColors.Orange),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (ready) PaneIcons.Check else PaneIcons.Key, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = PaneTheme.type.headline, color = colors.label)
                Spacer(Modifier.height(2.dp))
                Text(body, style = PaneTheme.type.subheadline, color = colors.secondaryLabel)
            }
        }
        if (status.supported && (!ready || (!status.isBitwarden && status.bitwardenInstalled))) {
            Spacer(Modifier.height(14.dp))
            PrimaryButton(
                text = if (status.bitwardenInstalled) "Use Bitwarden" else "Choose Autofill Service",
                onClick = onChoose,
                style = if (ready) ButtonStyle.Tinted else ButtonStyle.Filled,
            )
        }
    }
}

/** A snapshot of the system autofill setup, as far as an app is allowed to see it. */
internal data class AutofillStatus(
    val supported: Boolean,
    val enabled: Boolean,
    val servicePackage: String?,
    val serviceLabel: String?,
    val bitwardenInstalled: Boolean,
) {
    val isBitwarden: Boolean get() = servicePackage == BITWARDEN

    companion object {
        const val BITWARDEN = "com.x8bit.bitwarden"

        fun read(context: Context): AutofillStatus {
            val pm = context.packageManager
            val bitwardenInstalled = pm.getLaunchIntentForPackage(BITWARDEN) != null
            val afm = context.getSystemService(AutofillManager::class.java)
            if (afm == null || !afm.hasAutofillFeature() || !afm.isAutofillSupported) {
                return AutofillStatus(false, false, null, null, bitwardenInstalled)
            }
            // Android 9+ names the service; 8.x only says whether one is active for this app.
            val component: ComponentName? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) afm.autofillServiceComponentName else null
            val servicePackage = component?.packageName
            val label = servicePackage?.let { pkg ->
                runCatching {
                    @Suppress("DEPRECATION")
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg)
            }
            return AutofillStatus(
                supported = true,
                enabled = component != null || afm.isEnabled,
                servicePackage = servicePackage,
                serviceLabel = label,
                bitwardenInstalled = bitwardenInstalled,
            )
        }

        /**
         * The screens where the user picks a password manager, best first. Android 14+ has one
         * page for passwords, passkeys and autofill; earlier versions have the autofill picker,
         * which lists every service even though the request names Pane.
         */
        fun pickerIntents(context: Context): Array<Intent> {
            val autofillPicker = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, Uri.parse("package:${context.packageName}"))
            val fallback = Intent(Settings.ACTION_SETTINGS)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                arrayOf(Intent(Settings.ACTION_CREDENTIAL_PROVIDER), autofillPicker, fallback)
            } else {
                arrayOf(autofillPicker, fallback)
            }
        }
    }
}
