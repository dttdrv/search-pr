package app.pane.browser.ui.browser

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.components.PrimaryButton
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.PaneTheme

/** Device-owner check before showing private tabs, using the platform biometric prompt. */
object BiometricGate {
    /** Locking only makes sense when the device itself has a lock; API 28 is the platform minimum. */
    fun isAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 29) return Build.VERSION.SDK_INT >= 28
        val manager = context.getSystemService(BiometricManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= 30) {
            manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
                BiometricManager.BIOMETRIC_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            manager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
        }
    }

    fun authenticate(activity: Activity, onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < 28 || !isAvailable(activity)) {
            onResult(true)
            return
        }
        val executor = activity.mainExecutor
        val builder = BiometricPrompt.Builder(activity)
            .setTitle("Unlock Private Tabs")
            .setSubtitle("Confirm it's you to see your private tabs")
        when {
            Build.VERSION.SDK_INT >= 30 -> builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            Build.VERSION.SDK_INT == 29 -> {
                @Suppress("DEPRECATION")
                builder.setDeviceCredentialAllowed(true)
            }
            else -> builder.setNegativeButton("Cancel", executor) { _, _ -> onResult(false) }
        }
        builder.build().authenticate(
            CancellationSignal(),
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
            },
        )
    }
}

/** Covers the page while private tabs are locked. */
@Composable
fun PrivateLockCover(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    Column(
        modifier.fillMaxSize().background(colors.groupedBackground).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(PaneIcons.Lock, null, tint = colors.accent, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("Private Tabs Locked", style = PaneTheme.type.title2, color = colors.label)
        Spacer(Modifier.height(8.dp))
        Text(
            "Unlock with your fingerprint, face or screen lock.",
            style = PaneTheme.type.subheadline,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Unlock", onClick = onUnlock, icon = PaneIcons.Fingerprint, modifier = Modifier.width(220.dp))
    }
}
