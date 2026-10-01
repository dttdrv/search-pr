package app.pane.browser.ui.settings

import android.text.format.DateFormat
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.components.RowMargin
import app.pane.browser.ui.components.excludeFromAutofill
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.library.arrive
import app.pane.browser.ui.library.rememberBackLabel
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.ContinuousRoundedShape
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.floating
import app.pane.core.settings.BrowserSettings
import app.pane.core.settings.StartFavorites
import app.pane.core.settings.StartTitle
import java.util.Date

private enum class StartSheet { Title, Count, Source }

private val TitleChoices = listOf(
    StartTitle.Name to "Name",
    StartTitle.Custom to "Custom",
    StartTitle.Clock to "Clock",
    StartTitle.None to "None",
)

private val SourceChoices = listOf(
    StartFavorites.Frequent to "Most visited",
    StartFavorites.Bookmarks to "Bookmarks",
)

/**
 * What the start page shows: a live miniature of it, then the few switches and choices behind it.
 * Every change applies at once, to the miniature and to the page itself.
 */
@Composable
fun StartSettingsScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val settings by rememberSettingsState()
    val backLabel = rememberBackLabel(Route.StartSettings)
    var sheet by remember { mutableStateOf<StartSheet?>(null) }
    var customText by rememberSaveable { mutableStateOf(settings.startTitleText) }

    fun update(transform: (BrowserSettings) -> BrowserSettings) = container.settings.update(transform)

    Box(Modifier.fillMaxSize()) {
        LargeTitleScaffold(title = "Start page", onBack = navigator::pop, backLabel = backLabel) {
            item(key = "preview") {
                Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp).arrive(0), contentAlignment = Alignment.Center) {
                    StartPreview(settings)
                }
            }

            item(key = "page") {
                GroupedSection(modifier = Modifier.arrive(1)) {
                    row {
                        NavRow(
                            title = "Title",
                            value = TitleChoices.first { it.first == settings.startTitle }.second,
                            onClick = { sheet = StartSheet.Title },
                        )
                    }
                    if (settings.startTitle == StartTitle.Custom) {
                        row {
                            TitleTextRow(
                                value = customText,
                                onChange = { text ->
                                    val next = text.take(BrowserSettings.START_TITLE_MAX)
                                    customText = next
                                    update { it.copy(startTitleText = next) }
                                },
                            )
                        }
                    }
                    row {
                        SwitchRow(
                            title = "Search field",
                            checked = settings.startShowSearch,
                            onCheckedChange = { on -> update { it.copy(startShowSearch = on) } },
                        )
                    }
                }
            }

            item(key = "favorites") {
                GroupedSection(modifier = Modifier.arrive(2), header = "Favorites") {
                    row {
                        SwitchRow(
                            title = "Show favorites",
                            checked = settings.showHomeFavorites,
                            onCheckedChange = { on -> update { it.copy(showHomeFavorites = on) } },
                        )
                    }
                    if (settings.showHomeFavorites) {
                        row {
                            NavRow(
                                title = "Number of favorites",
                                value = settings.startFavoritesLimit.toString(),
                                onClick = { sheet = StartSheet.Count },
                            )
                        }
                        row {
                            NavRow(
                                title = "Favorites from",
                                value = SourceChoices.first { it.first == settings.startFavoritesSource }.second,
                                onClick = { sheet = StartSheet.Source },
                            )
                        }
                    }
                }
            }
        }

        ChoiceSheet(
            visible = sheet == StartSheet.Title,
            title = "Title",
            message = null,
            options = TitleChoices.map { it.second },
            selectedIndex = TitleChoices.indexOfFirst { it.first == settings.startTitle },
            onSelect = { index ->
                update { it.copy(startTitle = TitleChoices[index].first) }
                sheet = null
            },
            onDismiss = { sheet = null },
        )
        ChoiceSheet(
            visible = sheet == StartSheet.Count,
            title = "Number of favorites",
            message = null,
            options = BrowserSettings.START_FAVORITES_COUNTS.map { it.toString() },
            selectedIndex = BrowserSettings.START_FAVORITES_COUNTS.indexOf(settings.startFavoritesLimit),
            onSelect = { index ->
                update { it.copy(startFavoritesCount = BrowserSettings.START_FAVORITES_COUNTS[index]) }
                sheet = null
            },
            onDismiss = { sheet = null },
        )
        ChoiceSheet(
            visible = sheet == StartSheet.Source,
            title = "Favorites from",
            message = null,
            options = SourceChoices.map { it.second },
            selectedIndex = SourceChoices.indexOfFirst { it.first == settings.startFavoritesSource },
            onSelect = { index ->
                update { it.copy(startFavoritesSource = SourceChoices[index].first) }
                sheet = null
            },
            onDismiss = { sheet = null },
        )
    }
}

