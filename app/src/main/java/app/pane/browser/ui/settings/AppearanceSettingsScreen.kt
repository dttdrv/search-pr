package app.pane.browser.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.DotText
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.library.arrive
import app.pane.browser.ui.library.rememberBackLabel
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.rememberHaptics
import app.pane.core.settings.BrowserSettings
import app.pane.core.settings.ThemeMode
import kotlin.math.abs
import kotlin.math.roundToInt

/** Page text sizes offered by the slider: 80% to 200% in 10% steps. */
private val TextScales: List<Float> = (8..20).map { it / 10f }

/**
 * Text size first, one dotted number and a slider, then theme and the two everyday switches. What
 * the toolbar and tabs do lives in Tabs & toolbar; the rest sits under Advanced. Every change
 * applies live.
 */
@Composable
fun AppearanceSettingsScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val settings by rememberSettingsState()
    val backLabel = rememberBackLabel(Route.AppearanceSettings)

    fun update(transform: (BrowserSettings) -> BrowserSettings) = container.settings.update(transform)

    LargeTitleScaffold(title = "Appearance", onBack = navigator::pop, backLabel = backLabel) {
        item(key = "text") {
            val percent = (settings.textScale * 100).roundToInt()
            GroupedSection(modifier = Modifier.arrive(0), header = "Text size") {
                row {
                    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
                        DotText("$percent%", modifier = Modifier.padding(horizontal = 20.dp))
                        Text(
                            "The quick brown fox",
                            style = PaneTheme.type.body.copy(fontSize = (17f * settings.textScale).sp, lineHeight = (23f * settings.textScale).sp),
                            color = PaneTheme.colors.label,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                        )
                        TextSizeSlider(
                            value = settings.textScale,
                            onValueChange = { scale -> update { it.copy(textScale = scale) } },
                        )
                    }
                }
            }
        }

        item(key = "theme") {
            GroupedSection(modifier = Modifier.arrive(1), header = "Theme") {
                row {
                    SegmentedRow(
                        options = listOf("Automatic", "Light", "Dark"),
                        selectedIndex = settings.theme.ordinal,
                        onSelect = { i -> update { it.copy(theme = ThemeMode.entries[i]) } },
                    )
                }
            }
        }

        item(key = "basics") {
            GroupedSection(modifier = Modifier.arrive(3)) {
                row {
                    SwitchRow(
                        title = "Reduce motion",
                        checked = settings.reduceMotion,
                        onCheckedChange = { on -> update { it.copy(reduceMotion = on) } },
                    )
                }
                row {
                    SwitchRow(
                        title = "Haptics",
                        checked = settings.haptics,
                        onCheckedChange = { on -> update { it.copy(haptics = on) } },
                    )
                }
            }
        }

        item(key = "advanced") {
            AdvancedSection(modifier = Modifier.arrive(4)) {
                GroupedSection(header = "Websites") {
                    row {
                        SwitchRow(
                            title = "Request desktop sites",
                            checked = settings.desktopModeByDefault,
                            onCheckedChange = { on -> update { it.copy(desktopModeByDefault = on) } },
                        )
                    }
                    row {
                        SwitchRow(
                            title = "Allow zoom on every site",
                            checked = settings.forceZoom,
                            onCheckedChange = { on -> update { it.copy(forceZoom = on) } },
                        )
                    }
                }
            }
        }
    }
}

/** Small "A", stepped track, large "A": drag or tap anywhere on the track. */
@Composable
private fun TextSizeSlider(value: Float, onValueChange: (Float) -> Unit) {
    val colors = PaneTheme.colors
    val haptics = rememberHaptics()
    val index = TextScales.indices.minByOrNull { abs(TextScales[it] - value) } ?: 2
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("A", style = PaneTheme.type.body, color = colors.label)
        StepTrack(
            count = TextScales.size,
            index = index,
            onIndex = { i ->
                if (i != index) {
                    haptics.tick()
                    onValueChange(TextScales[i])
                }
            },
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .semantics {
                    contentDescription = "Page text size"
                    stateDescription = "${(TextScales[index] * 100).roundToInt()}%"
                },
        )
        Text("A", style = PaneTheme.type.largeTitle, color = colors.label)
    }
}

/**
 * A thin ink track with a dot at every detent and a solid round thumb; tap or drag anywhere to pick
 * a detent. It is all drawn in one pass, and the thumb's position is read only while drawing.
 */
@Composable
private fun StepTrack(count: Int, index: Int, onIndex: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    val currentOnIndex by rememberUpdatedState(onIndex)
    val position = animateFloatAsState(index.toFloat() / (count - 1).coerceAtLeast(1), Motion.snappy(), label = "thumb")
    // The thumb's slot: it is drawn smaller, but the touch target and the travel use this width.
    val thumb = 32.dp

    Box(
        modifier
            .pointerInput(count) {
                // Measured per event, so the mapping follows size changes such as rotation.
                fun detent(x: Float) = detentAt(x - thumb.toPx() / 2, (size.width - thumb.toPx()).coerceAtLeast(1f), count)
                detectTapGestures(onTap = { offset -> currentOnIndex(detent(offset.x)) })
            }
            .pointerInput(count) {
                fun detent(x: Float) = detentAt(x - thumb.toPx() / 2, (size.width - thumb.toPx()).coerceAtLeast(1f), count)
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    currentOnIndex(detent(change.position.x))
                }
            }
            .drawBehind {
                val slot = thumb.toPx()
                val usable = (size.width - slot).coerceAtLeast(1f)
                val cy = size.height / 2f
                val line = 2.dp.toPx()
                val travelled = usable * position.value
                val left = slot / 2f
                // The track: ink up to the thumb, a faint line beyond it.
                drawRoundRect(
                    colors.tertiaryLabel,
                    topLeft = Offset(left, cy - line / 2f),
                    size = Size(usable, line),
                    cornerRadius = CornerRadius(line / 2f),
                )
                drawRoundRect(
                    colors.label,
                    topLeft = Offset(left, cy - line / 2f),
                    size = Size(travelled, line),
                    cornerRadius = CornerRadius(line / 2f),
                )
                // A dot at each detent, ink where the thumb has been and faint where it has not.
                for (i in 0 until count) {
                    val x = left + usable * i / (count - 1).coerceAtLeast(1)
                    val passed = x <= left + travelled + 0.5f
                    drawCircle(if (passed) colors.label else colors.tertiaryLabel, 2.5.dp.toPx(), Offset(x, cy))
                }
                // The thumb: a solid ink dot with a thin gap of page around it.
                val centre = Offset(left + travelled, cy)
                drawCircle(colors.background, 11.dp.toPx(), centre)
                drawCircle(colors.label, 9.dp.toPx(), centre)
            },
    )
}

private fun detentAt(x: Float, usableWidth: Float, count: Int): Int =
    ((x / usableWidth) * (count - 1)).roundToInt().coerceIn(0, count - 1)
