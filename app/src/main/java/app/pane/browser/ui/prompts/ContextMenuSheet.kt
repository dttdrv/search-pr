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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import app.pane.browser.AppContainer
import app.pane.browser.LocalAppContainer
import app.pane.browser.engine.prompts.ContextMenuRequest
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.ListRow
import app.pane.browser.ui.components.LocalToasts
import app.pane.browser.ui.components.PaneSheet
import app.pane.browser.ui.components.ToastState
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.rememberHaptics
import app.pane.core.privacy.TrackingParams
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

private class MenuItem(val label: String, val icon: ImageVector, val action: () -> Unit)

/**
 * The long-press menu for links and media: a header naming what was pressed (with a preview for
 * images), then the actions, each with its glyph on the trailing edge as in iOS menus.
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
            add(MenuItem("Open in New Tab", PaneIcons.Plus) { actions.openInBackground(link, request.isPrivate) })
            if (!request.isPrivate) add(MenuItem("Open in Private Tab", PaneIcons.Private) { actions.openInBackground(link, private = true) })
        }
        add(MenuItem("Copy Link", PaneIcons.Copy) { actions.copy(link) })
        val clean = TrackingParams.strip(link)
        if (clean != link) add(MenuItem("Copy Clean Link", PaneIcons.Link) { actions.copy(clean) })
        add(MenuItem("Share Link", PaneIcons.Share) { actions.share(link) })
        if (request.linkIsWeb) add(MenuItem("Download Link", PaneIcons.Download) { actions.download(link) })
    }
    val mediaItems = buildList<MenuItem> {
        if (src == null || noun == null) return@buildList
        if (request.srcIsWeb) {
            add(MenuItem("Open $noun in New Tab", PaneIcons.Tabs) { actions.openInBackground(src, request.isPrivate) })
            add(MenuItem("Save $noun", PaneIcons.Download) { actions.download(src) })
        }
        // Inline data: URIs can be megabytes; too big for the clipboard or a share intent.
        if (request.srcIsWeb || src.length <= MAX_INLINE_URI) {
            add(MenuItem("Copy $noun Address", PaneIcons.Copy) { actions.copy(src) })
            add(MenuItem("Share $noun", PaneIcons.Share) { actions.share(src) })
        }
    }

    PaneSheet(visible = visible, onDismiss = onDone) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            AnimatedVisibility(
                visible = preview != null,
                enter = expandVertically(Motion.spring(0.4f, 0.9f)) + fadeIn(Motion.fade(220)),
            ) {
                val image = preview
                if (image != null) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Image(
                            bitmap = image,
                            contentDescription = request.altText,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.heightIn(max = 220.dp).clip(PaneShapes.large),
                        )
                    }
                }
            }
            MenuHeader(request)
            listOf(linkItems, mediaItems).filter { it.isNotEmpty() }.forEach { items ->
                GroupedSection {
                    items.forEach { item ->
                        row {
                            ListRow(
                                title = item.label,
                                showChevron = false,
                                onClick = {
                                    item.action()
                                    onDone()
                                },
                            ) {
                                Icon(item.icon, null, tint = colors.label, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuHeader(request: ContextMenuRequest) {
    val colors = PaneTheme.colors
    val target = request.linkUri ?: request.srcUri.orEmpty()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(PaneShapes.small).background(colors.fill),
            contentAlignment = Alignment.Center,
        ) {
            val icon = when {
                request.linkUri != null -> PaneIcons.Link
                request.media == ContextMenuRequest.Media.Audio -> PaneIcons.Mic
                request.media == ContextMenuRequest.Media.Video -> PaneIcons.Camera
                else -> PaneIcons.Globe
            }
            Icon(icon, null, tint = colors.secondaryLabel, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            request.label?.let {
                Text(it, style = PaneTheme.type.headline, color = colors.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                displayTarget(target),
                style = PaneTheme.type.footnote,
                color = colors.secondaryLabel,
                maxLines = if (request.label == null) 3 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun displayTarget(url: String): String =
    if (url.startsWith("data:", ignoreCase = true)) url.substringBefore(',').take(60) else PromptText.shorten(url, max = 200)

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
            if (private && !request.isPrivate) "Opened in private tab" else "Opened in new tab",
            PaneIcons.Tabs,
            if (show != null) "Show" else null,
            show,
        )
    }

    fun copy(text: String) {
        val clipboard = context.getSystemService<ClipboardManager>() ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("URL", text))
        // Android 13 and later confirm copies themselves.
        if (Build.VERSION.SDK_INT < 33) toasts.show("Copied", PaneIcons.Copy)
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
