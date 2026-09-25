package app.pane.browser.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import app.pane.core.settings.ThemeMode

object PaneTheme {
    val colors: PaneColors
        @Composable @ReadOnlyComposable get() = LocalPaneColors.current
    val type: PaneTypography
        @Composable @ReadOnlyComposable get() = LocalPaneTypography.current
}

@Composable
fun PaneTheme(
    mode: ThemeMode = ThemeMode.System,
    private: Boolean = false,
    hapticsEnabled: Boolean = true,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = when {
        private -> PrivateColors
        dark -> DarkColors
        else -> LightColors
    }
    val material = remember(colors) {
        val base = if (colors.isDark) darkColorScheme() else lightColorScheme()
        base.copy(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.groupedBackground,
            surface = colors.surface,
            onSurface = colors.label,
            onBackground = colors.label,
            surfaceVariant = colors.elevatedSurface,
            onSurfaceVariant = colors.secondaryLabel,
            outline = colors.separator,
            error = colors.destructive,
        )
    }
    MaterialTheme(colorScheme = material) {
        CompositionLocalProvider(
            LocalPaneColors provides colors,
            LocalPaneTypography provides DefaultTypography,
            LocalHapticsEnabled provides hapticsEnabled,
            LocalReduceMotion provides reduceMotion,
            LocalContentColor provides colors.label,
            // iOS highlights instead of rippling; a soft, bounded ripple is the closest native equivalent.
            LocalIndication provides ripple(color = colors.label.copy(alpha = 0.12f)),
            LocalTextSelectionColors provides TextSelectionColors(colors.accent, colors.accent.copy(alpha = 0.3f)),
            content = content,
        )
    }
}
