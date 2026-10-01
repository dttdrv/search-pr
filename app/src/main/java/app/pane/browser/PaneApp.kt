package app.pane.browser

import android.app.Application

class PaneApp : Application() {
    private var started = false

    val container: AppContainer by lazy {
        started = true
        AppContainer(this)
    }

    /** When the system wants memory back, background pages are the first thing to give. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (started) container.sessions.onTrimMemory(level)
    }
}
