package app.pane.browser.ui.browser

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.components.PrimaryButton
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.PaneTheme

/**
 * Device-owner check before showing private tabs: the platform biometric prompt, with the screen
 * lock as its fallback, on Android 11+; the screen-lock confirmation on earlier releases.
 */
object BiometricGate {
    private const val TITLE = "Unlock Private Tabs"
    private const val SUBTITLE = "Confirm it's you to see your private tabs"

    /**
     * Whether private tabs can actually be locked here. The setting, the page cover and the tab
     * overview all go by this, so a device with no way to confirm its owner never locks anyone out.
     */
    fun canLock(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            context.getSystemService(BiometricManager::class.java)?.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            ) == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            // Before 11 the biometric prompt can't fall back to the PIN, so the screen lock is confirmed
            // instead (that screen accepts an enrolled fingerprint too).
            context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
        }

    /**
     * Asks the owner to confirm it's them. On Android 11+ the answer comes through [onResult]; earlier
     * releases start the screen-lock confirmation with [confirmCredential], whose caller handles the
     * result. A device that can't lock is unlocked straight away.
     */
    fun authenticate(activity: Activity, confirmCredential: (Intent) -> Unit, onResult: (Boolean) -> Unit) {
        if (!canLock(activity)) {
            onResult(true)
            return
        }
        if (Build.VERSION.SDK_INT >= 30) {
            BiometricPrompt.Builder(activity)
                .setTitle(TITLE)
                .setSubtitle(SUBTITLE)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
                .authenticate(
                    CancellationSignal(),
                    activity.mainExecutor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
                    },
                )
        } else {
            @Suppress("DEPRECATION")
            val intent = activity.getSystemService(KeyguardManager::class.java)?.createConfirmDeviceCredentialIntent(TITLE, SUBTITLE)
            // No intent means there is no screen lock to confirm after all.
            if (intent != null) confirmCredential(intent) else onResult(true)
        }
    }
}

/**
 * Returns an action that asks the owner to unlock private tabs and calls [onUnlocked] once they
 * have (at once on a device that can't lock).
 */
@Composable
fun rememberPrivateUnlock(onUnlocked: () -> Unit): () -> Unit {
    val activity = LocalActivity.current
    val currentOnUnlocked by rememberUpdatedState(onUnlocked)
    val screenLock = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) currentOnUnlocked()
    }
    return {
        if (activity != null) {
            BiometricGate.authenticate(activity, confirmCredential = { screenLock.launch(it) }) { ok -> if (ok) currentOnUnlocked() }
        }
    }
}

/** Covers the page while private tabs are locked, and keeps every touch from reaching it. */
@Composable
fun PrivateLockCover(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    Box(modifier.fillMaxSize()) {
        // Sits behind the content so the Unlock button still gets its taps; every other touch,
        // scroll or hover stops here instead of falling through to the hidden page.
        Box(
            Modifier
                .matchParentSize()
                .background(colors.groupedBackground)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                },
        )
        Column(
            Modifier.fillMaxSize().padding(32.dp),
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
}
