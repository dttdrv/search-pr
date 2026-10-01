package app.pane.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderThemeTest {
    // reader.js only knows these two values; anything else makes the article follow the page's own media query instead of the app
    @Test fun anArticleIsAlwaysLightOrDark() {
        assertEquals("dark", ReaderScripts.themeName(true))
        assertEquals("light", ReaderScripts.themeName(false))
    }
}
