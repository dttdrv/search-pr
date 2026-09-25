package app.pane.browser.ui.prompts

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.pane.browser.engine.prompts.DateTimeRequest
import app.pane.browser.ui.components.PaneSheet
import app.pane.browser.ui.theme.ContinuousRoundedShape
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.rememberHaptics
import app.pane.core.prompts.DateTimeKind
import app.pane.core.prompts.DateTimeValues
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.text.DateFormatSymbols
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.IsoFields
import java.util.Locale
import kotlin.math.abs
import java.time.format.TextStyle as JavaTextStyle

private val RowHeight = 34.dp
private const val VISIBLE_ROWS = 5

/**
 * Date and time inputs as iOS-style wheels. The value is clamped to the input's min/max as it
 * changes, so an out-of-range choice visibly springs back instead of failing silently.
 */
@Composable
internal fun DateTimeSheet(request: DateTimeRequest, visible: Boolean, onDone: () -> Unit) {
    val kind = request.kind
    val colors = PaneTheme.colors
    val locale = remember { Locale.getDefault() }
    val min = remember { DateTimeValues.parse(kind, request.minValue) }
    val max = remember { DateTimeValues.parse(kind, request.maxValue) }
    var value by remember {
        mutableStateOf(DateTimeValues.initial(kind, request.defaultValue, request.minValue, request.maxValue, LocalDateTime.now()))
    }
    val years = remember {
        val base = value.year
        minOf(min?.year ?: (base - 100), base)..maxOf(max?.year ?: (base + 100), base)
    }

    fun update(next: LocalDateTime) {
        value = DateTimeValues.clamp(DateTimeValues.normalize(kind, next), min, max)
    }

    PaneSheet(visible = visible, onDismiss = {
        request.dismiss()
        onDone()
    }) {
        SheetBar(
            title = titleFor(kind),
            leading = "Clear",
            onLeading = {
                request.clear()
                onDone()
            },
            trailing = "Done",
            onTrailing = {
                request.pick(DateTimeValues.format(kind, value))
                onDone()
            },
        )
        Text(
            summary(kind, value, locale),
            style = PaneTheme.type.subheadline,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        val date = value.toLocalDate()
        val time = value.toLocalTime()
        when (kind) {
            DateTimeKind.Date -> WheelGroup { DateWheels(date, years, showDay = true) { update(it.atStartOfDay()) } }
            DateTimeKind.Month -> WheelGroup { DateWheels(date, years, showDay = false) { update(it.atStartOfDay()) } }
            DateTimeKind.Week -> WheelGroup { WeekWheels(date, years) { update(it.atStartOfDay()) } }
            DateTimeKind.Time -> WheelGroup { TimeWheels(time) { update(it.atDate(DateTimeValues.TIME_BASE_DATE)) } }
            DateTimeKind.DateTimeLocal -> {
                WheelGroup { DateWheels(date, years, showDay = true) { update(it.atTime(time)) } }
                Spacer(Modifier.height(8.dp))
                WheelGroup { TimeWheels(time) { update(date.atTime(it)) } }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** Keeps a wheel spun past its end from dragging the whole sheet down. */
private val KeepScrollInWheels = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

/** Wheels side by side over one shared selection band, like UIDatePicker. */
@Composable
private fun WheelGroup(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(RowHeight * VISIBLE_ROWS)
            .nestedScroll(KeepScrollInWheels),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(RowHeight)
                .clip(ContinuousRoundedShape(8.dp))
                .background(PaneTheme.colors.fill),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

@Composable
private fun DateWheels(date: LocalDate, years: IntRange, showDay: Boolean, onChange: (LocalDate) -> Unit) {
    val context = LocalContext.current
    val locale = remember { Locale.getDefault() }
    val order = remember {
        val system = DateFormat.getDateFormatOrder(context).toList().filter { it == 'd' || it == 'M' || it == 'y' }.distinct()
        system + listOf('M', 'd', 'y').filterNot { it in system }
    }
    val months = remember(locale) { (1..12).map { Month.of(it).getDisplayName(JavaTextStyle.FULL, locale) } }
    Row(Modifier.fillMaxWidth()) {
        order.filter { showDay || it != 'd' }.forEach { part ->
            when (part) {
                'M' -> WheelPicker(
                    count = 12,
                    selectedIndex = date.monthValue - 1,
                    onSelect = { onChange(date.withMonth(it + 1)) },
                    modifier = Modifier.weight(1.5f),
                ) { months[it] }
                'd' -> WheelPicker(
                    count = date.lengthOfMonth(),
                    selectedIndex = date.dayOfMonth - 1,
                    onSelect = { onChange(date.withDayOfMonth(it + 1)) },
                    modifier = Modifier.weight(0.7f),
                ) { (it + 1).toString() }
                else -> WheelPicker(
                    count = years.last - years.first + 1,
                    selectedIndex = date.year - years.first,
                    onSelect = { onChange(date.withYear(years.first + it)) },
                    modifier = Modifier.weight(1f),
                ) { (years.first + it).toString() }
            }
        }
    }
}

@Composable
private fun WeekWheels(date: LocalDate, years: IntRange, onChange: (LocalDate) -> Unit) {
    val year = date.get(IsoFields.WEEK_BASED_YEAR)
    val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    Row(Modifier.fillMaxWidth()) {
        WheelPicker(
            count = DateTimeValues.weeksInYear(year),
            selectedIndex = week - 1,
            onSelect = { onChange(DateTimeValues.weekStart(year, it + 1)) },
            modifier = Modifier.weight(1.3f),
        ) { "Week ${it + 1}" }
        WheelPicker(
            count = years.last - years.first + 1,
            selectedIndex = year - years.first,
            onSelect = { onChange(DateTimeValues.weekStart(years.first + it, week)) },
            modifier = Modifier.weight(1f),
        ) { (years.first + it).toString() }
    }
}

@Composable
private fun TimeWheels(time: LocalTime, onChange: (LocalTime) -> Unit) {
    val context = LocalContext.current
    val is24Hour = remember { DateFormat.is24HourFormat(context) }
    val amPm = remember { DateFormatSymbols.getInstance().amPmStrings.toList() }
    val pm = time.hour >= 12
    Row(Modifier.fillMaxWidth()) {
        if (is24Hour) {
            WheelPicker(24, time.hour, { onChange(time.withHour(it)) }, Modifier.weight(1f)) { two(it) }
        } else {
            WheelPicker(12, time.hour % 12, { onChange(time.withHour(it + if (pm) 12 else 0)) }, Modifier.weight(1f)) {
                if (it == 0) "12" else it.toString()
            }
        }
        WheelPicker(60, time.minute, { onChange(time.withMinute(it)) }, Modifier.weight(1f)) { two(it) }
        if (!is24Hour) {
            WheelPicker(2, if (pm) 1 else 0, { onChange(time.withHour(time.hour % 12 + if (it == 1) 12 else 0)) }, Modifier.weight(1f)) {
                amPm.getOrNull(it) ?: if (it == 0) "AM" else "PM"
            }
        }
    }
}

/**
 * One wheel: snaps to rows, ticks as values pass the band, and tilts rows away from the centre
 * like a drum. [selectedIndex] is the source of truth; when it changes from outside (a day that
 * no longer exists, a clamp to min/max) the wheel glides to it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelPicker(
    count: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: (Int) -> String,
) {
    val colors = PaneTheme.colors
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val selected = selectedIndex.coerceIn(0, (count - 1).coerceAtLeast(0))
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected)
    val fling = rememberSnapFlingBehavior(state)
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)

    LaunchedEffect(state) {
        state.centerOn(currentSelected, animate = false)
        snapshotFlow { state.centeredIndex() }
            .distinctUntilChanged()
            .collect { index ->
                if (index != currentSelected) {
                    if (state.isScrollInProgress) haptics.tick()
                    currentOnSelect(index)
                }
            }
    }
    LaunchedEffect(selected, count) {
        if (!state.isScrollInProgress && state.centeredIndex() != selected) state.centerOn(selected, animate = true)
    }

    LazyColumn(
        state = state,
        flingBehavior = fling,
        contentPadding = PaddingValues(vertical = RowHeight * (VISIBLE_ROWS / 2)),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.height(RowHeight * VISIBLE_ROWS),
    ) {
        items(count) { index ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(RowHeight)
                    .graphicsLayer {
                        val info = state.layoutInfo
                        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                        if (item != null && item.size > 0) {
                            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                            val rows = (item.offset + item.size / 2f - center) / item.size
                            val distance = abs(rows)
                            alpha = (1f - distance * 0.3f).coerceIn(0.15f, 1f)
                            rotationX = (-rows * 18f).coerceIn(-72f, 72f)
                            val scale = 1f - (distance * 0.04f).coerceAtMost(0.12f)
                            scaleX = scale
                            scaleY = scale
                            cameraDistance = 12f * density
                        }
                    }
                    .clickable(remember { MutableInteractionSource() }, indication = null) {
                        scope.launch { state.centerOn(index, animate = true) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(index),
                    style = PaneTheme.type.title3.copy(fontWeight = FontWeight.Normal),
                    color = colors.label,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Index of the row nearest the middle of the wheel. */
private fun LazyListState.centeredIndex(): Int {
    val info = layoutInfo
    val items = info.visibleItemsInfo
    if (items.isEmpty()) return firstVisibleItemIndex
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return items.minBy { abs(it.offset + it.size / 2 - center) }.index
}

/** Scrolls so row [index] sits in the middle, measured from the actual layout. */
private suspend fun LazyListState.centerOn(index: Int, animate: Boolean) {
    fun delta(): Float? {
        val info = layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return null
        val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
        return item.offset + item.size / 2f - center
    }
    if (delta() == null) scrollToItem(index)
    val d = delta() ?: return
    if (d == 0f) return
    if (animate) animateScrollBy(d) else scrollBy(d)
}

private fun two(n: Int) = n.toString().padStart(2, '0')

private fun titleFor(kind: DateTimeKind) = when (kind) {
    DateTimeKind.Date -> "Date"
    DateTimeKind.Month -> "Month"
    DateTimeKind.Week -> "Week"
    DateTimeKind.Time -> "Time"
    DateTimeKind.DateTimeLocal -> "Date & Time"
}

private fun summary(kind: DateTimeKind, value: LocalDateTime, locale: Locale): String = try {
    when (kind) {
        DateTimeKind.Date -> value.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
        DateTimeKind.Month -> value.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
        DateTimeKind.Week -> {
            val start = value.toLocalDate()
            val days = DateTimeFormatter.ofPattern("d MMM", locale)
            "Week ${start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}, ${start.get(IsoFields.WEEK_BASED_YEAR)} · " +
                "${start.format(days)} – ${start.plusDays(6).format(days)}"
        }
        DateTimeKind.Time -> value.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
        DateTimeKind.DateTimeLocal -> value.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale))
    }
} catch (_: RuntimeException) {
    DateTimeValues.format(kind, value)
}
