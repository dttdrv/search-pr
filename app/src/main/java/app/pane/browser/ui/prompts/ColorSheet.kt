package app.pane.browser.ui.prompts

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.pane.browser.engine.prompts.ColorRequest
import app.pane.browser.ui.components.PaneSheet
import app.pane.browser.ui.theme.ContinuousRoundedShape
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.rememberHaptics
import app.pane.core.prompts.ColorValues

private fun colorOf(hex: String): Color = Color(ColorValues.toArgb(hex) ?: 0xFF000000.toInt())

/** `<input type=color>`: the iOS colour grid, the page's suggested colours and a hex field. */
@Composable
internal fun ColorSheet(request: ColorRequest, visible: Boolean, onDone: () -> Unit) {
    val colors = PaneTheme.colors
    val haptics = rememberHaptics()
    val focusManager = LocalFocusManager.current
    val initial = remember { ColorValues.normalize(request.defaultValue) ?: "#000000" }
    val suggestions = remember { request.suggestions.mapNotNull { ColorValues.normalize(it) }.distinct().take(ColorValues.GRID_COLUMNS) }
    var hex by remember { mutableStateOf(initial) }
    var field by remember { mutableStateOf(initial.removePrefix("#").uppercase()) }
    val preview by animateColorAsState(colorOf(hex), Motion.fade(160), label = "preview")

    fun choose(value: String) {
        if (value == hex) return
        haptics.tick()
        hex = value
        field = value.removePrefix("#").uppercase()
        focusManager.clearFocus()
    }

    PaneSheet(visible = visible, onDismiss = {
        request.dismiss()
        onDone()
    }) {
        SheetBar("Color", trailing = "Done", onTrailing = {
            request.pick(hex)
            onDone()
        })
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(ContinuousRoundedShape(14.dp))
                    .background(preview)
                    .border(0.5.dp, colors.separator, ContinuousRoundedShape(14.dp)),
            )
            Column(Modifier.weight(1f)) {
                Text("Hex Color #", style = PaneTheme.type.footnote, color = colors.secondaryLabel)
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(ContinuousRoundedShape(10.dp))
                        .background(colors.fill)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = field,
                        onValueChange = { input ->
                            val cleaned = input.filter { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }.take(6).uppercase()
                            field = cleaned
                            ColorValues.normalize(cleaned)?.let { hex = it }
                        },
                        singleLine = true,
                        textStyle = PaneTheme.type.body.copy(color = colors.label),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (suggestions.isNotEmpty()) {
            Text(
                "SUGGESTED",
                style = PaneTheme.type.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(start = 32.dp, top = 12.dp, bottom = 7.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                suggestions.forEach { value -> Swatch(value, selected = value == hex) { choose(value) } }
            }
        }
        Spacer(Modifier.height(16.dp))
        ColorGrid(selected = hex, onSelect = { choose(it) })
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Swatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    val colors = PaneTheme.colors
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.border(2.5.dp, colors.accent, CircleShape) else Modifier)
            .clickable(onClick = onClick)
            .padding(4.dp)
            .clip(CircleShape)
            .background(colorOf(hex))
            .border(0.5.dp, colors.separator, CircleShape),
    )
}

@Composable
private fun ColorGrid(selected: String, onSelect: (String) -> Unit) {
    val rows = remember { ColorValues.grid.chunked(ColorValues.GRID_COLUMNS) }
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(ContinuousRoundedShape(10.dp)),
    ) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { hex ->
                    val isSelected = hex == selected
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(colorOf(hex))
                            .clickable { onSelect(hex) }
                            .then(
                                if (isSelected) {
                                    Modifier.border(3.dp, if (ColorValues.isLight(hex)) Color.Black else Color.White, ContinuousRoundedShape(4.dp))
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
        }
    }
}
