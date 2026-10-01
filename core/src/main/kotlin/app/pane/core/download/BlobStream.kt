package app.pane.core.download

import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.io.InputStream

/** one slice of a blob, with the blob's whole size and type. */
class BlobChunk(val bytes: ByteArray, val size: Long, val type: String)

/** reads `[from, to)` of a `blob:` address, or null when the page that made it can't. */
fun interface BlobReader {
    suspend fun read(url: String, from: Long, to: Long): BlobChunk?
}

/** a blob read [slice] bytes at a time. a refill waits on the page, so this belongs on the download's own thread. */
class BlobStream private constructor(
    private val reader: BlobReader,
    private val url: String,
    private val slice: Long,
    first: BlobChunk,
) : InputStream() {
    val size = first.size
    val type = first.type
    private var bytes = first.bytes
    private var at = 0
    private var next = bytes.size.toLong()

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xff
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (at == bytes.size) {
            if (next >= size) return -1
            bytes = runBlocking { reader.read(url, next, next + slice) }?.bytes?.takeIf { it.isNotEmpty() } ?: throw IOException("Lost $url at $next of $size")
            at = 0
            next += bytes.size
        }
        val n = minOf(len, bytes.size - at)
        bytes.copyInto(b, off, at, at + n)
        at += n
        return n
    }

    companion object {
        fun open(reader: BlobReader, url: String, slice: Long): BlobStream {
            val first = runBlocking { reader.read(url, 0, slice) } ?: throw IOException("Can't read $url")
            return BlobStream(reader, url, slice, first)
        }
    }
}
