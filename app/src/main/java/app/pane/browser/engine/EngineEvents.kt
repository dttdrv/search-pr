package app.pane.browser.engine

/** One-shot things the engine tells the UI about. State lives in the store; these are moments. */
sealed interface EngineEvent {
    val tabId: String?

    /** The page tried to open [uri] in another app; the UI asks first. */
    data class ExternalLink(override val tabId: String?, val uri: String, val fallbackUrl: String?, val userGesture: Boolean) : EngineEvent

    /** A link or script opened a new tab in the background. */
    data class OpenedInBackground(override val tabId: String) : EngineEvent

    /** The page wants the toolbars back, e.g. an input was focused. */
    data class ShowToolbar(override val tabId: String) : EngineEvent

    data class FirstPaint(override val tabId: String) : EngineEvent

    /** Good moment to refresh the tab's thumbnail. */
    data class PageSettled(override val tabId: String) : EngineEvent

    data class Crashed(override val tabId: String) : EngineEvent

    data class Message(override val tabId: String?, val text: String) : EngineEvent
}

/** Vertical scroll position of a tab's page, in CSS-ish device pixels. */
data class ScrollUpdate(val tabId: String, val scrollY: Int)
