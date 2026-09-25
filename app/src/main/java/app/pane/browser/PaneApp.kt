package app.pane.browser

import android.app.Application

class PaneApp : Application() {
    /** Only ever touched from the main process (see [AppContainer]). */
    val container: AppContainer by lazy { AppContainer(this) }
}
