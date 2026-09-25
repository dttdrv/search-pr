package app.pane.browser.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** The iOS Dynamic Type scale at its default size. */
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
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

val DefaultTypography = PaneTypography(
    largeTitle = base.copy(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.012).em),
    title1 = base.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em),
    title2 = base.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.006).em),
    title3 = base.copy(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.005).em),
    headline = base.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.003).em),
    body = base.copy(fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.003).em),
    callout = base.copy(fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.002).em),
    subheadline = base.copy(fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.001).em),
    footnote = base.copy(fontSize = 13.sp, lineHeight = 18.sp),
    caption = base.copy(fontSize = 12.sp, lineHeight = 16.sp),
    caption2 = base.copy(fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.005.em),
)

val LocalPaneTypography = staticCompositionLocalOf { DefaultTypography }
