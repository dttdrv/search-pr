package app.pane.browser.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colours modelled on iOS system colours. Components use roles ("label",
 * "groupedBackground") rather than raw values so light, dark and private themes stay consistent.
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
    /** Toolbar/chrome material: slightly translucent so the page glows through at the edges. */
    val chrome: Color,
    val chromeBorder: Color,
    val scrim: Color,
    val shadow: Color,
)

val LightColors = PaneColors(
    isDark = false,
    background = Color(0xFFFFFFFF),
    groupedBackground = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    elevatedSurface = Color(0xFFFFFFFF),
    fill = Color(0x1F787880),
    secondaryFill = Color(0x14787880),
    label = Color(0xFF000000),
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    separator = Color(0x4A3C3C43),
    accent = Color(0xFF0A7AFF),
    onAccent = Color.White,
    destructive = Color(0xFFFF3B30),
    positive = Color(0xFF34C759),
    warning = Color(0xFFFF9500),
    chrome = Color(0xF2F9F9FB),
    chromeBorder = Color(0x1A000000),
    scrim = Color(0x66000000),
    shadow = Color(0x33000000),
)

val DarkColors = PaneColors(
    isDark = true,
    background = Color(0xFF000000),
    groupedBackground = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    elevatedSurface = Color(0xFF2C2C2E),
    fill = Color(0x5C787880),
    secondaryFill = Color(0x52787880),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    separator = Color(0x99545458),
    accent = Color(0xFF3395FF),
    onAccent = Color.White,
    destructive = Color(0xFFFF453A),
    positive = Color(0xFF30D158),
    warning = Color(0xFFFF9F0A),
    chrome = Color(0xF01C1C1E),
    chromeBorder = Color(0x1FFFFFFF),
    scrim = Color(0x8C000000),
    shadow = Color(0x66000000),
)

/** Private browsing: always dark, with a violet accent so the mode is unmistakable. */
val PrivateColors = DarkColors.copy(
    background = Color(0xFF0E0B16),
    groupedBackground = Color(0xFF0E0B16),
    surface = Color(0xFF1B1726),
    elevatedSurface = Color(0xFF262136),
    accent = Color(0xFFA78BFA),
    chrome = Color(0xF01B1726),
    chromeBorder = Color(0x26A78BFA),
)

val LocalPaneColors = staticCompositionLocalOf { LightColors }
