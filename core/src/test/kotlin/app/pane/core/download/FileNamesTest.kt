package app.pane.core.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileNamesTest {
    @Test fun prefersRfc5987Name() {
        assertEquals("naïve file.txt", FileNames.choose("attachment; filename=\"fallback.txt\"; filename*=UTF-8''na%C3%AFve%20file.txt", "https://x/y", null))
    }

    @Test fun quotedAndBareNames() {
        assertEquals("report.pdf", FileNames.choose("attachment; filename=\"report.pdf\"", "https://x/y", null))
        assertEquals("report.pdf", FileNames.choose("attachment; filename=report.pdf", "https://x/y", null))
    }

    @Test fun fallsBackToUrlAndMime() {
        assertEquals("photo.jpg", FileNames.choose(null, "https://x/img/photo.jpg?w=1", "image/jpeg"))
        assertEquals("download.pdf", FileNames.choose(null, "https://x/", "application/pdf"))
        assertEquals("file.pdf", FileNames.choose(null, "https://x/file", "application/pdf; charset=binary"))
    }

    @Test fun neutralisesPathTraversalAndHiddenFiles() {
        assertEquals("passwd", FileNames.choose("attachment; filename=\"../../etc/passwd\"", "https://x/", null))
        assertEquals("bashrc", FileNames.sanitize(".bashrc"))
        assertEquals("_con.txt", FileNames.sanitize("con.txt"))
        assertEquals("ab.txt", FileNames.sanitize("a\u0000b.txt"))
    }

    @Test fun capsLength() {
        val long = "a".repeat(300) + ".txt"
        val s = FileNames.sanitize(long)
        assertTrue(s.length <= 120 && s.endsWith(".txt"))
    }

    @Test fun flagsExecutables() {
        assertTrue(FileNames.isPotentiallyDangerous("thing.APK"))
    }
}
