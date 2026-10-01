package app.pane.browser.ui.prompts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.engine.prompts.ChoiceRequest
import app.pane.browser.ui.components.PaneSheet
import app.pane.browser.ui.components.SearchField
import app.pane.browser.ui.components.SectionLabel
import app.pane.browser.ui.components.Separator
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.rememberHaptics
import org.mozilla.geckoview.GeckoSession.PromptDelegate.ChoicePrompt.Choice

/** Lists longer than this get a search field (country pickers, time zones…). */
private const val SEARCH_THRESHOLD = 12

private class ChoiceGroup(val label: String?, val choices: List<Choice>)

private sealed interface ChoiceLine {
    val key: String

    class Header(override val key: String, val label: String) : ChoiceLine

    class Option(override val key: String, val choice: Choice, val first: Boolean, val last: Boolean) : ChoiceLine
}

/**
 * `<select>` as a list of flat rows with hairlines between them: single choice picks and closes,
 * multiple choice ticks circles and confirms with Done, page menus act on tap. `<optgroup>`s
 * become small-capitals section labels.
 */
@Composable
internal fun ChoiceSheet(request: ChoiceRequest, visible: Boolean, onDone: () -> Unit) {
    val prompt by request.updates.collectAsStateWithLifecycle()
    val colors = PaneTheme.colors
    val haptics = rememberHaptics()
    val groups = remember(prompt) { groupChoices(prompt.choices) }
    val optionCount = remember(groups) { groups.sumOf { it.choices.size } }
    var selected by remember { mutableStateOf(groups.flatMap { g -> g.choices.filter { it.selected }.map { it.id } }.toSet()) }
    var query by remember { mutableStateOf("") }
    val lines = remember(groups, query) { linesFor(groups, query) }
    val initialIndex = remember { lines.indexOfFirst { it is ChoiceLine.Option && it.choice.id in selected }.coerceAtLeast(0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (initialIndex - 3).coerceAtLeast(0))
    val fadePx = with(LocalDensity.current) { 28.dp.toPx() }

    val title = listOf(prompt.title, prompt.message).firstOrNull { !it.isNullOrBlank() }
        ?: when {
            request.isMenu -> "Options"
            request.isMultiple -> "Choose Options"
            else -> "Choose an Option"
        }

    fun tap(choice: Choice) {
        haptics.tick()
        when {
            request.isMultiple -> selected = if (choice.id in selected) selected - choice.id else selected + choice.id
            else -> {
                selected = setOf(choice.id)
                request.select(choice.id)
                onDone()
            }
        }
    }

    PaneSheet(visible = visible, onDismiss = {
        request.dismiss()
        onDone()
    }) {
        SheetTitle(title)
        if (optionCount > SEARCH_THRESHOLD) {
            SearchField(query, { query = it }, Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        }
        Box(Modifier.weight(1f, fill = false)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(top = 4.dp, bottom = if (request.isMultiple) 8.dp else 24.dp),
            ) {
                items(lines, key = { it.key }) { line ->
                    when (line) {
                        is ChoiceLine.Header -> SectionLabel(
                            line.label,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
                        )
                        is ChoiceLine.Option -> OptionRow(
                            line = line,
                            checked = line.choice.id in selected,
                            indicator = when {
                                request.isMenu -> Indicator.None
                                request.isMultiple -> Indicator.Circle
                                else -> Indicator.Check
                            },
                            onClick = { tap(line.choice) },
                        )
                    }
                }
                if (lines.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "No Results",
                            style = PaneTheme.type.body,
                            color = colors.secondaryLabel,
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                        )
                    }
                }
            }
            ScrollEdges(
                top = {
                    if (listState.firstVisibleItemIndex > 0) 1f else listState.firstVisibleItemScrollOffset / fadePx
                },
                bottom = {
                    val info = listState.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull()
                    when {
                        last == null -> 0f
                        last.index < info.totalItemsCount - 1 -> 1f
                        else -> (last.offset + last.size - (info.viewportEndOffset - info.afterContentPadding)) / fadePx
                    }
                },
            )
        }
        if (request.isMultiple) {
            SheetButtons(primary = "Done", onPrimary = {
                request.selectAll(selected.toList())
                onDone()
            })
        }
    }
}

private enum class Indicator { None, Check, Circle }

@Composable
private fun OptionRow(line: ChoiceLine.Option, checked: Boolean, indicator: Indicator, onClick: () -> Unit) {
    val colors = PaneTheme.colors
    val choice = line.choice
    val enabled = !choice.disabled
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .defaultMinSize(minHeight = 52.dp)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (indicator == Indicator.Circle) CheckCircle(checked)
            Text(
                choice.label?.takeIf { it.isNotBlank() } ?: "—",
                style = PaneTheme.type.body,
                color = if (enabled) colors.label else colors.tertiaryLabel,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (indicator == Indicator.Check) {
                Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                    if (checked) Icon(PaneIcons.Check, null, tint = colors.label, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (!line.last) Separator(Modifier.padding(start = if (indicator == Indicator.Circle) 60.dp else 24.dp, end = 24.dp))
    }
}

/** Top-level options fall into runs split by separators; each `<optgroup>` is its own group. */
private fun groupChoices(choices: Array<out Choice>?): List<ChoiceGroup> {
    val groups = mutableListOf<ChoiceGroup>()
    var run = mutableListOf<Choice>()
    fun closeRun() {
        if (run.isNotEmpty()) groups += ChoiceGroup(null, run)
        run = mutableListOf()
    }
    for (choice in choices.orEmpty()) {
        val children = choice.items
        when {
            choice.separator -> closeRun()
            children != null -> {
                closeRun()
                val leaves = mutableListOf<Choice>()
                collectLeaves(children, leaves)
                if (leaves.isNotEmpty()) groups += ChoiceGroup(choice.label, leaves)
            }
            else -> run.add(choice)
        }
    }
    closeRun()
    return groups
}

private fun collectLeaves(items: Array<out Choice>, into: MutableList<Choice>) {
    for (item in items) {
        val children = item.items
        when {
            children != null -> collectLeaves(children, into)
            !item.separator -> into += item
        }
    }
}

private fun linesFor(groups: List<ChoiceGroup>, query: String): List<ChoiceLine> {
    val q = query.trim()
    val lines = mutableListOf<ChoiceLine>()
    var index = 0
    groups.forEachIndexed { g, group ->
        val visible = if (q.isEmpty()) group.choices else group.choices.filter { it.label.orEmpty().contains(q, ignoreCase = true) }
        if (visible.isEmpty()) return@forEachIndexed
        if (!group.label.isNullOrBlank()) lines += ChoiceLine.Header("h$g", group.label)
        visible.forEachIndexed { i, choice ->
            lines += ChoiceLine.Option("o${index++}", choice, first = i == 0, last = i == visible.lastIndex)
        }
    }
    return lines
}