/** The field for the custom title: a plain row, with how much of the limit is used on the right. */
@Composable
private fun TitleTextRow(value: String, onChange: (String) -> Unit) {
    val colors = PaneTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = RowMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Your own words", style = PaneTheme.type.body, color = colors.tertiaryLabel, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = PaneTheme.type.body.copy(color = colors.label),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().excludeFromAutofill(),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text("${value.length}/${BrowserSettings.START_TITLE_MAX}", style = PaneTheme.type.caption, color = colors.tertiaryLabel)
    }
}

/**
 * A miniature of the start page in a phone-shaped card, drawn with plain boxes: the chosen title, the
 * pill and the grid of tiles, all centred and flat, as on the page itself.
 */
@Composable
private fun StartPreview(settings: BrowserSettings) {
    val colors = PaneTheme.colors
    val shape = remember { ContinuousRoundedShape(28.dp) }
    val context = LocalContext.current
    val time = remember { DateFormat.getTimeFormat(context).format(Date()) }
    val title = settings.startTitle
    val custom = settings.startTitleCustom
    val hasTitle = title == StartTitle.Name || title == StartTitle.Clock || (title == StartTitle.Custom && custom.isNotEmpty())
    val rows = if (settings.showHomeFavorites) settings.startFavoritesLimit / 4 else 0

    Box(
        Modifier
            .width(120.dp)
            .aspectRatio(PhoneAspect)
            .floating(shape, 0.dp, fill = colors.surface)
            .clearAndSetSemantics { contentDescription = "Preview of the start page" },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).animateContentSize(Motion.sizeSpring),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                title == StartTitle.Clock -> {
                    Text(time, style = PaneTheme.type.title2.copy(fontSize = 22.sp, lineHeight = 26.sp), color = colors.label, maxLines = 1)
                    Box(Modifier.padding(top = 4.dp).width(44.dp).height(3.dp).background(colors.faint, CircleShape))
                }
                hasTitle -> Text(
                    if (title == StartTitle.Name) "Pane" else custom,
                    style = PaneTheme.type.title3.copy(fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.label,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (settings.startShowSearch) {
                if (hasTitle) Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().height(22.dp).floating(PaneShapes.pill, 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(PaneIcons.Magnifier, contentDescription = null, tint = colors.tertiaryLabel, modifier = Modifier.size(9.dp))
                    Box(Modifier.padding(start = 5.dp).width(34.dp).height(3.dp).background(colors.faint, CircleShape))
                }
            }
            if (rows > 0) {
                if (hasTitle || settings.startShowSearch) Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(rows) {
                        Row(Modifier.fillMaxWidth()) {
                            repeat(4) { MiniTile(Modifier.weight(1f).padding(horizontal = 3.dp)) }
                        }
                    }
                }
            }
            // Lifts the middle of the block, as on the page.
            Spacer(Modifier.height(36.dp))
        }
    }
}

/** One favourite in the miniature: a floating square with a dot for its icon and a dash for its name. */
@Composable
private fun MiniTile(modifier: Modifier) {
    val colors = PaneTheme.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).floating(RoundedCornerShape(8.dp), 0.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxSize(0.4f).background(colors.faint, CircleShape))
        }
        Box(Modifier.padding(top = 4.dp).fillMaxWidth(0.6f).height(2.dp).background(colors.faint, CircleShape))
    }
}

/** The proportions of a current phone screen, so the miniature reads as one. */
private const val PhoneAspect = 9f / 19.5f
