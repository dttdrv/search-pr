package app.pane.browser.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneTheme

/**
 * A search field: a hairline-outlined pill with no fill, a small magnifier and a clear button. The
 * outline deepens to ink while you type.
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    onSubmit: (() -> Unit)? = null,
) {
    val colors = PaneTheme.colors
    var focused by remember { mutableStateOf(false) }
    // No outline: a soft wash that deepens a touch while typing.
    val wash = animateColorAsState(if (focused) colors.fill else colors.surface, Motion.fade(), label = "searchWash")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .drawBehind { drawRoundRect(wash.value, cornerRadius = CornerRadius(size.height / 2f)) }
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PaneIcons.Magnifier, contentDescription = null, tint = colors.secondaryLabel, modifier = Modifier.size(17.dp))
        Box(Modifier.weight(1f).padding(start = 10.dp)) {
            if (value.isEmpty()) Text(placeholder, style = PaneTheme.type.body, color = colors.secondaryLabel, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = PaneTheme.type.body.copy(color = colors.label),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke() }),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.excludeFromAutofill(),
            )
        }
        if (value.isNotEmpty()) {
            // A small mark with a full-size touch target around it.
            Box(
                Modifier
                    .size(40.dp)
                    .semantics { contentDescription = "Clear" }
                    .pressDim { onValueChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(PaneIcons.Close, contentDescription = null, tint = colors.secondaryLabel, modifier = Modifier.size(16.dp))
            }
        } else {
            // Keeps the field's height and padding steady whether or not the clear button is there.
            Box(Modifier.size(12.dp))
        }
    }
}
