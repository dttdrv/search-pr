package app.pane.browser.ui.browser

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import app.pane.browser.MainActivity
import app.pane.core.url.UrlDisplay
import kotlin.math.abs

/** "Add to Home Screen": a pinned launcher shortcut with a monogram icon. No favicon fetch. */
object HomeShortcuts {
    private val palette = intArrayOf(
        0xFF5E5CE6.toInt(), 0xFF0A84FF.toInt(), 0xFF30B0C7.toInt(), 0xFF34C759.toInt(), 0xFFFF9F0A.toInt(),
        0xFFFF375F.toInt(), 0xFFBF5AF2.toInt(), 0xFF64D2FF.toInt(), 0xFFAC8E68.toInt(), 0xFF8E8E93.toInt(),
    )

    fun pin(context: Context, url: String, title: String): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val host = UrlDisplay.toolbarText(url)
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val info = ShortcutInfoCompat.Builder(context, "site:${url.hashCode()}")
            .setShortLabel(title.ifBlank { host }.take(24))
            .setLongLabel(title.ifBlank { host })
            .setIcon(IconCompat.createWithAdaptiveBitmap(monogram(host)))
            .setIntent(intent)
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, info, null)
    }

    /** 108×108 adaptive-icon bitmap: host colour with the first letter centred in the safe zone. */
    private fun monogram(host: String): Bitmap {
        val size = 216
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette[abs(host.hashCode()) % palette.size])
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = size * 0.34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val letter = host.removePrefix("www.").firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•"
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(letter, size / 2f, y, paint)
        return bitmap
    }
}
