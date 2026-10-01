package app.pane.browser.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colours. Pane is a white ground and quiet greys: one ink, a muted grey for everything
 * secondary, a faint grey for things that should barely be there, a hairline, and two soft washes
 * for a selected or pressed row. Nothing is tinted except the accent, a clear blue (the one used
 * for primary buttons, switches and the cursor). An amber means exactly one thing: this connection
 * is not secure. A destructive action is a restrained red.
 *
 * Components use roles ("label", "separator") rather than raw values so light, dark and private
 * themes stay consistent.
 */
@Immutable
data class PaneColors(
    val isDark: Boolean,
    val background: Color,
    val groupedBackground: Color,
    /** A control's resting fill (a quiet field or track): barely off the ground. */
    val surface: Color,
    val elevatedSurface: Color,
    /** The wash behind a selected or live row, and under disabled controls. */
    val fill: Color,
    val secondaryFill: Color,
    val label: Color,
    /** Muted grey: every secondary line of text. */
    val secondaryLabel: Color,
    /** Between muted and faint: placeholders, chevrons, timestamps. */
    val tertiaryLabel: Color,
    /** The faintest grey that is still drawn: outlines of idle controls, the unselected dot. */
    val faint: Color,
    val separator: Color,
    val accent: Color,
    val onAccent: Color,
    val destructive: Color,
    /** No green in Pane: "positive" is just ink. A secure padlock stays quiet grey. */
    val positive: Color,
    /** Amber, for "this connection is not secure" and nothing else. */
    val warning: Color,
    /** The solid fill of things that float over the page: the address pill, menus, sheets. */
    val floating: Color,
    /** The hairline between list rows and under headers (floating surfaces have no outline). */
    val hairline: Color,
    /** Same amber as [warning]; kept as a name for the little status dot beside an unsafe address. */
    val signal: Color,
    val scrim: Color,
)

// Light: a very light grey ground (0.957) under white raised surfaces, ink 0.09, muted 0.55, faint 0.83.
private val Paper = Color(0xFFFFFFFF)
private val GroundLight = Color(0xFFF3F3F4)
private val InkLight = Color(0xFF171717)
private val MutedLight = Color(0xFF8C8C8C)
private val SoftLight = Color(0xFFB3B3B3)
private val FaintLight = Color(0xFFD4D4D4)
private val HairlineLight = Color(0xFFE9E9EB)
private val WashLight = Color(0xFFE8E8EA)
private val HoverLight = Color(0xFFECECEE)
private val AmberLight = Color(0xFFB5540A)
private val RedLight = Color(0xFFB3261E)
private val BlueLight = Color(0xFF0285FF)

// Dark: a very dark grey ground (0.075) under slightly lighter raised surfaces, ink 0.93.
private val GroundDark = Color(0xFF131313)
private val RaisedDark = Color(0xFF232323)
private val InkDark = Color(0xFFEDEDED)
private val MutedDark = Color(0xFF949494)
private val SoftDark = Color(0xFF6B6B6B)
private val FaintDark = Color(0xFF525252)
private val HairlineDark = Color(0xFF2F2F2F)
private val WashDark = Color(0xFF2B2B2B)
private val HoverDark = Color(0xFF1B1B1B)
private val AmberDark = Color(0xFFFABF24)
private val RedDark = Color(0xFFF2766E)
private val BlueDark = Color(0xFF2A8BF2)

val LightColors = PaneColors(
    isDark = false,
    background = GroundLight,
    groupedBackground = GroundLight,
    surface = HoverLight,
    elevatedSurface = Paper,
    fill = WashLight,
    secondaryFill = HoverLight,
    label = InkLight,
    secondaryLabel = MutedLight,
    tertiaryLabel = SoftLight,
    faint = FaintLight,
    separator = HairlineLight,
    accent = BlueLight,
    onAccent = Paper,
    destructive = RedLight,
    positive = InkLight,
    warning = AmberLight,
    floating = Paper,
    hairline = HairlineLight,
    signal = AmberLight,
    scrim = Color.Black.copy(alpha = 0.30f),
)

val DarkColors = PaneColors(
    isDark = true,
    background = GroundDark,
    groupedBackground = GroundDark,
    surface = HoverDark,
    elevatedSurface = RaisedDark,
    fill = WashDark,
    secondaryFill = HoverDark,
    label = InkDark,
    secondaryLabel = MutedDark,
    tertiaryLabel = SoftDark,
    faint = FaintDark,
    separator = HairlineDark,
    accent = BlueDark,
    onAccent = Color.White,
    destructive = RedDark,
    positive = InkDark,
    warning = AmberDark,
    floating = RaisedDark,
    hairline = HairlineDark,
    signal = AmberDark,
    scrim = Color.Black.copy(alpha = 0.55f),
)

/** Private browsing: the same greys on a deeper ground, so it reads as "the dark side" at a glance. */
val PrivateColors = DarkColors.copy(
    background = Color(0xFF0C0C0C),
    groupedBackground = Color(0xFF0C0C0C),
    surface = Color(0xFF141414),
    elevatedSurface = Color(0xFF1C1C1C),
    fill = Color(0xFF232323),
    secondaryFill = Color(0xFF141414),
    floating = Color(0xFF1C1C1C),
    hairline = Color(0xFF282828),
    separator = Color(0xFF282828),
)

/**
 * The colours for cards and buttons set on a frosted panel. A dark panel is lighter than the ground
 * these were drawn for, so a fixed grey would sink into it: they lift by a wash of white instead.
 */
fun PaneColors.onFrost(): PaneColors {
    if (!isDark) return this
    val lift = Color.White.copy(alpha = 0.08f)
    return copy(elevatedSurface = lift, floating = lift)
}

val LocalPaneColors = staticCompositionLocalOf { LightColors }
