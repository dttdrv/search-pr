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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.components.LocalToasts
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.library.arrive
import app.pane.browser.ui.library.rememberBackLabel
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route

/**
 * Pane keeps no passwords of its own: sign-in forms and passkey requests go to whatever password
 * manager the phone has set up, with the site's address. This screen names that service and links
 * to the system screens for changing it. Nothing here is specific to any one provider.
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
        toasts.show("Open your phone's Settings and search for passwords", PaneIcons.Info)
    }

    LargeTitleScaffold(title = "Passwords", onBack = navigator::pop, backLabel = backLabel) {
        item(key = "system") {
            GroupedSection(
                modifier = Modifier.arrive(0),
                footer = "Pane never stores your passwords.",
            ) {
                row {
                    NavRow(
                        title = "Autofill service",
                        // What the phone reports as active; nothing is assumed about which app it is.
                        value = status.serviceLabel ?: if (!status.supported) "Unavailable" else if (status.enabled) "On" else "Not set",
                        onClick = { launchFirst(*AutofillStatus.pickerIntents(context)) },
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    row {
                        NavRow(
                            title = "Passkeys",
                            onClick = { launchFirst(*AutofillStatus.passkeyIntents()) },
                        )
                    }
                }
            }
        }
    }
}

/** A snapshot of the system autofill setup, as far as an app is allowed to see it. */
internal data class AutofillStatus(
    val supported: Boolean,
    val enabled: Boolean,
    val servicePackage: String?,
    val serviceLabel: String?,
) {
    companion object {
        fun read(context: Context): AutofillStatus {
            val pm = context.packageManager
            val afm = context.getSystemService(AutofillManager::class.java)
            if (afm == null || !afm.isAutofillSupported) {
                return AutofillStatus(false, false, null, null)
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
            )
        }

        /**
         * The screens where the user picks a password manager, best first: the system's autofill
         * picker (which lists every service even though the request names Pane), then, on Android
         * 15+, the page for passwords, passkeys and autofill, then the top of Settings.
         */
        fun pickerIntents(context: Context): Array<Intent> {
            val autofillPicker = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, Uri.parse("package:${context.packageName}"))
            val fallback = Intent(Settings.ACTION_SETTINGS)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                arrayOf(autofillPicker, Intent(Settings.ACTION_CREDENTIAL_PROVIDER), fallback)
            } else {
                arrayOf(autofillPicker, fallback)
            }
        }

        /** Android 15+'s page for choosing where passkeys live, or the top of Settings. */
        fun passkeyIntents(): Array<Intent> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                arrayOf(Intent(Settings.ACTION_CREDENTIAL_PROVIDER), Intent(Settings.ACTION_SETTINGS))
            } else {
                arrayOf(Intent(Settings.ACTION_SETTINGS))
            }
    }
}
