package app.pane.browser.ui.prompts

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import app.pane.browser.AppContainer
import app.pane.browser.LocalAppContainer
import app.pane.browser.engine.prompts.ContextMenuRequest
import app.pane.browser.ui.components.ListRow
import app.pane.browser.ui.components.LocalToasts
import app.pane.browser.ui.components.PaneSheet
import app.pane.browser.ui.components.ToastState
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.entrance
import app.pane.browser.ui.theme.rememberHaptics
import app.pane.core.privacy.TrackingParams
import app.pane.core.prompts.PermissionText
import app.pane.core.prompts.PromptText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest
import org.mozilla.geckoview.WebResponse
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

private class MenuItem(val label: String, val destructive: Boolean = false, val action: () -> Unit)

/**
 * The long-press menu for links and media, a floating glass list: the host of what was pressed
 * (with a preview for images) and then plain text rows that arrive one after another.
 */
@Composable
internal fun ContextMenuSheet(request: ContextMenuRequest, visible: Boolean, onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val toasts = LocalToasts.current
    val haptics = rememberHaptics()
    val colors = PaneTheme.colors
    val previewPx = with(LocalDensity.current) { 360.dp.roundToPx() }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(request.id) { haptics.longPress() }
    val src = request.srcUri
    if (request.media == ContextMenuRequest.Media.Image && src != null) {
        LaunchedEffect(src) { preview = loadPreview(container.runtime, src, request.isPrivate, previewPx) }
    }

    val actions = remember(request) { MenuActions(container, context, toasts, request) }
    val link = request.linkUri
    val noun = when (request.media) {
        ContextMenuRequest.Media.Image -> "Image"
        ContextMenuRequest.Media.Video -> "Video"
        ContextMenuRequest.Media.Audio -> "Audio"
        null -> null
    }
    val linkItems = buildList<MenuItem> {
        if (link == null) return@buildList
        if (request.linkIsWeb) {
            add(MenuItem("New Tab") { actions.openInBackground(link, request.isPrivate) })
            if (!request.isPrivate) add(MenuItem("New Private Tab") { actions.openInBackground(link, private = true) })
        }
        add(MenuItem("Copy Link") { actions.copy(link) })
        val clean = TrackingParams.strip(link)
        if (clean != link) add(MenuItem("Copy Clean Link") { actions.copy(clean) })
        add(MenuItem("Share Link") { actions.share(link) })
        if (request.linkIsWeb) add(MenuItem("Download Link") { actions.download(link) })
    }
    val mediaItems = buildList<MenuItem> {
        if (src == null || noun == null) return@buildList
        if (request.srcIsWeb) {
            add(MenuItem("Open $noun") { actions.openInBackground(src, request.isPrivate) })
            add(MenuItem("Save $noun") { actions.download(src) })
        }
        // Inline data: URIs can be megabytes; too big for the clipboard or a share intent.
        if (request.srcIsWeb || src.length <= MAX_INLINE_URI) {
            add(MenuItem("Copy $noun Address") { actions.copy(src) })
            add(MenuItem("Share $noun") { actions.share(src) })
        }
    }
    val groups = listOf(linkItems, mediaItems).filter { it.isNotEmpty() }

    PaneSheet(visible = visible, onDismiss = onDone) {
        FadingColumn(Modifier.weight(1f, fill = false)) {
            AnimatedVisibility(
                visible = preview != null,
                enter = expandVertically(Motion.smooth()) + fadeIn(Motion.fade(220)),
            ) {
                val image = preview
                if (image != null) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                        Image(
                            bitmap = image,
                            contentDescription = request.altText,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.heightIn(max = 220.dp).clip(PaneShapes.large),
                        )
                    }
                }
            }
            MenuHeader(request, Modifier.entrance(1))
            groups.forEachIndexed { g, items ->
                GlassSection(entranceIndex = 2 + groups.take(g).sumOf { it.size }) {
                    items.forEach { item ->
                        row {
                            ListRow(
                                title = item.label,
                                titleColor = if (item.destructive) colors.destructive else colors.label,
                                showChevron = false,
                                onClick = {
                                    item.action()
                                    onDone()
                                },
                            )
                        }
                    }
                }
            }
            Box(Modifier.height(10.dp))
        }
    }
}

