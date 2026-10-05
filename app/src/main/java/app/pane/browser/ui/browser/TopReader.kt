package app.pane.browser.ui.browser

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import androidx.compose.ui.graphics.Color

/**
 * Reads the colour of a strip of the window as it is on screen. The copy and the reading both happen off
 * the main thread, from the pixels the window already drew, so it costs the page nothing and can follow a
 * scroll; one read is in flight at a time and a request made meanwhile is dropped.
 */
internal class TopReader(private val window: Window) {
    private val thread = HandlerThread("pane-top").apply { start() }
    private val worker = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private val strip = Bitmap.createBitmap(PageColors.StripWidth, StripRows, Bitmap.Config.ARGB_8888)
    private var busy = false

    /** Hands [onColor] the colour along [rows] of the window, on the main thread; [ground] counts where nothing is drawn. */
    fun read(rows: Rect, ground: Color, onColor: (Color) -> Unit) {
        if (busy || rows.isEmpty) return
        busy = true
        val request = runCatching {
            PixelCopy.request(window, rows, strip, { result ->
                val color = if (result == PixelCopy.SUCCESS) PageColors.top(strip, ground) else null
                main.post {
                    busy = false
                    if (color != null) onColor(color)
                }
            }, worker)
        }
        // a window with no surface yet refuses the copy
        if (request.isFailure) busy = false
    }

    fun close() {
        thread.quitSafely()
    }

    private companion object {
        /** Rows in the copied strip: enough for the rows [PageColors.top] reads. */
        const val StripRows = 8
    }
}
