package app.pane.browser.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

/**
 * Small text fetches (search suggestions) with a plain `HttpURLConnection`: https only (plain http
 * just for localhost), short timeouts, a 256 KB cap, no cookies and nothing cached on disk. It
 * stays out of the web view on purpose, so it never shares state with browsing.
 */
class WebFetcher {
    /**
     * The body of a successful response as text, or null if the request failed, was refused
     * (not https) or the server answered with an error. Bodies beyond [MAX_BYTES] are cut off.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun text(url: String, private: Boolean = false): String? {
        val body = fetch(url, "application/json, text/plain;q=0.9, */*;q=0.1", MAX_BYTES, truncate = true) ?: return null
        return String(body.bytes, charsetOf(body.contentType))
    }

    /** A response body of at most [limit] bytes (null if it is larger, or on any failure): image previews. */
    suspend fun bytes(url: String, accept: String, limit: Int): ByteArray? =
        fetch(url, accept, limit, truncate = false)?.bytes

    private class Body(val bytes: ByteArray, val contentType: String?)

    private suspend fun fetch(url: String, accept: String, limit: Int, truncate: Boolean): Body? {
        val target = parse(url) ?: return null
        val connection = try {
            target.openConnection() as? HttpURLConnection
        } catch (_: Exception) {
            null
        } ?: return null
        return coroutineScope {
            // A read blocked on the network doesn't notice cancellation; closing the connection wakes it.
            val closer = launch(Dispatchers.IO) {
                try {
                    awaitCancellation()
                } finally {
                    connection.disconnect()
                }
            }
            try {
                withContext(Dispatchers.IO) { read(connection, accept, limit, truncate) }
            } finally {
                closer.cancel()
            }
        }
    }

    /** Blocking. Null on any failure. */
    private fun read(connection: HttpURLConnection, accept: String, limit: Int, truncate: Boolean): Body? = try {
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.useCaches = false
        connection.instanceFollowRedirects = true
        connection.requestMethod = "GET"
        // A generic user agent: the system's default one names the device model.
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept", accept)
        if (connection.responseCode !in 200..299) {
            null
        } else {
            val out = ByteArrayOutputStream()
            var tooBig = false
            connection.inputStream.use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    if (out.size() >= limit) {
                        tooBig = !truncate
                        break
                    }
                    val n = input.read(buffer, 0, minOf(buffer.size, limit - out.size()))
                    if (n < 0) break
                    out.write(buffer, 0, n)
                }
            }
            if (tooBig) null else Body(out.toByteArray(), connection.contentType)
        }
    } catch (_: Exception) {
        null
    } finally {
        runCatching { connection.disconnect() }
    }

    private fun parse(url: String): URL? {
        val parsed = try {
            URL(url.trim())
        } catch (_: Exception) {
            return null
        }
        val secure = parsed.protocol.equals("https", ignoreCase = true)
        val local = parsed.protocol.equals("http", ignoreCase = true) && isLocalHost(parsed.host)
        return parsed.takeIf { (secure || local) && parsed.host.isNotEmpty() }
    }

    private fun isLocalHost(host: String): Boolean {
        val h = host.lowercase()
        return h == "localhost" || h == "127.0.0.1" || h == "[::1]" || h.endsWith(".localhost")
    }

    /** The charset named in a `Content-Type` header; UTF-8 when absent or unknown. */
    private fun charsetOf(contentType: String?): Charset {
        val name = contentType
            ?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("charset=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim()
            ?.trim('"')
            ?: return Charsets.UTF_8
        return try {
            Charset.forName(name)
        } catch (_: Exception) {
            Charsets.UTF_8
        }
    }

    private companion object {
        const val MAX_BYTES = 256 * 1024
        const val BUFFER_SIZE = 16 * 1024
        const val CONNECT_TIMEOUT_MS = 6_000
        const val READ_TIMEOUT_MS = 6_000
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Mobile Safari/537.36"
    }
}
