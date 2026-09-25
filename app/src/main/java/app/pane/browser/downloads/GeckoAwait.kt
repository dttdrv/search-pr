package app.pane.browser.downloads

import kotlinx.coroutines.suspendCancellableCoroutine
import org.mozilla.geckoview.GeckoResult
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Suspends until this [GeckoResult] completes. Call it from a thread with a Looper (the main
 * dispatcher), which is where Gecko delivers the result. The value may be null (`Void` results).
 */
internal suspend fun <T> GeckoResult<T>.await(): T? = suspendCancellableCoroutine { cont ->
    accept(
        { value -> if (cont.isActive) cont.resume(value) },
        { error -> if (cont.isActive) cont.resumeWithException(error ?: IllegalStateException("GeckoResult failed")) },
    )
}
