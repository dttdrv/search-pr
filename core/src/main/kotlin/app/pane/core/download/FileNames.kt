package app.pane.core.download

import java.net.URLDecoder

/** Picks a safe file name for a download. */
object FileNames {
    private val reserved = setOf(
        "con", "prn", "aux", "nul", "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
        "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9",
    )
    private val dangerousExtensions = setOf("apk", "apks", "xapk", "aab", "exe", "msi", "bat", "cmd", "scr", "ps1", "vbs", "jar", "sh")

    private val mimeToExt = mapOf(
        "application/pdf" to "pdf", "image/jpeg" to "jpg", "image/png" to "png", "image/gif" to "gif",
        "image/webp" to "webp", "image/avif" to "avif", "image/svg+xml" to "svg", "video/mp4" to "mp4",
        "video/webm" to "webm", "audio/mpeg" to "mp3", "audio/ogg" to "ogg", "audio/wav" to "wav",
        "application/zip" to "zip", "application/json" to "json", "text/plain" to "txt", "text/html" to "html",
        "text/csv" to "csv", "application/epub+zip" to "epub", "application/x-xpinstall" to "xpi",
        "application/vnd.android.package-archive" to "apk", "application/msword" to "doc",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "docx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to "xlsx",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" to "pptx",
        "text/calendar" to "ics", "text/vcard" to "vcf",
    )

    fun extensionForMime(mime: String?): String? = mime?.substringBefore(';')?.trim()?.lowercase()?.let { mimeToExt[it] }

    /**
     * Derives a name from `Content-Disposition`, falling back to the URL path and the MIME type.
     * The result never contains path separators, control characters or a leading dot.
     */
    fun choose(contentDisposition: String?, url: String, mimeType: String?): String {
        val fromHeader = contentDisposition?.let { fromContentDisposition(it) }
        val fromUrl = url.substringBefore('#').substringBefore('?').substringAfterLast('/').let { decode(it) }
        var name = sanitize(fromHeader ?: fromUrl)
        if (name.isEmpty()) name = "download"
        if (!name.contains('.')) {
            extensionForMime(mimeType)?.let { name = "$name.$it" }
        }
        return name
    }

    fun fromContentDisposition(header: String): String? {
        // RFC 6266 / 5987: filename*=UTF-8''na%C3%AFve.txt takes priority.
        Regex("filename\\*\\s*=\\s*([^;]+)", RegexOption.IGNORE_CASE).find(header)?.let { m ->
            val value = m.groupValues[1].trim().trim('"')
            val parts = value.split("'", limit = 3)
            if (parts.size == 3) {
                val charset = runCatching { charset(parts[0].ifEmpty { "UTF-8" }) }.getOrDefault(Charsets.UTF_8)
                runCatching { return URLDecoder.decode(parts[2], charset.name()) }
            }
        }
        Regex("filename\\s*=\\s*(\"((?:[^\"\\\\]|\\\\.)*)\"|[^;]+)", RegexOption.IGNORE_CASE).find(header)?.let { m ->
            val quoted = m.groupValues[2]
            return (quoted.ifEmpty { m.groupValues[1] }).replace("\\\"", "\"").trim()
        }
        return null
    }

    fun sanitize(name: String): String {
        var n = name.substringAfterLast('/').substringAfterLast('\\')
        n = n.filter { !it.isISOControl() && it !in "<>:\"|?*" }
        n = n.trim().trimStart('.').trimEnd('.', ' ')
        if (n.length > 120) {
            val ext = n.substringAfterLast('.', "").take(10)
            n = n.take(120 - ext.length - 1) + if (ext.isNotEmpty()) ".$ext" else ""
        }
        if (n.substringBefore('.').lowercase() in reserved) n = "_$n"
        return n
    }

    /** True for files that can execute code if opened; the UI warns before saving these. */
    fun isPotentiallyDangerous(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in dangerousExtensions

    private fun decode(s: String) = try {
        URLDecoder.decode(s, "UTF-8")
    } catch (_: IllegalArgumentException) {
        s
    }
}
