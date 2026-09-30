package app.pane.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PromptQueueTest {
    private class Request(override val tabId: String?, val onDismiss: () -> Unit = {}) : PromptRequest {
        override val id = PromptRequest.nextId()
        var dismissals = 0
        override fun dismiss() {
            dismissals++
            onDismiss()
        }
    }

    @Test fun dismissOnlyClosedTabAndRetainPromptsEnqueuedByCallbacks() {
        val queue = PromptQueue()
        val next = Request("other")
        val closing = Request("closing") { queue.enqueue(next) }
        val global = Request(null)
        queue.enqueue(closing)
        queue.enqueue(global)

        queue.dismissForTab("closing")
        queue.dismissForTab("closing")

        assertEquals(listOf(global, next), queue.requests.value)
        assertEquals(1, closing.dismissals)
        assertEquals(0, global.dismissals)
    }

    @Test fun dismissAllRetainsReentrantRequests() {
        val queue = PromptQueue()
        val next = Request(null)
        val first = Request("tab") { queue.enqueue(next) }
        queue.enqueue(first)
        queue.dismissAll()
        assertEquals(listOf(next), queue.requests.value)
        assertEquals(1, first.dismissals)
    }

    @Test fun closingTabsCannotLoseConcurrentPromptsFromOtherTabs() {
        val queue = PromptQueue()
        val start = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(2)
        try {
            val adding = workers.submit {
                start.await()
                repeat(1000) { queue.enqueue(Request("survivor")) }
            }
            val closing = workers.submit {
                start.await()
                repeat(1000) {
                    queue.enqueue(Request("closing"))
                    queue.dismissForTab("closing")
                }
            }
            start.countDown()
            adding.get(10, TimeUnit.SECONDS)
            closing.get(10, TimeUnit.SECONDS)
            assertEquals(1000, queue.requests.value.size)
            assertTrue(queue.requests.value.all { it.tabId == "survivor" })
        } finally {
            workers.shutdownNow()
        }
    }
}