/** What was pressed: its link text or alt text (if any) and, in secondary type, the host it points at. */
@Composable
private fun MenuHeader(request: ContextMenuRequest, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    val target = request.linkUri ?: request.srcUri.orEmpty()
    Column(modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 8.dp)) {
        request.label?.let {
            Text(it, style = PaneTheme.type.headline, color = colors.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(
            displayTarget(target),
            style = PaneTheme.type.subheadline,
            color = colors.secondaryLabel,
            maxLines = if (request.label == null) 3 else 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The host for web addresses (that is what tells a link's destination), the scheme for inline data, else the address itself. */
private fun displayTarget(url: String): String = when {
    url.startsWith("data:", ignoreCase = true) -> url.substringBefore(',').take(60)
    url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true) -> PermissionText.displayHost(url)
    else -> PromptText.shorten(url, max = 200)
}

/** What the menu's rows do. Opening in the background confirms with a toast that can switch to the tab. */
private class MenuActions(
    private val container: AppContainer,
    private val context: Context,
    private val toasts: ToastState,
    private val request: ContextMenuRequest,
) {
    fun openInBackground(url: String, private: Boolean) {
        val before = container.store.state.value.tabs.mapTo(HashSet()) { it.id }
        container.browser.open(url, newTab = true, private = private, background = true, fromTabId = request.tabId)
        val opened = container.store.state.value.tabs.firstOrNull { it.id !in before }?.id
        val show: (() -> Unit)? = if (opened != null) ({ container.browser.select(opened) }) else null
        toasts.show(
            if (private && !request.isPrivate) "Opened privately" else "Tab opened",
            null,
            if (show != null) "Show" else null,
            show,
        )
    }

    fun copy(text: String) {
        val clipboard = context.getSystemService<ClipboardManager>() ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("URL", text))
        // Android 13 and later confirm copies themselves.
        if (Build.VERSION.SDK_INT < 33) toasts.show("Copied")
    }

    fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        runCatching { context.startActivity(Intent.createChooser(send, null)) }
    }

    fun download(url: String) {
        container.downloads.download(url, request.isPrivate, referrer = request.baseUri)
    }
}

private const val MAX_INLINE_URI = 4096
private const val MAX_PREVIEW_BYTES = 8 * 1024 * 1024

/**
 * Loads the long-pressed image for the menu header. Fetched anonymously through Gecko (same
 * network settings as browsing, usually straight from its cache); inline `data:` images are decoded
 * locally. Anything that fails to decode just means no preview.
 */
private suspend fun loadPreview(runtime: GeckoRuntime, url: String, private: Boolean, maxPx: Int): ImageBitmap? {
    val bytes = when {
        url.startsWith("data:", ignoreCase = true) -> withContext(Dispatchers.Default) { decodeDataUri(url) }
        url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true) -> fetchBytes(runtime, url, private)
        else -> null
    } ?: return null
    return withContext(Dispatchers.Default) { decodeSampled(bytes, maxPx)?.asImageBitmap() }
}

private fun decodeDataUri(url: String): ByteArray? {
    val comma = url.indexOf(',')
    if (comma < 0 || !url.substring(0, comma).endsWith(";base64", ignoreCase = true)) return null
    return try {
        Base64.decode(url.substring(comma + 1), Base64.DEFAULT)
    } catch (_: IllegalArgumentException) {
        null
    }
}

// The flags parameter is a bit field; GeckoView's annotation just doesn't say so.
@SuppressLint("WrongConstant")
private suspend fun fetchBytes(runtime: GeckoRuntime, url: String, private: Boolean): ByteArray? {
    val flags = GeckoWebExecutor.FETCH_FLAGS_ANONYMOUS or (if (private) GeckoWebExecutor.FETCH_FLAGS_PRIVATE else 0)
    val request = WebRequest.Builder(url)
        .header("Accept", "image/avif,image/webp,image/png,image/*;q=0.8")
        .cacheMode(WebRequest.CACHE_MODE_DEFAULT)
        .build()
    val response = suspendCancellableCoroutine<WebResponse?> { cont ->
        GeckoWebExecutor(runtime).fetch(request, flags).accept({ result ->
            if (cont.isActive) cont.resume(result) else runCatching { result?.body?.close() }
        }, { _ ->
            if (cont.isActive) cont.resume(null)
        })
    } ?: return null
    return withContext(Dispatchers.IO) {
        val body = response.body ?: return@withContext null
        try {
            if (response.statusCode !in 200..299) return@withContext null
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val n = body.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > MAX_PREVIEW_BYTES) return@withContext null
            }
            out.toByteArray()
        } catch (_: java.io.IOException) {
            null
        } finally {
            runCatching { body.close() }
        }
    }
}

private fun decodeSampled(bytes: ByteArray, maxPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxPx || bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}
