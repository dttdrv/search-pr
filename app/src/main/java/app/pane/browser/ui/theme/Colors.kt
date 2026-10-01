package app.pane.browser.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colours. Pane is black and white, after Nothing's design language: true black and
 * white, flat grey surfaces, hairlines instead of boxes, and one red used only as a signal (a
 * status dot, a destructive action). The accent is the label colour itself, so a primary button is
 * solid ink. Components use roles ("label", "surface") rather than raw values so light, dark and
 * private themes stay consistent.
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
    /** The solid fill of things that float over the page: the address pill, menus, sheets. */
    val floating: Color,
    /** The one-pixel line around floating surfaces. */
    val hairline: Color,
    /** Nothing's red. A status dot or a destructive action, never decoration. */
    val signal: Color,
    val scrim: Color,
    val shadow: Color,
)

private val Ink = Color(0xFF000000)
private val Paper = Color(0xFFFFFFFF)
private val Signal = Color(0xFFD71921)

val LightColors = PaneColors(
    isDark = false,
    background = Paper,
    groupedBackground = Paper,
    surface = Color(0xFFF2F2F2),
    elevatedSurface = Paper,
    fill = Ink.copy(alpha = 0.06f),
    secondaryFill = Ink.copy(alpha = 0.04f),
    label = Ink,
    secondaryLabel = Ink.copy(alpha = 0.56f),
    tertiaryLabel = Ink.copy(alpha = 0.36f),
    separator = Ink.copy(alpha = 0.12f),
    accent = Ink,
    onAccent = Paper,
    destructive = Signal,
    positive = Ink,
    warning = Signal,
    floating = Paper,
    hairline = Ink.copy(alpha = 0.14f),
    signal = Signal,
    scrim = Color.Black.copy(alpha = 0.40f),
    shadow = Color.Black.copy(alpha = 0.16f),
)

val DarkColors = PaneColors(
    isDark = true,
    background = Ink,
    groupedBackground = Ink,
    surface = Color(0xFF101010),
    elevatedSurface = Color(0xFF1A1A1A),
    fill = Paper.copy(alpha = 0.10f),
    secondaryFill = Paper.copy(alpha = 0.06f),
    label = Paper,
    secondaryLabel = Paper.copy(alpha = 0.58f),
    tertiaryLabel = Paper.copy(alpha = 0.36f),
    separator = Paper.copy(alpha = 0.16f),
    accent = Paper,
    onAccent = Ink,
    destructive = Color(0xFFFF4B52),
    positive = Paper,
    warning = Color(0xFFFF4B52),
    floating = Color(0xFF141414),
    hairline = Paper.copy(alpha = 0.18f),
    signal = Color(0xFFFF4B52),
    scrim = Color.Black.copy(alpha = 0.66f),
    shadow = Color.Black.copy(alpha = 0.60f),
)

/** Private browsing: the darkest ink, otherwise the same monochrome. The pill says "Private". */
val PrivateColors = DarkColors.copy(
    surface = Color(0xFF0C0C0C),
    elevatedSurface = Color(0xFF161616),
    floating = Color(0xFF0F0F0F),
)

val LocalPaneColors = staticCompositionLocalOf { LightColors }
