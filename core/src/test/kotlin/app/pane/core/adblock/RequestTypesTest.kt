package app.pane.core.adblock

import kotlin.test.Test
import kotlin.test.assertEquals

class RequestTypesTest {
    private fun infer(url: String, accept: String? = "*/*", dest: String? = null, method: String? = "GET", ranged: Boolean = false) =
        RequestTypes.infer(url, accept, dest, method, ranged)

    @Test fun acceptHeaderDecidesWhenItSpeaks() {
        assertEquals(ResourceType.SUBDOCUMENT, infer("https://a.com/frame", "text/html,application/xhtml+xml,*/*;q=0.8"))
        assertEquals(ResourceType.STYLESHEET, infer("https://fonts.googleapis.com/css2?family=Inter", "text/css,*/*;q=0.1"))
        assertEquals(ResourceType.IMAGE, infer("https://a.com/img?id=3", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"))
        assertEquals(ResourceType.MEDIA, infer("https://a.com/v", "video/mp4"))
    }

    @Test fun extensionsSettleWildcardRequests() {
        assertEquals(ResourceType.SCRIPT, infer("https://a.com/x/app.min.js?v=3"))
        assertEquals(ResourceType.FONT, infer("https://a.com/f/r.woff2"))
        assertEquals(ResourceType.IMAGE, infer("https://a.com/p/photo.JPG#x"))
        assertEquals(ResourceType.MEDIA, infer("https://a.com/seg/1.m4s"))
    }

    @Test fun rangeMeansMedia() {
        assertEquals(ResourceType.MEDIA, infer("https://a.com/stream/12345", ranged = true))
    }

    @Test fun whatCannotBeToldIsEverythingItCouldBe() {
        assertEquals(ResourceType.UNKNOWN, infer("https://a.com/collect?x=1"))
        assertEquals(ResourceType.UNKNOWN, infer("https://a.com"))
        assertEquals(ResourceType.UNKNOWN, infer("https://a.com/some.thing/with/dots"))
        assertEquals(ResourceType.XHR or ResourceType.PING or ResourceType.OTHER, infer("https://a.com/beacon", method = "POST"))
    }

    @Test fun secFetchDestWinsWhenPresent() {
        assertEquals(ResourceType.SCRIPT, infer("https://a.com/x", dest = "script"))
        assertEquals(ResourceType.XHR or ResourceType.PING or ResourceType.OTHER, infer("https://a.com/x.js", dest = "empty"))
        assertEquals(ResourceType.UNKNOWN, infer("https://a.com/x", dest = "somethingnew"))
    }

    @Test fun extensionParsing() {
        assertEquals("js", RequestTypes.extensionOf("https://a.com/b/c.js?x=a.b"))
        assertEquals("", RequestTypes.extensionOf("https://a.com"))
        assertEquals("", RequestTypes.extensionOf("https://a.com/dir.v2/file"))
        assertEquals("", RequestTypes.extensionOf("https://a.com/b/c.verylongextension"))
    }
}
