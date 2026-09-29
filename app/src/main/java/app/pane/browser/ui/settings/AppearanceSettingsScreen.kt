package app.pane.browser.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.library.arrive
import app.pane.browser.ui.library.rememberBackLabel
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.rememberHaptics
import app.pane.core.settings.BrowserSettings
import app.pane.core.settings.GlassQuality
import app.pane.core.settings.ThemeMode
import kotlin.math.abs
import kotlin.math.roundToInt

/** Page text sizes offered by the slider: 80% to 200% in 10% steps. */
private val TextScales: List<Float> = (8..20).map { it / 10f }

/**
 * Text size first, big and obvious, then theme, how much glass to draw, and the two everyday
 * switches. What the toolbar and tabs do lives in Tabs & toolbar; the rest sits under Advanced.
 * Every change applies live.
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
                    Column(Modifier.padding(top = 16.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$percent%", style = PaneTheme.type.title1, color = PaneTheme.colors.label)
                        Text(
                            "The quick brown fox",
                            style = PaneTheme.type.body.copy(fontSize = (17f * settings.textScale).sp, lineHeight = (23f * settings.textScale).sp),
                            color = PaneTheme.colors.label,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
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

        item(key = "glass") {
            GroupedSection(
                modifier = Modifier.arrive(2),
                header = "Glass effects",
                footer = "Light is easier on the battery.",
            ) {
                row {
                    SegmentedRow(
                        options = listOf("Full", "Light", "Off"),
                        selectedIndex = settings.glassQuality.ordinal,
                        onSelect = { i -> update { it.copy(glassQuality = GlassQuality.entries[i]) } },
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
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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

/** A track with [count] detents and a springy thumb; tap or drag anywhere to pick a detent. */
@Composable
private fun StepTrack(count: Int, index: Int, onIndex: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    val currentOnIndex by rememberUpdatedState(onIndex)
    val position by animateFloatAsState(index.toFloat() / (count - 1).coerceAtLeast(1), Motion.snappy(), label = "thumb")
    val thumb = 32.dp

    BoxWithConstraints(
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
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackWidth = maxWidth - thumb
        // Detent ticks under the track.
        Row(
            Modifier.padding(horizontal = thumb / 2).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(count) {
                Box(Modifier.width(1.5.dp).height(10.dp).clip(PaneShapes.pill).background(colors.tertiaryLabel))
            }
        }
        Box(
            Modifier
                .padding(horizontal = thumb / 2)
                .fillMaxWidth()
                .height(6.dp)
                .clip(PaneShapes.pill)
                .background(colors.fill),
        )
        // The filled part of the track follows the thumb; its width is read while drawing.
        Box(
            Modifier
                .padding(horizontal = thumb / 2)
                .fillMaxWidth()
                .height(6.dp)
                .drawBehind {
                    drawRoundRect(
                        color = colors.accent,
                        size = Size(size.width * position, size.height),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                },
        )
        Box(
            Modifier
                .offset { IntOffset((trackWidth * position).roundToPx(), 0) }
                .size(thumb)
                .shadow(4.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.25f))
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

private fun detentAt(x: Float, usableWidth: Float, count: Int): Int =
    ((x / usableWidth) * (count - 1)).roundToInt().coerceIn(0, count - 1)
