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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.floating
import app.pane.browser.ui.theme.frosted

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

/** Plain text button, with an optional glyph in front. Prefer [ChromeButton] where a glyph alone says it. */
@Composable
fun TextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bold: Boolean = false,
    color: Color = PaneTheme.colors.label,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
            .pressDim(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Box(Modifier.size(8.dp))
        }
        Text(text, style = if (bold) PaneTheme.type.headline else PaneTheme.type.body, color = color)
    }
}

enum class ButtonStyle { Filled, Tinted, Plain, Destructive }

/** Full-width pill action used in sheets and onboarding. Filled is the one accent-blue pill. */
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
        ButtonStyle.Destructive -> colors.destructive to Color.White
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight)
            .pressScale(enabled = enabled, pressedScale = 0.98f, haptic = true, onClick = onClick)
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

/**
 * A secondary action under (or beside) the primary one: no fill, no outline, just a glyph and a few
 * words in muted ink. It stays quiet so the one real choice reads first.
 */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = PaneTheme.colors
    val tint = if (enabled) colors.secondaryLabel else colors.tertiaryLabel
    Row(
        modifier
            .height(ButtonHeight)
            .pressDim(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Box(Modifier.size(8.dp))
        }
        Text(text, style = PaneTheme.type.headline, color = tint, maxLines = 1)
    }
}

/**
 * A pill that is only a glyph, for actions everyone already reads (next, done, close). [style]
 * Filled is the solid accent pill; anything else is a soft wash.
 */
@Composable
fun IconPill(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Filled,
    enabled: Boolean = true,
) {
    val colors = PaneTheme.colors
    val filled = style == ButtonStyle.Filled
    val tint = when {
        !enabled -> colors.tertiaryLabel
        filled -> colors.onAccent
        else -> colors.label
    }
    Box(
        modifier
            .height(ButtonHeight)
            .semantics { this.contentDescription = contentDescription }
            .pressScale(enabled = enabled, pressedScale = 0.97f, haptic = true, onClick = onClick)
            .clip(PaneShapes.pill)
            .background(if (filled && enabled) colors.accent else colors.fill),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** The height of a full-width pill action. */
private val ButtonHeight = 48.dp

/** A round button that floats over the page, [frosted] over it. Its [content] is centred. */
@Composable
fun FloatingCircle(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 50.dp,
    enabled: Boolean = true,
    contentDescription: String? = null,
    onLongClick: (() -> Unit)? = null,
    frostAt: (() -> Offset)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)
            .pressScale(enabled = enabled, pressedScale = 0.9f, haptic = true, onLongClick = onLongClick, onClick = onClick)
            .frosted(CircleShape, position = frostAt),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** A text pill ("New", "Done") that floats over content. */
@Composable
fun FloatingTextButton(
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
                    Modifier.floating(PaneShapes.pill)
                },
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = PaneTheme.type.headline, color = if (strong) colors.onAccent else colors.label, maxLines = 1)
    }
}
