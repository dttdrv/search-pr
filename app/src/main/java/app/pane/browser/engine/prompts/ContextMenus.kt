package app.pane.browser.engine.prompts

import app.pane.browser.engine.PromptQueue
import app.pane.browser.engine.PromptRequest
import org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement

/** A long-press on a link, image or media element. Purely informational: nothing in Gecko waits on it. */
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

/** Turns a long-press in a page into a context menu request (links, images, media). */
object ContextMenus {
    fun request(queue: PromptQueue, tabId: String, private: Boolean, x: Int, y: Int, element: ContextElement) {
        val link = element.linkUri?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("javascript:", ignoreCase = true) }
        val src = element.srcUri?.trim()?.takeIf { it.isNotEmpty() }
        val media = when (element.type) {
            ContextElement.TYPE_IMAGE -> ContextMenuRequest.Media.Image
            ContextElement.TYPE_VIDEO -> ContextMenuRequest.Media.Video
            ContextElement.TYPE_AUDIO -> ContextMenuRequest.Media.Audio
            else -> null
        }?.takeIf { src != null }
        if (link == null && media == null) return

        // A menu that never got shown (its tab was in the background) is stale by now.
        queue.requests.value.filter { it is ContextMenuRequest && it.tabId == tabId }.forEach(queue::remove)
        queue.enqueue(
            ContextMenuRequest(
                tabId = tabId,
                isPrivate = private,
                media = media,
                linkUri = link,
                srcUri = src,
                title = element.title,
                altText = element.altText,
                linkText = element.linkText,
                baseUri = element.baseUri,
            ),
        )
    }
}
