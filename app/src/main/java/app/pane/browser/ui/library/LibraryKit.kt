package app.pane.browser.ui.library

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.unit.sp
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.PaneSheet
import app.pane.browser.ui.components.Separator
import app.pane.browser.ui.components.ToastState
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.ContinuousRoundedShape
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.rememberHaptics
import app.pane.core.library.LetterTiles
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt
import app.pane.browser.ui.components.excludeFromAutofill

// Shared building blocks for the library and settings screens.

/** iOS system colours for glyph tiles, so each settings row is recognisable at a glance. */
internal object TileColors {
    val Blue = Color(0xFF0A84FF)
    val Green = Color(0xFF34C759)
    val Indigo = Color(0xFF5856D6)
    val Orange = Color(0xFFFF9500)
    val Red = Color(0xFFFF3B30)
    val Purple = Color(0xFFAF52DE)
    val Teal = Color(0xFF30B0C7)
    val Gray = Color(0xFF8E8E93)
    val Pink = Color(0xFFFF2D55)
    val Yellow = Color(0xFFFFB800)
    val Mint = Color(0xFF00B8A9)
    val Brown = Color(0xFFA2845E)
}

/** Deep enough for white letters to stay legible in both themes. */
private val SitePalette = listOf(
    Color(0xFF5E5CE6),
    Color(0xFF0A7AFF),
    Color(0xFF1F9BB5),
    Color(0xFF2EA852),
    Color(0xFFE67E00),
    Color(0xFFE8335A),
    Color(0xFF9F55D9),
    Color(0xFF9A7B55),
    Color(0xFFD9443A),
    Color(0xFF3867C9),
)

/** Letter tile standing in for a favicon; colour and letter are stable per site. */
@Composable
internal fun SiteTile(url: String, title: String?, modifier: Modifier = Modifier, size: Dp = 29.dp) {
    val letter = remember(url, title) { LetterTiles.letter(url, title) }
    val key = remember(url) { LetterTiles.siteName(LetterTiles.hostKey(url)) }
    LetterTile(letter, key, modifier, size)
}

/** A letter on a colour picked from [colorKey]; a globe when there is no letter. */
@Composable
internal fun LetterTile(letter: String?, colorKey: String, modifier: Modifier = Modifier, size: Dp = 29.dp) {
    val color = remember(colorKey) { SitePalette[LetterTiles.colorIndex(colorKey, SitePalette.size)] }
    Box(
        modifier
            .size(size)
            .clip(ContinuousRoundedShape(size * 0.24f))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (letter != null) {
            Text(
                letter,
                style = PaneTheme.type.headline.copy(fontSize = (size.value * 0.52f).sp, lineHeight = (size.value * 0.6f).sp),
                color = Color.White,
                maxLines = 1,
            )
        } else {
            Icon(PaneIcons.Globe, null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
        }
    }
}

private val TopRounded = ContinuousRoundedShape(CornerSize(12.dp), CornerSize(12.dp), CornerSize(0.dp), CornerSize(0.dp))
private val BottomRounded = ContinuousRoundedShape(CornerSize(0.dp), CornerSize(0.dp), CornerSize(12.dp), CornerSize(12.dp))

/**
 * One row of an inset-grouped card built from separate lazy items, so rows can animate in and out
 * individually while the card keeps its rounded ends.
 */
internal fun Modifier.groupedItem(first: Boolean, last: Boolean, background: Color): Modifier {
    val shape: Shape = when {
        first && last -> PaneShapes.medium
        first -> TopRounded
        last -> BottomRounded
        else -> RectangleShape
    }
    return this.padding(horizontal = 16.dp).clip(shape).background(background)
}

/** Caps section title matching [GroupedSection]'s header, for sections built from lazy items. */
@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = PaneTheme.type.footnote,
        color = PaneTheme.colors.secondaryLabel,
        modifier = modifier.padding(start = 32.dp, end = 32.dp, top = 22.dp, bottom = 7.dp),
    )
}

/** Footnote under a lazily built section. */
@Composable
internal fun SectionFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = PaneTheme.type.footnote,
        color = PaneTheme.colors.secondaryLabel,
        modifier = modifier.padding(start = 32.dp, end = 32.dp, top = 7.dp),
    )
}

/**
 * A list row with a leading tile, a one-line title and subtitle, press and long-press. Unlike
 * [app.pane.browser.ui.components.ListRow] it supports long-press menus and extra content
 * beneath the text (a progress bar).
 */
@Composable
internal fun LibraryRow(
    title: String,
    subtitle: String?,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    subtitleColor: Color = PaneTheme.colors.secondaryLabel,
    below: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = PaneTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .combinedClickable(onLongClick = onLongClick, onClick = onClick)
            .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading()
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

/** Separator inset past a row's 29dp tile, as in iOS lists with images. */
@Composable
internal fun RowSeparator() {
    Separator(Modifier.padding(start = 57.dp))
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
                .background(colors.surface)
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

/** An action in an [ActionSheet]. */
internal data class SheetAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Long-press menu presented as a sheet: a header naming the item, then its actions with trailing
 * glyphs, like an iOS context menu.
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
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
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
                            .defaultMinSize(minHeight = 48.dp)
                            .clickable {
                                onDismiss()
                                action.onClick()
                            }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(action.label, style = PaneTheme.type.body, color = tint, modifier = Modifier.weight(1f))
                        Icon(action.icon, null, tint = tint, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** Centred placeholder for empty lists: glyph, title and a sentence of guidance. */
@Composable
internal fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    Column(
        modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 96.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = colors.tertiaryLabel, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, style = PaneTheme.type.title3, color = colors.label, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = PaneTheme.type.subheadline, color = colors.secondaryLabel, textAlign = TextAlign.Center)
    }
}

/** A borderless text field for grouped sections, like the fields in iOS edit sheets. */
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
        modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).padding(horizontal = 16.dp, vertical = 11.dp),
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
    Route.PasswordSettings -> "Passwords"
    Route.SiteSettings -> "Sites"
    Route.AppearanceSettings -> "Appearance"
    Route.ClearData -> "Clear Data"
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
