package app.pane.core.prompts

/** How to present a page-initiated dialog (`alert`, `confirm`, `prompt`). */
enum class DialogVerdict {
    Show,

    /** The page is looping dialogs: also offer "Don't allow more dialogs from this page". */
    ShowWithOptOut,

    /** The user opted out for this page: answer silently without showing anything. */
    Suppress,
}

/**
 * Stops pages from trapping people in a loop of `alert()`s, the classic scam-page tactic.
 *
 * As in Firefox, a dialog is "successive" when it opens while another is still up or shortly
 * after the previous one closed; the gap is measured from the close because a loop re-opens
 * instantly however long the user took to read. Once more than [maxSuccessive] dialogs arrive in
 * a row, the next ones offer an opt-out. After the user takes it, dialogs from the same page are
 * suppressed until the tab moves to a different page.
 *
 * Call [onDialogOpened] for every dialog and [onDialogClosed] for every one that wasn't suppressed.
 */
class DialogThrottle(
    private val maxSuccessive: Int = 3,
    private val successiveGapMs: Long = 3_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var page: String? = null
    private var streak = 0
    private var open = 0
    private var lastClosedAt: Long? = null
    private var blocked = false

    /** [pageKey] identifies the page that is showing the dialog, e.g. its host. */
    @Synchronized
    fun onDialogOpened(pageKey: String?): DialogVerdict {
        enterPage(pageKey)
        if (blocked) return DialogVerdict.Suppress
        val closedAt = lastClosedAt
        val successive = open > 0 || (closedAt != null && clock() - closedAt <= successiveGapMs)
        streak = if (successive) streak + 1 else 1
        open++
        return if (streak > maxSuccessive) DialogVerdict.ShowWithOptOut else DialogVerdict.Show
    }

    @Synchronized
    fun onDialogClosed() {
        if (open > 0) open--
        lastClosedAt = clock()
    }

    /** The user chose "Don't allow more dialogs from this page". */
    @Synchronized
    fun block() {
        blocked = true
    }

    @get:Synchronized
    val isBlocked: Boolean get() = blocked

    private fun enterPage(pageKey: String?) {
        if (pageKey == page) return
        page = pageKey
        streak = 0
        lastClosedAt = null
        blocked = false
    }
}
