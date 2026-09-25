package app.pane.browser.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.theme.ContinuousRoundedShape
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.rememberHaptics

/** UISegmentedControl: a sliding thumb behind equal-width labels. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PaneTheme.colors
    val haptics = rememberHaptics()
    val outer = ContinuousRoundedShape(9.dp)
    val inner = ContinuousRoundedShape(7.dp)
    BoxWithConstraints(
        modifier = modifier
            .height(32.dp)
            .clip(outer)
            .background(colors.fill)
            .padding(2.dp),
    ) {
        val segment = maxWidth / options.size.coerceAtLeast(1)
        val thumbX by animateDpAsState(segment * selectedIndex, Motion.spring(0.35f, 0.82f), label = "segment")
        Box(
            Modifier
                .offset { IntOffset(thumbX.roundToPx(), 0) }
                .width(segment)
                .fillMaxHeight()
                .shadow(2.dp, inner)
                .clip(inner)
                .background(if (colors.isDark) colors.elevatedSurface.copy(alpha = 1f) else colors.surface),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            options.forEachIndexed { index, label ->
                Box(
                    Modifier
                        .width(segment)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                        ) {
                            if (index != selectedIndex) {
                                haptics.tick()
                                onSelect(index)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = PaneTheme.type.footnote.copy(
                            fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = colors.label,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
