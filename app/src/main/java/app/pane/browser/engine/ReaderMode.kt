package app.pane.browser.engine

import android.content.Context
import app.pane.core.settings.ThemeMode
import org.json.JSONObject

/**
 * The scripts that find out whether a page has an article and show it as one, built from the
 * bundled Readability (Mozilla, Apache 2.0; see assets/reader/LICENSE-readability.md).
 *
 * Both run in the page's own world, so each is a function expression of its own: Readability and
 * everything else stay out of the page's globals, and the page's globals can't shadow them.
 */
internal class ReaderScripts(private val context: Context) {
    private fun asset(name: String): String =
        context.assets.open("reader/$name").bufferedReader().use { it.readText() }

    private val readability by lazy { asset("Readability.js") }
    private val readerable by lazy { asset("Readability-readerable.js") }
    private val reader by lazy { asset("reader.js") }
    private val css by lazy { asset("reader.css") }

    /** Evaluates to 0 (nothing to read), 1 (has an article) or 2 (already showing one). */
    fun detect(): String = """
        (function () {
          try {
            if (window.__paneReader) return 2;
            if (document.querySelector('meta[name="pane-error"]')) return 0;
            var type = document.contentType || "";
            if (type !== "text/html" && type !== "application/xhtml+xml") return 0;
            if (!document.body) return 0;
            var module;
            $readerable
            ;
            return isProbablyReaderable(document) ? 1 : 0;
          } catch (e) {
            return 0;
          }
        })()
    """

    /** Replaces the page with its article. Evaluates to "ok", "active" or "unavailable". */
    fun enter(theme: ThemeMode, viewOriginalLabel: String): String {
        val options = JSONObject()
            .put("css", css)
            .put("theme", themeName(theme))
            .put("original", viewOriginalLabel)
        return """
            (function () {
              try {
                var module;
                $readability
                ;
                var OPTIONS = $options;
                return (function () {
                  $reader
                })();
              } catch (e) {
                return "unavailable";
              }
            })()
        """
    }

    internal companion object {
        fun themeName(theme: ThemeMode): String = when (theme) {
            ThemeMode.Dark -> "dark"
            ThemeMode.Light -> "light"
            ThemeMode.System -> "auto"
        }

        /** Pages worth probing: real web documents, not the app's own pages. */
        fun isReadableScheme(url: String): Boolean =
            url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
    }
}
