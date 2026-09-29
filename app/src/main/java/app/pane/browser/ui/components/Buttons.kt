package app.pane.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import app.pane.browser.ui.theme.GlassStrength
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.glass

/** A 44dp-target icon button that dims when pressed. Reserved for glyphs everyone already knows. */
@Composable
fun ChromeButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = PaneTheme.colors.label,
    size: Dp = 22.dp,
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

/** Plain text button ("Done", "Cancel"). */
@Composable
fun TextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bold: Boolean = false,
    color: Color = PaneTheme.colors.label,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
            .pressDim(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = if (bold) PaneTheme.type.headline else PaneTheme.type.body, color = color)
    }
}

enum class ButtonStyle { Filled, Tinted, Plain, Destructive }

/** Full-width pill action used in sheets and onboarding. Filled is solid ink (paper in dark). */
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
        ButtonStyle.Tinted -> colors.fill to colors.label
        ButtonStyle.Plain -> Color.Transparent to colors.label
        ButtonStyle.Destructive -> colors.destructive.copy(alpha = 0.12f) to colors.destructive
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .pressScale(enabled = enabled, pressedScale = 0.97f, haptic = true, onClick = onClick)
            .clip(PaneShapes.pill)
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

/** A round frosted button that floats over the page. Its [content] is centred. */
@Composable
fun GlassCircle(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 50.dp,
    enabled: Boolean = true,
    contentDescription: String? = null,
    onLongClick: (() -> Unit)? = null,
    strength: GlassStrength = GlassStrength.Regular,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)
            .pressScale(enabled = enabled, pressedScale = 0.9f, haptic = true, onLongClick = onLongClick, onClick = onClick)
            .glass(CircleShape, strength),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** A frosted text pill ("New", "Done") that floats over content. */
@Composable
fun GlassTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = PaneTheme.colors
    Box(
        modifier = modifier
            .height(46.dp)
            .pressScale(enabled = enabled, pressedScale = 0.94f, haptic = true, onClick = onClick)
            .then(
                if (strong) {
                    Modifier.clip(PaneShapes.pill).background(colors.accent)
                } else {
                    Modifier.glass(PaneShapes.pill)
                },
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = PaneTheme.type.headline, color = if (strong) colors.onAccent else colors.label, maxLines = 1)
    }
}
