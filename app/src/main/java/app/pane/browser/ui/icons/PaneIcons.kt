package app.pane.browser.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Pane's own line icons: 24pt grid, 1.75pt round strokes, in the spirit of SF Symbols. Drawn in
 * black and tinted by `Icon`, so they follow the theme.
 */
object PaneIcons {
    private fun icon(name: String, vararg paths: String, stroke: Float = 1.75f, filled: Boolean = false): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                for (d in paths) {
                    addPath(
                        pathData = addPathNodes(d),
                        fill = if (filled) SolidColor(Color.Black) else null,
                        stroke = if (filled) null else SolidColor(Color.Black),
                        strokeLineWidth = stroke,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
            .build()

    val Back by lazy { icon("back", "M15 5l-7 7 7 7", stroke = 2.1f) }
    val Forward by lazy { icon("forward", "M9 5l7 7-7 7", stroke = 2.1f) }
    val ChevronRight by lazy { icon("chevronRight", "M9.5 6l6 6-6 6", stroke = 2f) }
    val ChevronDown by lazy { icon("chevronDown", "M6 9.5l6 6 6-6", stroke = 2f) }
    val ChevronUp by lazy { icon("chevronUp", "M6 14.5l6-6 6 6", stroke = 2f) }
    val Share by lazy {
        icon("share", "M12 3v11.5", "M8.5 6.5L12 3l3.5 3.5",
            "M8 10H6.5A1.5 1.5 0 0 0 5 11.5v8A1.5 1.5 0 0 0 6.5 21h11a1.5 1.5 0 0 0 1.5-1.5v-8A1.5 1.5 0 0 0 17.5 10H16")
    }
    val Tabs by lazy {
        icon("tabs", "M8 8V6.5A2.5 2.5 0 0 1 10.5 4h7A2.5 2.5 0 0 1 20 6.5v7a2.5 2.5 0 0 1-2.5 2.5H16",
            "M6.5 8h7A2.5 2.5 0 0 1 16 10.5v7a2.5 2.5 0 0 1-2.5 2.5h-7A2.5 2.5 0 0 1 4 17.5v-7A2.5 2.5 0 0 1 6.5 8z")
    }
    val Plus by lazy { icon("plus", "M12 5v14", "M5 12h14", stroke = 2f) }
    val Close by lazy { icon("close", "M6.5 6.5l11 11", "M17.5 6.5l-11 11", stroke = 2f) }
    val CloseCircle by lazy { icon("closeCircle", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z", "M9.2 9.2l5.6 5.6", "M14.8 9.2l-5.6 5.6") }
    val Search by lazy { icon("search", "M10.5 17a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13z", "M15.5 15.5L20 20", stroke = 2f) }
    val Lock by lazy {
        icon("lock", "M8 11V8a4 4 0 0 1 8 0v3", "M7 11h10a1.5 1.5 0 0 1 1.5 1.5v6A1.5 1.5 0 0 1 17 20H7a1.5 1.5 0 0 1-1.5-1.5v-6A1.5 1.5 0 0 1 7 11z")
    }
    val LockFill by lazy {
        icon("lockFill", "M7 10.5V8a5 5 0 0 1 10 0v2.5h-2V8a3 3 0 0 0-6 0v2.5z",
            "M6.5 10.5h11A1.5 1.5 0 0 1 19 12v7a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 5 19v-7a1.5 1.5 0 0 1 1.5-1.5z", filled = true)
    }
    val Warning by lazy { icon("warning", "M12 4L2.8 19.5h18.4z", "M12 10v4", "M12 16.8v.01", stroke = 1.9f) }
    val Book by lazy { icon("book", "M5 5.5A2.5 2.5 0 0 1 7.5 3H19v15H7.5A2.5 2.5 0 0 0 5 20.5z", "M5 20.5A2.5 2.5 0 0 0 7.5 23", "M5 20.5V20a2 2 0 0 1 2-2h12v3H7.5") }
    val Bookmark by lazy { icon("bookmark", "M7 3.5h10a1 1 0 0 1 1 1V21l-6-4-6 4V4.5a1 1 0 0 1 1-1z") }
    val BookmarkFill by lazy { icon("bookmarkFill", "M7 3.5h10a1 1 0 0 1 1 1V21l-6-4-6 4V4.5a1 1 0 0 1 1-1z", filled = true) }
    val Star by lazy { icon("star", "M12 3.5l2.6 5.3 5.9.9-4.25 4.1 1 5.8L12 16.9l-5.25 2.7 1-5.8L3.5 9.7l5.9-.9z") }
    val StarFill by lazy { icon("starFill", "M12 3.5l2.6 5.3 5.9.9-4.25 4.1 1 5.8L12 16.9l-5.25 2.7 1-5.8L3.5 9.7l5.9-.9z", filled = true) }
    val Clock by lazy { icon("clock", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z", "M12 7v5l3.2 2") }
    val Download by lazy { icon("download", "M12 4v11", "M7.5 10.5L12 15l4.5-4.5", "M5 20h14", stroke = 1.9f) }
    val Puzzle by lazy {
        icon("puzzle", "M10 4.5a2 2 0 0 1 4 0V6h3.5A1.5 1.5 0 0 1 19 7.5V11h-1.5a2 2 0 0 0 0 4H19v3.5a1.5 1.5 0 0 1-1.5 1.5H14v-1.5a2 2 0 0 0-4 0V20H6.5A1.5 1.5 0 0 1 5 18.5V15h1.5a2 2 0 0 0 0-4H5V7.5A1.5 1.5 0 0 1 6.5 6H10z")
    }
    val Sliders by lazy { icon("sliders", "M4 7h9", "M17 7h3", "M15 5v4", "M4 17h3", "M11 17h9", "M9 15v4", stroke = 1.9f) }
    val Gear by lazy {
        icon("gear", "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z",
            "M19.4 13.5l1.6 1.2-2 3.5-1.9-.7a7.5 7.5 0 0 1-2.1 1.2L14.7 21h-4l-.4-2.3a7.5 7.5 0 0 1-2.1-1.2l-1.9.7-2-3.5 1.6-1.2a7.6 7.6 0 0 1 0-2.4L4.3 9.8l2-3.5 1.9.7a7.5 7.5 0 0 1 2.1-1.2L10.7 3h4l.4 2.3a7.5 7.5 0 0 1 2.1 1.2l1.9-.7 2 3.5-1.6 1.2a7.6 7.6 0 0 1 0 2.4z")
    }
    val Private by lazy {
        icon("private", "M3.5 3.5l17 17",
            "M10.6 5.1A9.7 9.7 0 0 1 12 5c5 0 8.5 4.5 9.5 7a13 13 0 0 1-2.7 3.9",
            "M6.6 6.6C4.6 8 3.2 10 2.5 12c1 2.5 4.5 7 9.5 7 1.9 0 3.6-.6 5-1.5",
            "M9.9 9.9a3 3 0 0 0 4.2 4.2")
    }
    val Reload by lazy { icon("reload", "M19.5 12a7.5 7.5 0 1 1-2.2-5.3", "M19.5 4.5V9H15", stroke = 1.9f) }
    val More by lazy { icon("more", "M6 12h.01", "M12 12h.01", "M18 12h.01", stroke = 2.6f) }
    val MoreCircle by lazy { icon("moreCircle", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z", "M8 12h.01", "M12 12h.01", "M16 12h.01") }
    val Reader by lazy { icon("reader", "M5 6h14", "M5 10h14", "M5 14h14", "M5 18h9") }
    val TextSize by lazy { icon("textSize", "M3 18l4-11 4 11", "M4.5 14h5", "M14 18l3-8 3 8", "M15 15.5h4") }
    val Moon by lazy { icon("moon", "M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z") }
    val Sun by lazy {
        icon("sun", "M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8z", "M12 2.5v2", "M12 19.5v2", "M4.6 4.6l1.4 1.4", "M18 18l1.4 1.4",
            "M2.5 12h2", "M19.5 12h2", "M4.6 19.4L6 18", "M18 6l1.4-1.4")
    }
    val Shield by lazy { icon("shield", "M12 3l7 3v5.5c0 4.5-3 8-7 9.5-4-1.5-7-5-7-9.5V6z") }
    val ShieldCheck by lazy { icon("shieldCheck", "M12 3l7 3v5.5c0 4.5-3 8-7 9.5-4-1.5-7-5-7-9.5V6z", "M9 12l2 2 4-4") }
    val Globe by lazy {
        icon("globe", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z", "M3 12h18",
            "M12 3c2.5 2.5 3.8 5.5 3.8 9s-1.3 6.5-3.8 9c-2.5-2.5-3.8-5.5-3.8-9S9.5 5.5 12 3z")
    }
    val Home by lazy { icon("home", "M4 10.5L12 4l8 6.5V19a1 1 0 0 1-1 1h-4.5v-6h-5v6H5a1 1 0 0 1-1-1z") }
    val Desktop by lazy {
        icon("desktop", "M5.5 4h13A1.5 1.5 0 0 1 20 5.5v9a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 14.5v-9A1.5 1.5 0 0 1 5.5 4z", "M9 20h6", "M12 16v4")
    }
    val Phone by lazy { icon("phone", "M7.5 3h9A1.5 1.5 0 0 1 18 4.5v15a1.5 1.5 0 0 1-1.5 1.5h-9A1.5 1.5 0 0 1 6 19.5v-15A1.5 1.5 0 0 1 7.5 3z", "M11 18h2") }
    val FindInPage by lazy {
        icon("findInPage", "M13 3H6.5A1.5 1.5 0 0 0 5 4.5v15A1.5 1.5 0 0 0 6.5 21H11", "M13 3l5 5v2.5", "M13 3v5h5",
            "M16.5 19a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z", "M18.4 18.4L21 21")
    }
    val Copy by lazy {
        icon("copy", "M10.5 8h8A1.5 1.5 0 0 1 20 9.5v9a1.5 1.5 0 0 1-1.5 1.5h-8A1.5 1.5 0 0 1 9 18.5v-9A1.5 1.5 0 0 1 10.5 8z",
            "M16 8V5.5A1.5 1.5 0 0 0 14.5 4h-9A1.5 1.5 0 0 0 4 5.5v9A1.5 1.5 0 0 0 5.5 16H9")
    }
    val Link by lazy { icon("link", "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1", "M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1") }
    val Trash by lazy {
        icon("trash", "M4 7h16", "M9.5 7V4.5h5V7", "M6.5 7l.9 12.1A1.5 1.5 0 0 0 8.9 20.5h6.2a1.5 1.5 0 0 0 1.5-1.4L17.5 7", "M10 11v6", "M14 11v6")
    }
    val Check by lazy { icon("check", "M5 12.5l4.5 4.5L19 7.5", stroke = 2.2f) }
    val Mic by lazy { icon("mic", "M12 3a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3z", "M6 11a6 6 0 0 0 12 0", "M12 17v4") }
    val Camera by lazy {
        icon("camera", "M5.5 7h2l1.5-2h6l1.5 2h2A1.5 1.5 0 0 1 20 8.5v9a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 17.5v-9A1.5 1.5 0 0 1 5.5 7z",
            "M12 16a3 3 0 1 0 0-6 3 3 0 0 0 0 6z")
    }
    val Location by lazy { icon("location", "M12 21s-6.5-5.5-6.5-11a6.5 6.5 0 0 1 13 0c0 5.5-6.5 11-6.5 11z", "M12 12.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z") }
    val Bell by lazy { icon("bell", "M6 16v-5a6 6 0 0 1 12 0v5l1.5 2h-15z", "M10 20.5a2 2 0 0 0 4 0") }
    val Info by lazy { icon("info", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z", "M12 11v5", "M12 8h.01") }
    val Grid by lazy {
        icon("grid", "M5.5 4h4A1.5 1.5 0 0 1 11 5.5v4A1.5 1.5 0 0 1 9.5 11h-4A1.5 1.5 0 0 1 4 9.5v-4A1.5 1.5 0 0 1 5.5 4z",
            "M14.5 4h4A1.5 1.5 0 0 1 20 5.5v4a1.5 1.5 0 0 1-1.5 1.5h-4A1.5 1.5 0 0 1 13 9.5v-4A1.5 1.5 0 0 1 14.5 4z",
            "M5.5 13h4a1.5 1.5 0 0 1 1.5 1.5v4A1.5 1.5 0 0 1 9.5 20h-4A1.5 1.5 0 0 1 4 18.5v-4A1.5 1.5 0 0 1 5.5 13z",
            "M14.5 13h4a1.5 1.5 0 0 1 1.5 1.5v4a1.5 1.5 0 0 1-1.5 1.5h-4a1.5 1.5 0 0 1-1.5-1.5v-4a1.5 1.5 0 0 1 1.5-1.5z")
    }
    val OpenExternal by lazy {
        icon("openExternal", "M14 4h6v6", "M20 4l-9 9", "M18 14v4.5a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 4 18.5v-11A1.5 1.5 0 0 1 5.5 6H10")
    }
    val Key by lazy { icon("key", "M14.5 13a4.5 4.5 0 1 0-4.1-2.6L4 16.8V20h3.2v-2h2v-2h2l.9-.9A4.5 4.5 0 0 0 14.5 13z", "M16 8h.01") }
    val Palette by lazy {
        icon("palette", "M12 21a9 9 0 1 1 9-9c0 2-1.5 3-3.5 3H16a2 2 0 0 0-1.5 3.3c.4.5.5 1 .5 1.2 0 .8-.9 1.5-3 1.5z",
            "M7.5 11h.01", "M10.5 7.5h.01", "M14.5 7.5h.01", stroke = 1.75f)
    }
    val Fingerprint by lazy {
        icon("fingerprint", "M6.5 18.5c.7-1.6 1-3.4 1-5.5a4.5 4.5 0 0 1 9 0c0 1.2-.1 2.4-.3 3.5",
            "M12 13c0 3-.7 5.7-2 8", "M4.5 15c.3-.7.5-1.3.5-2a7 7 0 0 1 11.7-5.2", "M19 10.5c.3.8.5 1.6.5 2.5 0 2.5-.3 4.8-1 7", "M15 21c.3-.8.6-1.7.8-2.6")
    }
    val Undo by lazy { icon("undo", "M9 14L4 9l5-5", "M4 9h10.5a5.5 5.5 0 0 1 0 11H11") }
    val Pin by lazy { icon("pin", "M9 4h6l-1 6 3 3v1.5H7V13l3-3z", "M12 14.5V20") }
    val Qr by lazy {
        icon("qr", "M4 4h6v6H4z", "M14 4h6v6h-6z", "M4 14h6v6H4z", "M14 14h2.5v2.5H14z", "M18 18h2v2h-2z", "M18 14h2", "M14 19.5v.5")
    }
    val Stop by lazy { icon("stop", "M6.5 6.5l11 11", "M17.5 6.5l-11 11", stroke = 2f) }
}
