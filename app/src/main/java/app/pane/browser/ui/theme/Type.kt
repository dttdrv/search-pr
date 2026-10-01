package app.pane.browser.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The type scale, set in the phone's own system font (whatever typeface the user or the maker chose
 * for Android). Small and calm: body is 15sp, secondary text is set smaller and in the muted grey
 * rather than lighter or heavier, titles are semibold with a hair of negative tracking, and emphasis
 * is medium weight, never bold. Sizes follow the system font-size setting.
 */
@Immutable
data class PaneTypography(
    val largeTitle: TextStyle,
    val title1: TextStyle,
    val title2: TextStyle,
    val title3: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val callout: TextStyle,
    val subheadline: TextStyle,
    val footnote: TextStyle,
    val caption: TextStyle,
    val caption2: TextStyle,
)

private val base = TextStyle(
    fontFamily = FontFamily.Default,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** Titles: semibold, tracked in a touch (-0.01em). */
private val titleTracking = (-0.01).em

val DefaultTypography = PaneTypography(
    largeTitle = base.copy(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = titleTracking),
    title1 = base.copy(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = titleTracking),
    title2 = base.copy(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold, letterSpacing = titleTracking),
    title3 = base.copy(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold, letterSpacing = titleTracking),
    headline = base.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
    body = base.copy(fontSize = 15.sp, lineHeight = 21.sp),
    callout = base.copy(fontSize = 15.sp, lineHeight = 20.sp),
    subheadline = base.copy(fontSize = 14.sp, lineHeight = 19.sp),
    footnote = base.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
    caption = base.copy(fontSize = 12.sp, lineHeight = 16.sp),
    caption2 = base.copy(fontSize = 11.sp, lineHeight = 14.sp),
)

val LocalPaneTypography = staticCompositionLocalOf { DefaultTypography }
