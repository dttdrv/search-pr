package app.pane.browser.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.ContinuousRoundedShape
import app.pane.browser.ui.theme.PaneTheme

/** UISearchBar-style field: tinted capsule with a magnifier and a clear button. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    onSubmit: (() -> Unit)? = null,
) {
    val colors = PaneTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(ContinuousRoundedShape(10.dp))
            .background(colors.fill)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PaneIcons.Search, null, tint = colors.secondaryLabel, modifier = Modifier.size(17.dp))
        Box(Modifier.weight(1f).padding(horizontal = 6.dp)) {
            if (value.isEmpty()) Text(placeholder, style = PaneTheme.type.body, color = colors.secondaryLabel, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = PaneTheme.type.body.copy(color = colors.label),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                PaneIcons.CloseCircle,
                contentDescription = "Clear",
                tint = colors.secondaryLabel,
                modifier = Modifier.size(18.dp).pressDim { onValueChange("") },
            )
        }
    }
}
