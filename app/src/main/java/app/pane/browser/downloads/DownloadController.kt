package app.pane.browser.downloads

import android.content.Context
import app.pane.browser.data.DownloadsRepository
import kotlinx.coroutines.CoroutineScope
import org.mozilla.geckoview.WebResponse

/**
 * Saves responses Gecko can't render (files) into the public Downloads collection.
 *
 * STUB: owned by the library/settings work stream.
 */
class DownloadController(
    private val context: Context,
    private val repository: DownloadsRepository,
    private val scope: CoroutineScope,
) {
    fun onExternalResponse(tabId: String, response: WebResponse, private: Boolean) {
        runCatching { response.body?.close() }
    }

    /** Downloads [url] directly (context menu "Download Link" / "Save Image"). */
    fun download(url: String, private: Boolean, referrer: String? = null) = Unit
}
