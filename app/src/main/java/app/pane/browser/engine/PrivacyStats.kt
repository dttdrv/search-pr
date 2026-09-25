package app.pane.browser.engine

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A rolling seven-day count of blocked trackers for the start page's privacy report. Only a
 * number per day is kept — never which sites or trackers.
 */
class PrivacyStats(context: Context, private val clock: () -> Long = System::currentTimeMillis) {
    private val prefs = context.getSharedPreferences("pane_stats", Context.MODE_PRIVATE)
    private val _weekTotal = MutableStateFlow(computeWeek())
    val weekTotal: StateFlow<Int> = _weekTotal.asStateFlow()
    private var pending = 0

    fun increment() {
        pending++
        // Batch writes; trackers arrive in bursts.
        if (pending >= 10) flush()
        _weekTotal.value = computeWeek() + pending
    }

    fun flush() {
        if (pending == 0) return
        val key = dayKey(clock())
        prefs.edit { putInt(key, prefs.getInt(key, 0) + pending) }
        pending = 0
        prune()
    }

    fun reset() {
        prefs.edit { clear() }
        pending = 0
        _weekTotal.value = 0
    }

    private fun computeWeek(): Int {
        val today = clock() / DAY
        return (0 until 7).sumOf { prefs.getInt("d${today - it}", 0) }
    }

    private fun prune() {
        val today = clock() / DAY
        val stale = prefs.all.keys.filter { it.startsWith("d") && (it.drop(1).toLongOrNull() ?: 0) < today - 7 }
        if (stale.isNotEmpty()) prefs.edit { stale.forEach(::remove) }
    }

    private fun dayKey(now: Long) = "d${now / DAY}"

    private companion object {
        const val DAY = 86_400_000L
    }
}
