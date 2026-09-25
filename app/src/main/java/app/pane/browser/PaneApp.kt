package app.pane.browser

import android.app.Application
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

class PaneApp : Application() {
    val runtime: GeckoRuntime by lazy {
        GeckoRuntime.create(this, GeckoRuntimeSettings.Builder().build())
    }
}
