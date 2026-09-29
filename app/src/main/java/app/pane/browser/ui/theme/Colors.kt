package app.pane.browser.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colours. Pane is monochrome: ink on paper, paper on ink. The accent is the label colour
 * itself, so primary buttons are solid black (or white in dark) and nothing on screen is tinted
 * except the page. Components use roles ("label", "surface") rather than raw values so light, dark
 * and private themes stay consistent.
 */
@Immutable
data class PaneColors(
    val isDark: Boolean,
    val background: Color,
    val groupedBackground: Color,
    val surface: Color,
    val elevatedSurface: Color,
    val fill: Color,
    val secondaryFill: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val accent: Color,
    val onAccent: Color,
    val destructive: Color,
    val positive: Color,
    val warning: Color,
    /** The tint laid over blurred content on glass bars and sheets. */
    val chrome: Color,
    /** Hairline that catches the light around glass. */
    val chromeBorder: Color,
    val scrim: Color,
    val shadow: Color,
)

private val Ink = Color(0xFF0D0D0D)
private val Paper = Color(0xFFFFFFFF)

val LightColors = PaneColors(
    isDark = false,
    background = Paper,
    groupedBackground = Paper,
    surface = Color(0xFFF4F4F4),
    elevatedSurface = Paper,
    fill = Ink.copy(alpha = 0.07f),
    secondaryFill = Ink.copy(alpha = 0.04f),
    label = Ink,
    secondaryLabel = Ink.copy(alpha = 0.60f),
    tertiaryLabel = Ink.copy(alpha = 0.38f),
    separator = Ink.copy(alpha = 0.09f),
    accent = Ink,
    onAccent = Paper,
    destructive = Color(0xFFF93A37),
    positive = Color(0xFF10A37F),
    warning = Color(0xFFD97706),
    chrome = Paper.copy(alpha = 0.46f),
    chromeBorder = Ink.copy(alpha = 0.08f),
    scrim = Color.Black.copy(alpha = 0.38f),
    shadow = Color.Black.copy(alpha = 0.22f),
)

val DarkColors = PaneColors(
    isDark = true,
    background = Ink,
    groupedBackground = Ink,
    surface = Color(0xFF1B1B1B),
    elevatedSurface = Color(0xFF262626),
    fill = Paper.copy(alpha = 0.12f),
    secondaryFill = Paper.copy(alpha = 0.07f),
    label = Color(0xFFF5F5F5),
    secondaryLabel = Paper.copy(alpha = 0.62f),
    tertiaryLabel = Paper.copy(alpha = 0.38f),
    separator = Paper.copy(alpha = 0.11f),
    accent = Color(0xFFF5F5F5),
    onAccent = Ink,
    destructive = Color(0xFFFF6459),
    positive = Color(0xFF19C37D),
    warning = Color(0xFFFFB020),
    chrome = Color(0xFF161616).copy(alpha = 0.50f),
    chromeBorder = Paper.copy(alpha = 0.14f),
    scrim = Color.Black.copy(alpha = 0.62f),
    shadow = Color.Black.copy(alpha = 0.55f),
)

/** Private browsing: the darkest ink, otherwise the same monochrome. The pill says "Private". */
val PrivateColors = DarkColors.copy(
    background = Color.Black,
    groupedBackground = Color.Black,
    surface = Color(0xFF131313),
    elevatedSurface = Color(0xFF1E1E1E),
    chrome = Color(0xFF0B0B0B).copy(alpha = 0.60f),
)

val LocalPaneColors = staticCompositionLocalOf { LightColors }
