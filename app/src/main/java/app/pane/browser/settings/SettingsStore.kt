package app.pane.browser.settings

import android.content.Context
import androidx.core.content.edit
import app.pane.core.settings.BrowserSettings
import app.pane.core.settings.SettingsJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persists [BrowserSettings] as one JSON blob; unknown/missing keys fall back to defaults. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("pane_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<BrowserSettings> = _state.asStateFlow()
    val current: BrowserSettings get() = _state.value

    private fun load(): BrowserSettings {
        val raw = prefs.getString(KEY, null) ?: return BrowserSettings()
        // Keys from older versions are ignored (the HTTPS choice is translated); new ones take their defaults.
        val stored = SettingsJson.decode(raw) ?: return BrowserSettings()
        return if (stored.defaultsVersion < BrowserSettings.DEFAULTS_VERSION) {
            stored.copy(defaultsVersion = BrowserSettings.DEFAULTS_VERSION)
        } else {
            stored
        }
    }

    @Synchronized
    fun update(transform: (BrowserSettings) -> BrowserSettings) {
        val next = transform(_state.value)
        if (next == _state.value) return
        _state.value = next
        prefs.edit { putString(KEY, SettingsJson.encode(next)) }
    }

    fun reset() = update { BrowserSettings(onboardingDone = true) }

    private companion object {
        const val KEY = "settings_v1"
    }
}
