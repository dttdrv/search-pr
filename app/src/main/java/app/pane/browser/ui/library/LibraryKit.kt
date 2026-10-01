package app.pane.browser.ui.library

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.LeadingGap
import app.pane.browser.ui.components.RowMargin
import app.pane.browser.ui.components.SectionLabel
import app.pane.browser.ui.components.PaneSheet
import app.pane.browser.ui.components.Separator
import app.pane.browser.ui.components.ToastState
import app.pane.browser.ui.components.rowPress
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.entrance
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt
import app.pane.browser.ui.components.excludeFromAutofill

// Shared building blocks for the library and settings screens.

/**
 * Entrance for the first rows of a list. Plays once per row: rows that scroll back into view, or
 * shift up the list when another row is deleted, don't replay it.
 */
@Composable
internal fun Modifier.arrive(index: Int): Modifier {
    var played by rememberSaveable { mutableStateOf(false) }
    val play = remember { !played && index < ARRIVE_LIMIT }
    LaunchedEffect(Unit) { played = true }
    return if (play) this.entrance(index) else this
}

private const val ARRIVE_LIMIT = 9

/**
 * A date or group heading above library rows: small capitals, with 28dp of air before it. The
 * rows under it are flat, separated by hairlines, with no card behind them.
 */
@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    SectionLabel(
        text,
        color = PaneTheme.colors.secondaryLabel,
        modifier = modifier.padding(start = RowMargin, end = RowMargin, top = 28.dp, bottom = 4.dp),
    )
}

/** Footnote under a lazily built section. */
@Composable
internal fun SectionFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = PaneTheme.type.footnote,
        color = PaneTheme.colors.secondaryLabel,
        modifier = modifier.padding(start = RowMargin, end = RowMargin, top = 12.dp),
    )
}

/**
 * A list row with a one-line title and subtitle, press (it dims) and long-press. Unlike
 * [app.pane.browser.ui.components.ListRow] it supports long-press menus and extra content
 * beneath the text (a progress bar). Its text starts at 20dp, or after a 28dp icon and a 16dp
 * gap (64dp) when it has a [leading] mark, which is where [RowSeparator] should start.
 */
@Composable
internal fun LibraryRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    subtitleColor: Color = PaneTheme.colors.secondaryLabel,
    below: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = PaneTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .rowPress(onLongClick = onLongClick, onClick = onClick)
            .padding(start = RowMargin, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LeadingGap),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, style = PaneTheme.type.body, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = PaneTheme.type.footnote, color = subtitleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            below?.invoke()
        }
        trailing?.invoke(this)
    }
}

/** Where a library row's text starts when it leads with a 28dp site icon. */
internal val SiteRowInset = 64.dp

/**
 * Hairline between rows of a lazily built list, from [inset] (where the text starts) to the screen
 * margin.
 */
@Composable
internal fun RowSeparator(inset: Dp = RowMargin) {
    Separator(Modifier.padding(start = inset, end = RowMargin))
}

/**
 * iOS swipe-to-delete: the row follows the finger to reveal a red Delete button; a long or fast
 * swipe deletes outright. Everything settles on springs, so letting go mid-gesture feels natural.
 */
@Composable
internal fun SwipeToDelete(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = "Delete",
    content: @Composable () -> Unit,
) {
    val colors = PaneTheme.colors
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val actionWidth = with(LocalDensity.current) { 84.dp.toPx() }
    val offset = remember { Animatable(0f) }
    var width by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    val open by remember { derivedStateOf { offset.value < -1f } }

    fun commit() {
        scope.launch {
            offset.animateTo(-width, Motion.smooth())
            onDelete()
            // Normally the row has left the list by now. If it is still here (undone, or the
            // delete failed), slide it back rather than leaving an empty red row behind.
            delay(1_000)
            offset.animateTo(0f, Motion.snappy())
        }
    }

    fun close() {
        scope.launch { offset.animateTo(0f, Motion.snappy()) }
    }

    Box(
        modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width.toFloat() }
            .clipToBounds(),
    ) {
        // The action sits behind the row and is only drawn once the row has moved.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = if (offset.value < -0.5f) 1f else 0f }
                .background(colors.destructive)
                .clickable(enabled = open, onClick = ::commit),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                label,
                style = PaneTheme.type.body,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .width(84.dp)
                    // Past the button's width the label rides along with the row's edge.
                    .graphicsLayer { translationX = min(0f, offset.value + actionWidth) },
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                // Opaque, so the red action only shows once the row has moved off it.
                .background(colors.background)
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                    state = rememberDraggableState { delta ->
                        val next = (offset.value + delta).coerceIn(-width, 0f)
                        scope.launch { offset.snapTo(next) }
                        val beyond = width > 0f && next < -width * FULL_SWIPE
                        if (beyond != armed) {
                            armed = beyond
                            haptics.gestureThreshold()
                        }
                    },
                    onDragStopped = { velocity ->
                        val wasArmed = armed
                        armed = false
                        when {
                            wasArmed || (velocity < -3000f && offset.value < -actionWidth * 0.5f) -> commit()
                            offset.value < -actionWidth * 0.5f || velocity < -900f ->
                                offset.animateTo(-actionWidth, Motion.bouncy(), initialVelocity = velocity)
                            else -> offset.animateTo(0f, Motion.snappy(), initialVelocity = velocity)
                        }
                    },
                ),
        ) {
            content()
            if (open) {
                // While the button shows, a tap on the row just closes it again.
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = ::close),
                )
            }
        }
    }
}

