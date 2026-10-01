package app.pane.browser.engine.prompts

import android.net.Uri
import android.webkit.WebView.HitTestResult
import app.pane.browser.engine.PromptQueue
import app.pane.browser.engine.PromptRequest

/** A long-press on a link, image or media element. Purely informational: nothing in the page waits on it. */
class ContextMenuRequest internal constructor(
    override val tabId: String,
    val isPrivate: Boolean,
    val media: Media?,
    val linkUri: String?,
    val srcUri: String?,
    val title: String?,
    val altText: String?,
    val linkText: String?,
    /** The page the element is on; sent as the referrer when downloading. */
    val baseUri: String?,
) : PromptRequest {
    enum class Media { Image, Video, Audio }

    override val id: Long = PromptRequest.nextId()

    /** Best human label: the link's text, else the element's title or alt text. */
    val label: String?
        get() = listOf(linkText, title, altText).firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }

    val linkIsWeb: Boolean get() = linkUri?.let { isWeb(it) } == true
    val srcIsWeb: Boolean get() = srcUri?.let { isWeb(it) } == true

    override fun dismiss() = Unit

    private fun isWeb(url: String) = url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)
}

/**
 * What a long-press landed on, as the web view reports it.
 *
 * @property type a `WebView.HitTestResult` type (or [VIDEO] / [AUDIO], which the web view itself
 * can't detect, for callers that find media another way).
 * @property extra `HitTestResult.getExtra()`: the link, the image source, or the phone number,
 * email address or street address, depending on [type].
 * @property linkUrl the link around the element, when known (`requestFocusNodeHref`).
 * @property imageUrl the image (or media) source, when known (`requestFocusNodeHref`).
 * @property title the link text or the element's title, when known.
 * @property pageUrl the page the element is on; sent as the referrer when downloading.
 */
data class HitInfo(
    val type: Int,
    val extra: String?,
    val linkUrl: String?,
    val imageUrl: String?,
    val title: String?,
    val pageUrl: String? = null,
) {
    companion object {
        /** Not a `HitTestResult` type: [imageUrl] / [extra] is a video source. */
        const val VIDEO = 100

        /** Not a `HitTestResult` type: [imageUrl] / [extra] is an audio source. */
        const val AUDIO = 101
    }
}

/** Turns a long-press in a page into a context menu request (links, images, media, phone numbers…). */
object ContextMenus {
    // HitTestResult.ANCHOR_TYPE and IMAGE_ANCHOR_TYPE are deprecated but still reported on old WebViews.
    private const val ANCHOR_TYPE = 1
    private const val IMAGE_ANCHOR_TYPE = 6

    @Suppress("UNUSED_PARAMETER")
    fun request(queue: PromptQueue, tabId: String, private: Boolean, x: Int, y: Int, hit: HitInfo) {
        val extra = hit.extra?.trim()?.takeIf { it.isNotEmpty() }
        val explicitLink = hit.linkUrl.clean()
        val explicitSrc = hit.imageUrl.clean()

        var link: String? = explicitLink
        var src: String? = explicitSrc
        var media: ContextMenuRequest.Media? = null
        when (hit.type) {
            HitTestResult.SRC_ANCHOR_TYPE, ANCHOR_TYPE -> link = link ?: extra.clean()
            HitTestResult.IMAGE_TYPE -> {
                media = ContextMenuRequest.Media.Image
                src = src ?: extra.clean()
            }
            HitTestResult.SRC_IMAGE_ANCHOR_TYPE, IMAGE_ANCHOR_TYPE -> {
                media = ContextMenuRequest.Media.Image
                // The extra is the image; the link has to come from the focus-node lookup.
                src = src ?: extra.clean()
            }
            HitInfo.VIDEO -> {
                media = ContextMenuRequest.Media.Video
                src = src ?: extra.clean()
            }
            HitInfo.AUDIO -> {
                media = ContextMenuRequest.Media.Audio
                src = src ?: extra.clean()
            }
            HitTestResult.PHONE_TYPE -> if (extra != null) link = "tel:$extra"
            HitTestResult.EMAIL_TYPE -> if (extra != null) link = "mailto:$extra"
            HitTestResult.GEO_TYPE -> if (extra != null) link = "geo:0,0?q=${Uri.encode(extra)}"
            else -> Unit
        }
        // A link or source that came along with some other hit type still makes a useful menu.
        if (media == null && src != null && hit.type != HitTestResult.EDIT_TEXT_TYPE) media = ContextMenuRequest.Media.Image
        if (media != null && src == null) media = null
        if (link == null && media == null) return

        // A menu that never got shown (its tab was in the background) is stale by now.
        queue.requests.value.filter { it is ContextMenuRequest && it.tabId == tabId }.forEach(queue::remove)
        queue.enqueue(
            ContextMenuRequest(
                tabId = tabId,
                isPrivate = private,
                media = media,
                linkUri = link,
                srcUri = if (media != null) src else null,
                title = hit.title,
                altText = hit.title.takeIf { media == ContextMenuRequest.Media.Image },
                linkText = hit.title.takeIf { media == null },
                baseUri = hit.pageUrl,
            ),
        )
    }

    /** Trimmed, or null when empty or a `javascript:` URL (which must never be offered as a link). */
    private fun String?.clean(): String? =
        this?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("javascript:", ignoreCase = true) }
}
