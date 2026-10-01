package app.pane.browser.settings

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import app.pane.core.settings.ThemeMode

/**
 * The theme choice as the app's own night mode, so the window, the pages and the system splash
 * follow it and not only the system. Automatic hands the decision back to the system. Before
 * Android 12 there is no such call, and only Pane's own screens follow the choice.
 */
object NightMode {
    fun apply(context: Context, theme: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        context.getSystemService(UiModeManager::class.java).setApplicationNightMode(
            when (theme) {
                ThemeMode.System -> UiModeManager.MODE_NIGHT_AUTO
                ThemeMode.Light -> UiModeManager.MODE_NIGHT_NO
                ThemeMode.Dark -> UiModeManager.MODE_NIGHT_YES
            },
        )
    }
}
