package app.pane.browser.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The visible part of a view at full resolution, as it is when this is called. Where the platform lets an app render
 * off screen (Android 10) the view is drawn by the gpu on the render thread, as it draws on screen, into a buffer the
 * bitmap then wraps, so the main thread only records it; before that it is drawn in software.
 */
object PageCapture {
    suspend fun of(view: View): Bitmap? {
        if (view.width <= 0 || view.height <= 0) return null
        return try {
            if (Build.VERSION.SDK_INT >= 29) gpu(view) else software(view)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // no snapshot: what would show it shows the page's title or nothing instead
            null
        }
    }

    @RequiresApi(29)
    private suspend fun gpu(view: View): Bitmap? {
        val node = RenderNode("capture").apply { setPosition(0, 0, view.width, view.height) }
        draw(view, node.beginRecording(view.width, view.height))
        node.endRecording()
        val usage = HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
        val reader = ImageReader.newInstance(view.width, view.height, PixelFormat.RGBA_8888, 1, usage)
        val renderer = HardwareRenderer()
        return try {
            suspendCancellableCoroutine { done ->
                reader.setOnImageAvailableListener({
                    // the bitmap keeps its own hold on the buffer, so the image and reader can go
                    val bitmap = runCatching {
                        it.acquireNextImage()?.use { image -> image.hardwareBuffer?.use { buffer -> Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB)) } }
                    }.getOrNull()
                    if (done.isActive) done.resume(bitmap)
                }, Handler(Looper.getMainLooper()))
                renderer.setSurface(reader.surface)
                renderer.setContentRoot(node)
                // a frame that won't be drawn brings no image
                val lost = HardwareRenderer.SYNC_LOST_SURFACE_REWARD_IF_FOUND or HardwareRenderer.SYNC_CONTEXT_IS_STOPPED or HardwareRenderer.SYNC_FRAME_DROPPED
                if (renderer.createRenderRequest().syncAndDraw() and lost != 0 && done.isActive) done.resume(null)
            }
        } finally {
            renderer.destroy()
            reader.close()
            node.discardDisplayList()
        }
    }

    private fun software(view: View): Bitmap =
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { draw(view, Canvas(it)) }

    // a view draws in its own scrolled coordinates; its parent is what brings the part on screen back to the origin
    private fun draw(view: View, canvas: Canvas) {
        canvas.translate(-view.scrollX.toFloat(), -view.scrollY.toFloat())
        view.draw(canvas)
    }
}
