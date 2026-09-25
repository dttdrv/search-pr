package app.pane.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme

/** A 44pt-target icon button that dims when pressed, like a UIKit bar button. */
@Composable
fun ChromeButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = PaneTheme.colors.accent,
    size: Dp = 24.dp,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .semantics { this.contentDescription = contentDescription }
            .pressDim(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
    }
}

/** Plain text button ("Done", "Cancel", "Edit"). */
@Composable
fun TextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bold: Boolean = false,
    color: Color = PaneTheme.colors.accent,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
            .pressDim(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = if (bold) PaneTheme.type.headline else PaneTheme.type.body, color = color)
    }
}

enum class ButtonStyle { Filled, Tinted, Plain, Destructive }

/** Large rounded action button used in sheets and onboarding. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Filled,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = PaneTheme.colors
    val (bg, fg) = when (style) {
        ButtonStyle.Filled -> colors.accent to colors.onAccent
        ButtonStyle.Tinted -> colors.accent.copy(alpha = 0.14f) to colors.accent
        ButtonStyle.Plain -> Color.Transparent to colors.accent
        ButtonStyle.Destructive -> colors.destructive.copy(alpha = 0.14f) to colors.destructive
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .pressScale(enabled = enabled, pressedScale = 0.97f, haptic = true, onClick = onClick)
            .clip(PaneShapes.large)
            .background(if (enabled) bg else colors.fill),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            Box(Modifier.size(8.dp))
        }
        Text(text, style = PaneTheme.type.headline, color = if (enabled) fg else colors.tertiaryLabel)
    }
}
