package app.pane.browser.engine

import androidx.core.content.edit
import app.pane.browser.AppContainer
import kotlinx.coroutines.suspendCancellableCoroutine
import org.mozilla.geckoview.StorageController
import kotlin.coroutines.resume

/**
 * "Clear browsing data on exit": wipes tabs, history, cookies, site data and caches. Runs when
 * the user leaves Pane, and again at the next launch in case the process died before finishing.
 */
object ClearOnExit {
    private const val PREFS = "pane_exit"
    private const val PENDING = "pending"

    fun markPending(container: AppContainer) {
        // Written synchronously: the process may be about to die.
        container.app.getSharedPreferences(PREFS, 0).edit(commit = true) { putBoolean(PENDING, true) }
    }

    fun isPending(container: AppContainer): Boolean = container.app.getSharedPreferences(PREFS, 0).getBoolean(PENDING, false)

    suspend fun run(container: AppContainer) {
        container.browser.closeAll(private = true)
        container.browser.closeAll(private = false)
        container.browser.clearRecentlyClosed()
        container.sessions.deletePersistedSession()
        container.history.clear()
        container.thumbnails.clearAll()
        container.snapshots.clear()
        suspendCancellableCoroutine { cont ->
            container.runtime.storageController.clearData(StorageController.ClearFlags.ALL)
                .accept({ if (cont.isActive) cont.resume(Unit) }, { if (cont.isActive) cont.resume(Unit) })
        }
        container.app.getSharedPreferences(PREFS, 0).edit { putBoolean(PENDING, false) }
    }
}
