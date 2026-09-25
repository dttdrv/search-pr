package app.pane.browser.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * Something the page (or engine) is waiting on the user for: an alert, a permission, a file
 * picker… Concrete types live in `engine.prompts`; the UI renders the head of the queue.
 */
interface PromptRequest {
    val id: Long

    /** Null for prompts that aren't tied to a tab. */
    val tabId: String?

    /** Completes the engine-side callback with a neutral answer (deny/cancel). Must be idempotent. */
    fun dismiss()

    companion object {
        private val ids = AtomicLong()
        fun nextId(): Long = ids.incrementAndGet()
    }
}

class PromptQueue {
    private val _requests = MutableStateFlow<List<PromptRequest>>(emptyList())
    val requests: StateFlow<List<PromptRequest>> = _requests.asStateFlow()

    fun enqueue(request: PromptRequest) = _requests.update { it + request }

    /** Removes a request that has been answered. */
    fun remove(request: PromptRequest) = _requests.update { list -> list.filterNot { it.id == request.id } }

    /** Dismisses and drops everything belonging to [tabId], e.g. when it closes or navigates away. */
    fun dismissForTab(tabId: String) {
        val (drop, keep) = _requests.value.partition { it.tabId == tabId }
        _requests.value = keep
        drop.forEach { it.dismiss() }
    }

    fun dismissAll() {
        val all = _requests.value
        _requests.value = emptyList()
        all.forEach { it.dismiss() }
    }
}
