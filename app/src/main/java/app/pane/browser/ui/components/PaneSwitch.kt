package app.pane.browser.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.rememberHaptics

/** Material 3's switch as Android's own Settings draws it, a tick or a cross in the handle, in Pane's greys and accent. */
@Composable
fun PaneSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = PaneTheme.colors
    val haptics = rememberHaptics()
    // every row holding a switch toggles on tap, so the row is the touch target and the switch stays 52×32
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Switch(
            checked = checked,
            onCheckedChange = {
                haptics.toggle(it)
                onCheckedChange(it)
            },
            modifier = modifier,
            enabled = enabled,
            thumbContent = {
                Icon(if (checked) PaneIcons.Check else PaneIcons.Close, null, Modifier.size(SwitchDefaults.IconSize))
            },
            colors = SwitchDefaults.colors(
                checkedIconColor = colors.accent,
                uncheckedThumbColor = colors.secondaryLabel,
                uncheckedTrackColor = colors.fill,
                uncheckedBorderColor = colors.secondaryLabel,
                uncheckedIconColor = colors.fill,
            ),
        )
    }
}
