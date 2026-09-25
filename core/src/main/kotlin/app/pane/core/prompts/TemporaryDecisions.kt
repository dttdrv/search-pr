package app.pane.core.prompts

/**
 * Answers given without "Remember", kept for a while so a page that asks again straight away gets
 * the same answer instead of another sheet (and can't nag its way to a "yes"). Mirrors Firefox's
 * hour-long temporary permissions.
 */
class TemporaryDecisions<K>(
    private val ttlMs: Long = 60 * 60 * 1000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Entry(val allowed: Boolean, val expiresAt: Long)

    private val entries = HashMap<K, Entry>()

    @Synchronized
    fun record(key: K, allowed: Boolean) {
        entries[key] = Entry(allowed, clock() + ttlMs)
    }

    /** The remembered answer, or null if there is none or it has expired. */
    @Synchronized
    fun lookup(key: K): Boolean? {
        val entry = entries[key] ?: return null
        if (clock() >= entry.expiresAt) {
            entries.remove(key)
            return null
        }
        return entry.allowed
    }

    @Synchronized
    fun forget(key: K) {
        entries.remove(key)
    }

    @Synchronized
    fun clear() = entries.clear()
}
