package app.pane.browser.ui.library

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.icons.PaneIcons

/** Broad file categories, each with the glyph and tile colour the downloads list shows. */
internal enum class FileKind(val color: Color) {
    Document(TileColors.Blue),
    Image(TileColors.Pink),
    Video(TileColors.Purple),
    Audio(TileColors.Red),
    Archive(TileColors.Brown),
    App(TileColors.Green),
    Web(TileColors.Teal),
    Other(TileColors.Gray),
    ;

    val icon: ImageVector
        get() = when (this) {
            Document -> FileGlyphs.Document
            Image -> FileGlyphs.Image
            Video -> FileGlyphs.Video
            Audio -> FileGlyphs.Audio
            Archive -> FileGlyphs.Archive
            App -> PaneIcons.Phone
            Web -> PaneIcons.Globe
            Other -> FileGlyphs.Document
        }

    companion object {
        private val archives = setOf("zip", "rar", "7z", "gz", "tgz", "bz2", "xz", "tar", "zst")
        private val apps = setOf("apk", "apks", "xapk", "aab", "xpi", "exe", "msi", "dmg", "jar")
        private val documents = setOf("pdf", "doc", "docx", "odt", "rtf", "txt", "md", "xls", "xlsx", "ods", "csv", "ppt", "pptx", "odp", "epub", "json", "xml", "ics", "vcf")

        fun of(fileName: String, mime: String?): FileKind {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            val type = mime?.substringBefore(';')?.trim()?.lowercase().orEmpty()
            return when {
                type.startsWith("image/") -> Image
                type.startsWith("video/") -> Video
                type.startsWith("audio/") -> Audio
                ext in apps || type == "application/vnd.android.package-archive" -> App
                ext in archives || type == "application/zip" -> Archive
                ext == "html" || ext == "htm" || type == "text/html" -> Web
                ext in documents || type.startsWith("text/") || type == "application/pdf" -> Document
                else -> Other
            }
        }
    }
}

/** Line glyphs in PaneIcons' style for file types it doesn't cover. */
internal object FileGlyphs {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                for (d in paths) {
                    addPath(
                        pathData = addPathNodes(d),
                        fill = null,
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 1.75f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
            .build()

    val Document by lazy {
        icon("fileDocument", "M7 3h7l5 5v12a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z", "M14 3v5h5", "M9 13h6", "M9 17h6")
    }
    val Image by lazy {
        icon(
            "fileImage",
            "M5.5 5h13A1.5 1.5 0 0 1 20 6.5v11a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 17.5v-11A1.5 1.5 0 0 1 5.5 5z",
            "M4 16l4.5-4.5 4 4 2.5-2.5L20 17",
            "M15.5 9.5h.01",
        )
    }
    val Video by lazy {
        icon("fileVideo", "M4.5 6h10A1.5 1.5 0 0 1 16 7.5v9a1.5 1.5 0 0 1-1.5 1.5h-10A1.5 1.5 0 0 1 3 16.5v-9A1.5 1.5 0 0 1 4.5 6z", "M16 10.5l5-3v9l-5-3")
    }
    val Audio by lazy {
        icon("fileAudio", "M9 18V6l10-2v12", "M6.5 20.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z", "M16.5 18.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z")
    }
    val Archive by lazy {
        icon("fileArchive", "M4 8h16v11a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1z", "M3 4h18v4H3z", "M10 12h4")
    }
}
