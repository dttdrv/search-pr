package app.pane.browser.extensions

import kotlinx.coroutines.suspendCancellableCoroutine
import org.mozilla.geckoview.GeckoResult
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Suspends until the engine answers. GeckoResult dispatches its listeners on the Looper of the
 * thread that attached them, so call this from the main thread.
 */
internal suspend fun <T> GeckoResult<T>.await(): T? = suspendCancellableCoroutine { cont ->
    accept(
        { value -> if (cont.isActive) cont.resume(value) },
        { error -> if (cont.isActive) cont.resumeWithException(error ?: IllegalStateException("GeckoResult failed")) },
    )
}

/**
 * A [GeckoResult] the engine is waiting on, completed at most once. Completing a GeckoResult twice
 * throws, and user answers can race with dismissals (back gesture, the sheet being swiped away),
 * so every answer goes through here.
 */
internal class PendingResult<T>(val result: GeckoResult<T> = GeckoResult()) {
    private val done = AtomicBoolean(false)

    val isDone: Boolean get() = done.get()

    /** Returns false when the result had already been completed. */
    fun complete(value: T): Boolean {
        if (!done.compareAndSet(false, true)) return false
        result.complete(value)
        return true
    }
}
