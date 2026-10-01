package app.pane.browser.settings

import android.content.Context
import androidx.core.content.edit
import app.pane.core.settings.BrowserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** Persists [BrowserSettings] as one JSON blob; unknown/missing keys fall back to defaults. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("pane_settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<BrowserSettings> = _state.asStateFlow()
    val current: BrowserSettings get() = _state.value

    private fun load(): BrowserSettings {
        val raw = prefs.getString(KEY, null) ?: return BrowserSettings()
        val stored = runCatching { json.decodeFromString(BrowserSettings.serializer(), raw) }.getOrNull() ?: return BrowserSettings()
        // Installs from before the defaults were loosened carry the old, stricter values as if chosen.
        // Move those three to the new defaults once; everything else the person set stays.
        val hasVersion = runCatching { "defaultsVersion" in json.parseToJsonElement(raw).jsonObject }.getOrDefault(true)
        if (hasVersion) return stored
        val d = BrowserSettings()
        return stored.copy(
            trackingProtection = d.trackingProtection,
            httpsMode = d.httpsMode,
            fingerprintingProtection = d.fingerprintingProtection,
            defaultsVersion = BrowserSettings.DEFAULTS_VERSION,
        )
    }

    @Synchronized
    fun update(transform: (BrowserSettings) -> BrowserSettings) {
        val next = transform(_state.value)
        if (next == _state.value) return
        _state.value = next
        prefs.edit { putString(KEY, json.encodeToString(BrowserSettings.serializer(), next)) }
    }

    fun reset() = update { BrowserSettings(onboardingDone = true) }

    private companion object {
        const val KEY = "settings_v1"
    }
}
