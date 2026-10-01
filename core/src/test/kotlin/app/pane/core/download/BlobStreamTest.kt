package app.pane.core.download

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BlobStreamTest {
    private val slice = 7L

    /** serves [data] the way the page does: at most the requested range, with the blob's whole size and type. */
    private class Page(val data: ByteArray, val failAt: Long = Long.MAX_VALUE, val most: Long = Long.MAX_VALUE / 2) : BlobReader {
        val asked = mutableListOf<LongRange>()

        override suspend fun read(url: String, from: Long, to: Long): BlobChunk? {
            asked += from until to
            if (from >= failAt) return null
            val end = minOf(to, from + most, data.size.toLong())
            return BlobChunk(data.copyOfRange(from.toInt(), end.toInt()), data.size.toLong(), "text/plain")
        }
    }

    private fun payload(size: Int) = ByteArray(size) { (it * 31 + 7).toByte() }

    @Test fun readsEverySliceInOrderWithoutAskingTwice() {
        // past one slice, and not a multiple of it, so the last slice is short
        val data = payload((slice * 3 + 2).toInt())
        val page = Page(data)
        val stream = BlobStream.open(page, "blob:x", slice)
        assertEquals(data.size.toLong(), stream.size)
        assertEquals("text/plain", stream.type)
        assertContentEquals(data, stream.readBytes())
        assertEquals((0 until 4).map { (it * slice) until (it + 1) * slice }, page.asked)
    }

    @Test fun aSliceShorterThanAskedIsNotSkippedOver() {
        val data = payload(40)
        assertContentEquals(data, BlobStream.open(Page(data, most = slice - 4), "blob:x", slice).readBytes())
    }

    @Test fun readsInBuffersSmallerAndLargerThanASlice() {
        val data = payload(50)
        for (buffer in listOf(1, 3, slice.toInt(), 100)) {
            val stream = BlobStream.open(Page(data), "blob:x", slice)
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(buffer)
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
            }
            assertContentEquals(data, out.toByteArray(), "buffer $buffer")
        }
    }

    @Test fun singleByteReadsAreUnsigned() {
        val stream = BlobStream.open(Page(byteArrayOf(-1, 0, 127)), "blob:x", slice)
        assertEquals(listOf(255, 0, 127, -1), List(4) { stream.read() })
    }

    @Test fun anEmptyBlobIsAnEmptyStream() {
        val stream = BlobStream.open(Page(ByteArray(0)), "blob:x", slice)
        assertEquals(0L, stream.size)
        assertEquals(-1, stream.read())
    }

    @Test fun aPageThatGoesAwayFailsTheDownloadInsteadOfEndingItEarly() {
        val data = payload((slice * 2).toInt())
        val stream = BlobStream.open(Page(data, failAt = slice), "blob:x", slice)
        assertFailsWith<IOException> { stream.readBytes() }
        assertFailsWith<IOException> { BlobStream.open(Page(data, failAt = 0), "blob:x", slice) }
    }
}
