package app.pane.core.library

/**
 * Picks a file name that doesn't collide with an existing one, the way desktop browsers do:
 * `report.pdf`, `report (1).pdf`, `report (2).pdf`… Used where the platform won't do it for us
 * (app-specific storage on Android 9 and older).
 */
object UniqueNames {
    private const val MAX_ATTEMPTS = 10_000

    fun next(name: String, taken: (String) -> Boolean): String {
        if (!taken(name)) return name
        val dot = name.lastIndexOf('.')
        // A leading dot is part of the name, not an extension.
        val (base, ext) = if (dot > 0) name.substring(0, dot) to name.substring(dot) else name to ""
        for (i in 1..MAX_ATTEMPTS) {
            val candidate = "$base ($i)$ext"
            if (!taken(candidate)) return candidate
        }
        return "$base (${System.currentTimeMillis()})$ext"
    }
}
