package app.pane.browser.ui.prompts

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.components.TextButton
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.ContinuousRoundedShape
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.rememberHaptics

/** The text field inside an iOS alert: 36pt, hairline border, no floating label. */
@Composable
internal fun AlertTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    requester: FocusRequester? = null,
) {
    val colors = PaneTheme.colors
    val shape = ContinuousRoundedShape(8.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(shape)
            .background(if (colors.isDark) colors.fill else colors.surface)
            .border(0.5.dp, colors.separator, shape)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = PaneTheme.type.subheadline, color = colors.tertiaryLabel, maxLines = 1)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = PaneTheme.type.subheadline.copy(color = colors.label),
            cursorBrush = SolidColor(colors.accent),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = if (password) KeyboardType.Password else keyboardType,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onDone = { onImeAction() },
                onGo = { onImeAction() },
                onNext = { onImeAction() },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (requester != null) Modifier.focusRequester(requester) else Modifier),
        )
    }
}

/** A round checkbox, as in iOS edit mode: an empty ring, or a filled accent disc with a tick. */
@Composable
internal fun CheckCircle(checked: Boolean, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val colors = PaneTheme.colors
    val fill by animateColorAsState(if (checked) colors.accent else Color.Transparent, Motion.fade(150), label = "check")
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .then(if (checked) Modifier else Modifier.border(1.5.dp, colors.tertiaryLabel, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(PaneIcons.Check, null, tint = colors.onAccent, modifier = Modifier.size(size * 0.62f))
    }
}

/** "Don't allow more dialogs from this page", shown inside alerts from a page that keeps opening them. */
@Composable
internal fun OptOutRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val haptics = rememberHaptics()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ContinuousRoundedShape(8.dp))
            .clickable(remember { MutableInteractionSource() }, indication = null) {
                haptics.toggle(!checked)
                onCheckedChange(!checked)
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CheckCircle(checked, size = 20.dp)
        Text(
            "Don’t allow more dialogs from this page",
            style = PaneTheme.type.footnote,
            color = PaneTheme.colors.label,
        )
    }
}

/** A larger version of the settings icon tile, used as the hero of permission sheets. */
@Composable
internal fun HeroTile(icon: ImageVector, background: Color, modifier: Modifier = Modifier, size: Dp = 60.dp) {
    Box(
        modifier
            .size(size)
            .clip(ContinuousRoundedShape(size * 0.24f))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

/** Sheet title bar with optional leading and trailing text buttons ("Clear" … "Done"). */
@Composable
internal fun SheetBar(
    title: String,
    leading: String? = null,
    onLeading: (() -> Unit)? = null,
    trailing: String? = null,
    onTrailing: (() -> Unit)? = null,
) {
    Box(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 8.dp)) {
        Text(
            title,
            style = PaneTheme.type.headline,
            color = PaneTheme.colors.label,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 72.dp),
        )
        if (leading != null && onLeading != null) {
            TextButton(leading, onClick = onLeading, modifier = Modifier.align(Alignment.CenterStart))
        }
        if (trailing != null && onTrailing != null) {
            TextButton(trailing, onClick = onTrailing, bold = true, modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}

/** Shape of one row in an inset-grouped list, so separate lazy items read as one card. */
internal fun groupedRowShape(first: Boolean, last: Boolean): Shape {
    val r = CornerSize(12.dp)
    val none = CornerSize(0.dp)
    return when {
        first && last -> PaneShapes.medium
        first -> ContinuousRoundedShape(r, r, none, none)
        last -> ContinuousRoundedShape(none, none, r, r)
        else -> RectangleShape
    }
}

/** iOS system colours for permission tiles. */
internal object TileColors {
    val blue = Color(0xFF0A84FF)
    val red = Color(0xFFFF3B30)
    val orange = Color(0xFFFF9500)
    val green = Color(0xFF34C759)
    val indigo = Color(0xFF5856D6)
    val purple = Color(0xFFAF52DE)
    val teal = Color(0xFF30B0C7)
    val gray = Color(0xFF8E8E93)
}
