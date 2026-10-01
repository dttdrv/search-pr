package app.pane.browser.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadRecordTest {
    private fun record(status: DownloadStatus) = DownloadRecord(1, "", "file.bin", null, null, -1L, 0L, status, 0L)

    /** the download service runs for exactly as long as one of these is in the list, so a new status must pick a side. */
    @Test fun aDownloadIsActiveUntilItEnds() {
        val ended = setOf(DownloadStatus.Completed, DownloadStatus.Failed, DownloadStatus.Cancelled)
        DownloadStatus.entries.forEach { assertEquals(it.toString(), it !in ended, record(it).isActive) }
    }
}
