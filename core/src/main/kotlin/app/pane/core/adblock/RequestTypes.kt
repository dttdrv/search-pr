package app.pane.core.adblock

/**
 * Guesses what a subresource request is for. WebView's `shouldInterceptRequest` gives a URL, a method
 * and the headers the page's renderer set, but not the resource type, so this reads what is there:
 *
 *  1. `Sec-Fetch-Dest` when the engine exposes it (it is added later in the network stack, so usually it doesn't);
 *  2. the `Accept` header, which Chromium sets per kind of request: `text/html...` for frames, `text/css...`
 *     for stylesheets, `image/...` lists for images, `video/` or `audio/` for media elements;
 *  3. a `Range` header on a `*` request, which only media elements send;
 *  4. the file extension of the path.
 *
 * What none of these can settle is `Accept: *` with an extension-less URL, which is how `<script>`,
 * `fetch()`, XHR, workers and beacons all ask. That returns [ResourceType.UNKNOWN], a set of bits, and a rule
 * matches when it shares any: so `$script`, `$xhr`, `$ping` and `$other` rules all apply to such a request,
 * which can block a fetch that a `$script`-only rule never meant, but never lets an obvious ad script through.
 * Fonts, `<object>` and CSS-initiated requests with no extension are likewise guessed at; WebSocket traffic
 * never reaches `shouldInterceptRequest`.
 */
object RequestTypes {
    fun infer(url: String, accept: String?, secFetchDest: String?, method: String?, ranged: Boolean): Int {
        if (secFetchDest != null) fromDestination(secFetchDest)?.let { return it }
        val a = accept?.trimStart().orEmpty()
        when {
            a.startsWith("text/html") || a.startsWith("application/xhtml") -> return ResourceType.SUBDOCUMENT
            a.startsWith("text/css") -> return ResourceType.STYLESHEET
            a.startsWith("image/") -> return ResourceType.IMAGE
            a.startsWith("video/") || a.startsWith("audio/") -> return ResourceType.MEDIA
        }
        val byExtension = fromExtension(extensionOf(url))
        if (byExtension != 0) return byExtension
        if (ranged) return ResourceType.MEDIA
        val post = method != null && !method.equals("GET", true) && !method.equals("HEAD", true)
        return if (post) ResourceType.XHR or ResourceType.PING or ResourceType.OTHER else ResourceType.UNKNOWN
    }

    private fun fromDestination(dest: String): Int? = when (dest.lowercase()) {
        "script" -> ResourceType.SCRIPT
        "style" -> ResourceType.STYLESHEET
        "image" -> ResourceType.IMAGE
        "font" -> ResourceType.FONT
        "iframe", "frame" -> ResourceType.SUBDOCUMENT
        "audio", "video", "track" -> ResourceType.MEDIA
        "empty" -> ResourceType.XHR or ResourceType.PING or ResourceType.OTHER
        "worker", "sharedworker", "serviceworker" -> ResourceType.SCRIPT or ResourceType.OTHER
        "object", "embed", "manifest", "report" -> ResourceType.OTHER
        else -> null
    }

    /** The lower-case extension of the last path segment (no dot), or "". */
    internal fun extensionOf(url: String): String {
        var end = url.length
        val q = url.indexOf('?')
        if (q >= 0) end = q
        val h = url.indexOf('#')
        if (h in 0 until end) end = h
        val slash = url.lastIndexOf('/', end - 1)
        val dot = url.lastIndexOf('.', end - 1)
        // `https://example.com` has dots but no path: the extension must follow the last slash after the authority.
        val authorityEnd = url.indexOf("://").let { if (it < 0) 0 else it + 2 }
        if (dot < 0 || dot < slash || slash <= authorityEnd || end - dot > 6) return ""
        return url.substring(dot + 1, end).lowercase()
    }

    private fun fromExtension(ext: String): Int = when (ext) {
        "js", "mjs", "cjs" -> ResourceType.SCRIPT
        "css" -> ResourceType.STYLESHEET
        "woff", "woff2", "ttf", "otf", "eot" -> ResourceType.FONT
        "png", "jpg", "jpeg", "gif", "webp", "avif", "svg", "ico", "bmp", "apng", "jfif" -> ResourceType.IMAGE
        "mp4", "webm", "m4v", "mov", "ogv", "mp3", "m4a", "ogg", "oga", "wav", "aac", "flac", "m3u8", "mpd", "m4s" -> ResourceType.MEDIA
        "html", "htm" -> ResourceType.SUBDOCUMENT
        "json", "xml", "txt" -> ResourceType.XHR or ResourceType.OTHER
        "swf" -> ResourceType.OTHER
        else -> 0
    }
}
