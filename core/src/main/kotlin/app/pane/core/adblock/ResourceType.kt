package app.pane.core.adblock

/**
 * What a request is for, as bits so a rule can name several and a caller that can't tell (WebView
 * doesn't say) can offer a few at once: a request matches a rule when the two share a bit.
 */
object ResourceType {
    const val SCRIPT = 1
    const val IMAGE = 2
    const val STYLESHEET = 4
    const val FONT = 8
    const val MEDIA = 16
    const val XHR = 32
    const val SUBDOCUMENT = 64
    const val PING = 128
    const val WEBSOCKET = 256
    const val OTHER = 512

    /** Every type a subresource request can have. The main frame is never filtered. */
    const val ALL_REQUESTS = 1023

    /** Page-level switches that exception rules can carry: `$document`, `$generichide`, `$elemhide`. */
    const val DOCUMENT = 1024
    const val GENERICHIDE = 2048
    const val ELEMHIDE = 4096
    const val PAGE_BITS = DOCUMENT or GENERICHIDE or ELEMHIDE

    /** What a request of unknown purpose may be: a script, a fetch, a beacon, or something else. */
    const val UNKNOWN = SCRIPT or XHR or PING or OTHER

    /** The bit for a filter option name, or 0 when it isn't a type. */
    fun fromOption(name: String): Int = when (name) {
        "script" -> SCRIPT
        "image" -> IMAGE
        "stylesheet", "css" -> STYLESHEET
        "font" -> FONT
        "media" -> MEDIA
        "xmlhttprequest", "xhr" -> XHR
        "subdocument", "frame" -> SUBDOCUMENT
        "ping" -> PING
        "websocket" -> WEBSOCKET
        "other", "object", "object-subrequest" -> OTHER
        "document", "doc" -> DOCUMENT
        "generichide", "ghide" -> GENERICHIDE
        "elemhide", "ehide" -> ELEMHIDE
        "all" -> ALL_REQUESTS or DOCUMENT
        else -> 0
    }
}
