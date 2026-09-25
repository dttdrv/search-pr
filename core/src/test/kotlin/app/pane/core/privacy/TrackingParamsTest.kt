package app.pane.core.privacy

import kotlin.test.Test
import kotlin.test.assertEquals

class TrackingParamsTest {
    @Test fun stripsCommonTrackers() {
        assertEquals("https://a.com/p?id=3", TrackingParams.strip("https://a.com/p?utm_source=x&id=3&fbclid=abc"))
        assertEquals("https://a.com/p", TrackingParams.strip("https://a.com/p?utm_source=x&gclid=1"))
        assertEquals("https://a.com/p#frag", TrackingParams.strip("https://a.com/p?utm_medium=x#frag"))
    }

    @Test fun hostSpecificRulesOnlyApplyToThatHost() {
        assertEquals("https://youtu.be/abc?t=10", TrackingParams.strip("https://youtu.be/abc?si=XYZ&t=10"))
        assertEquals("https://example.com/?si=keep", TrackingParams.strip("https://example.com/?si=keep"))
    }

    @Test fun leavesCleanUrlsAlone() {
        assertEquals("https://a.com/p?q=1&page=2", TrackingParams.strip("https://a.com/p?q=1&page=2"))
        assertEquals("https://a.com/", TrackingParams.strip("https://a.com/"))
    }
}