private const val FULL_SWIPE = 0.62f

/** An action in an [ActionSheet]: words only, red when it destroys something. */
internal data class SheetAction(
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Long-press menu presented as a sheet: a header naming the item, then its actions as plain text
 * rows.
 */
@Composable
internal fun ActionSheet(
    visible: Boolean,
    title: String,
    subtitle: String?,
    actions: List<SheetAction>,
    onDismiss: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = PaneTheme.colors
    PaneSheet(visible = visible, onDismiss = onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(start = RowMargin, end = RowMargin, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f)) {
                Text(title, style = PaneTheme.type.headline, color = colors.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = PaneTheme.type.footnote, color = colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        GroupedSection {
            actions.forEach { action ->
                row {
                    val tint = if (action.destructive) colors.destructive else colors.label
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                            .rowPress {
                                onDismiss()
                                action.onClick()
                            }
                            .padding(horizontal = RowMargin),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(action.label, style = PaneTheme.type.body, color = tint, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** Centred placeholder for empty lists: a title and a sentence of guidance, no artwork. */
@Composable
internal fun EmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    Column(
        modifier.fillMaxWidth().arrive(0).padding(start = 40.dp, end = 40.dp, top = 96.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = PaneTheme.type.title3, color = colors.label, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = PaneTheme.type.subheadline, color = colors.secondaryLabel, textAlign = TextAlign.Center)
    }
}

/** A borderless text field for list sections: type on the row itself, no box around it. */
@Composable
internal fun FieldRow(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val colors = PaneTheme.colors
    val isUri = keyboardType == KeyboardType.Uri
    Box(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).padding(horizontal = RowMargin, vertical = 11.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(placeholder, style = PaneTheme.type.body, color = colors.tertiaryLabel, maxLines = 1)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = PaneTheme.type.body.copy(color = colors.label),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(
                capitalization = if (isUri) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
                autoCorrectEnabled = !isUri,
                keyboardType = keyboardType,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth().excludeFromAutofill(),
        )
    }
}

/**
 * The previous screen's name for the back button, as UINavigationController does. Worked out
 * once, when the screen appears, so it doesn't change while the screen animates away.
 */
@Composable
internal fun rememberBackLabel(self: Route): String {
    val navigator = LocalNavigator.current
    return remember {
        val index = navigator.stack.lastIndexOf(self)
        navigator.stack.getOrNull(index - 1)?.let(::backTitle) ?: "Back"
    }
}

private fun backTitle(route: Route): String? = when (route) {
    Route.Settings -> "Settings"
    Route.SearchSettings -> "Search"
    Route.PrivacySettings -> "Privacy"
    Route.Connections -> "Connections"
    Route.PasswordSettings -> "Passwords"
    Route.SiteSettings -> "Site permissions"
    Route.AppearanceSettings -> "Appearance"
    Route.ClearData -> "Clear data"
    Route.About -> "About"
    Route.Bookmarks -> "Bookmarks"
    Route.History -> "History"
    Route.Downloads -> "Downloads"
    Route.Extensions -> "Extensions"
    else -> null
}

internal fun copyToClipboard(context: Context, text: String, toasts: ToastState) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Link", text))
    // Android 13+ confirms copies with its own overlay.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) toasts.show("Link copied", PaneIcons.Copy)
}

internal fun shareLink(context: Context, url: String, title: String?) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
    if (!title.isNullOrBlank()) send.putExtra(Intent.EXTRA_TITLE, title)
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
}
