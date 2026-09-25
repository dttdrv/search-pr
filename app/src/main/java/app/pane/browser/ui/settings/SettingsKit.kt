package app.pane.browser.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.R
import app.pane.browser.ui.components.CheckRow
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.PaneSheet
import app.pane.browser.ui.components.SegmentedControl
import app.pane.browser.ui.components.SheetHeader
import app.pane.browser.ui.theme.ContinuousRoundedShape
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.core.settings.BrowserSettings

/** Current settings as Compose state; every settings screen reads through this. */
@Composable
internal fun rememberSettingsState(): State<BrowserSettings> =
    LocalAppContainer.current.settings.state.collectAsStateWithLifecycle()

/** A segmented control filling a grouped row, for two or three mutually exclusive modes. */
@Composable
internal fun SegmentedRow(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    SegmentedControl(
        options = options,
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

/** A small capsule label such as "Private" next to a row's title. */
@Composable
internal fun Tag(text: String, color: Color) {
    Text(
        text,
        style = PaneTheme.type.caption2.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        maxLines = 1,
        modifier = Modifier
            .clip(PaneShapes.pill)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/**
 * Pane's icon as the launcher draws it: the adaptive foreground over the dark background,
 * cropped to the visible 72 of its 108 units.
 */
@Composable
internal fun AppIcon(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(ContinuousRoundedShape(size * 0.225f))
            .background(Brush.verticalGradient(listOf(Color(0xFF26262E), Color(0xFF0B0B0F)))),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * 1.5f),
        )
    }
}

/**
 * A single-choice picker in a sheet, used where a choice needs a sentence of explanation and
 * doesn't warrant its own screen (per-site permission values).
 */
@Composable
internal fun ChoiceSheet(
    visible: Boolean,
    title: String,
    message: String?,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = PaneTheme.colors
    PaneSheet(visible = visible, onDismiss = onDismiss) {
        SheetHeader(title, onDone = onDismiss)
        if (message != null) {
            Text(
                message,
                style = PaneTheme.type.footnote,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp),
            )
        }
        GroupedSection {
            options.forEachIndexed { index, option ->
                row { CheckRow(option, selected = index == selectedIndex, onClick = { onSelect(index) }) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
