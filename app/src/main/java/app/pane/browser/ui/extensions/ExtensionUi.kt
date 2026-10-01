package app.pane.browser.ui.extensions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pane.browser.AppContainer
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.RowMargin
import app.pane.browser.ui.components.Separator
import app.pane.browser.ui.components.pressScale
import app.pane.browser.ui.navigation.Navigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.ContinuousRoundedShape
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneTheme
import app.pane.core.extensions.Amo
import app.pane.core.extensions.AmoAddon

/** The icon size in extension rows. */
internal val ExtensionRowIcon = 32.dp

/** Separator inset for rows that start with a 32dp icon: 20 margin + 32 icon + 16 gap. */
internal val TileRowInset = 68.dp

/**
 * An extension's own icon, as it drew it, on softly rounded corners. While it loads, or when the
 * extension has none, only a hairline outline marks the place (no fill, no stand-in glyph).
 * [dimmed] is used for extensions that are turned off.
 */
@Composable
internal fun ExtensionIcon(icon: ImageBitmap?, size: Dp, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    val colors = PaneTheme.colors
    val shape = remember(size) { ContinuousRoundedShape(size * 0.225f) }
    Box(
        modifier
            .size(size)
            .graphicsLayer { alpha = if (dimmed) 0.45f else 1f }
            .clip(shape)
            .then(if (icon == null) Modifier.border(0.75.dp, colors.hairline, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    }
}

/** An add-on store icon fetched over the network (cached), cross-fading in over [fallback]. */
@Composable
internal fun AmoIcon(url: String?, size: Dp, modifier: Modifier = Modifier, fallback: @Composable () -> Unit = { ExtensionIcon(null, size) }) {
    val amo = LocalAppContainer.current.extensions.amo
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState(initialValue = url?.let { amo.cachedIcon(it) }, url) {
        val cached = url?.let { amo.cachedIcon(it) }
        value = cached
        if (url != null && cached == null) value = amo.icon(url, px)
    }
    Crossfade(targetState = bitmap, animationSpec = Motion.fade(180), modifier = modifier.size(size), label = "amoIcon") { image ->
        if (image != null) ExtensionIcon(image, size) else fallback()
    }
}

internal enum class GetState { Get, Installing, Installed }

/**
 * The "Get" button: a small hairline-outlined pill with no fill. It turns into a spinner while
 * installing and into quiet "Installed" text after, and the outline fades away with it.
 */
@Composable
internal fun GetButton(state: GetState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    val outline = animateFloatAsState(if (state == GetState.Get) 1f else 0f, Motion.fade(), label = "getOutline")
    // The pill is small; the touch target around it is not.
    Box(
        modifier
            .defaultMinSize(minWidth = 72.dp, minHeight = 48.dp)
            .pressScale(enabled = state == GetState.Get, pressedScale = 0.92f, haptic = true, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .height(32.dp)
                .widthIn(min = 64.dp)
                .drawBehind {
                    val stroke = 1.dp.toPx()
                    drawRoundRect(
                        color = colors.label.copy(alpha = colors.label.alpha * outline.value),
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius((size.height - stroke) / 2f),
                        style = Stroke(stroke),
                    )
                }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    (fadeIn(Motion.fade(160)) + scaleIn(Motion.snappy(), initialScale = 0.7f)).togetherWith(fadeOut(Motion.fade(90)))
                },
                contentAlignment = Alignment.Center,
                label = "get",
            ) { target ->
                when (target) {
                    GetState.Get -> Text(
                        "Get",
                        style = PaneTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.label,
                        maxLines = 1,
                    )
                    GetState.Installing -> CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = colors.label,
                        strokeWidth = 1.5.dp,
                    )
                    GetState.Installed -> Text(
                        "Installed",
                        style = PaneTheme.type.footnote,
                        color = colors.secondaryLabel,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * One row of a lazily built flat list: the same look as [GroupedSection][app.pane.browser.ui.components.GroupedSection]
 * (full-width, a hairline under it from [separatorInset]) but one lazy item per row, for lists
 * too long to compose at once.
 */
@Composable
internal fun GroupedItem(
    index: Int,
    count: Int,
    separatorInset: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        content()
        if (index < count - 1) Separator(Modifier.padding(start = separatorInset, end = RowMargin))
    }
}

/** Keeps showing the last non-null [value] while a sheet or alert animates away after it clears. */
@Composable
internal fun <T : Any> rememberRetained(value: T?): T? {
    val last = remember { mutableStateOf(value) }
    SideEffect { if (value != null) last.value = value }
    return value ?: last.value
}

/** Every URL an install of this listing may be keyed by in `ExtensionsManager.installing`. */
internal fun isInstalling(installing: Set<String>, slug: String, xpiUrl: String?): Boolean =
    Amo.latestXpiUrl(slug) in installing || (xpiUrl != null && xpiUrl in installing)

internal fun AmoAddon.installUrl(): String = xpiUrl ?: Amo.latestXpiUrl(slug)

/**
 * Opens an extension's options: in a tab when it asks for that (`open_in_tab`), otherwise on
 * Pane's own options screen.
 */
internal fun openExtensionOptions(container: AppContainer, navigator: Navigator, extensionId: String) {
    val ext = container.extensions.extension(extensionId) ?: return
    val url = ext.optionsPageUrl ?: return
    container.extensions.dismissPopup()
    if (ext.openOptionsPageInTab) {
        navigator.closeAll()
        container.browser.open(url, newTab = true, private = false)
    } else if (navigator.top != Route.ExtensionOptions(extensionId)) {
        navigator.push(Route.ExtensionOptions(extensionId))
    }
}

/** Opens [url] in a new normal tab and returns to the browser to show it. */
internal fun openInNewTab(container: AppContainer, navigator: Navigator, url: String) {
    navigator.closeAll()
    container.browser.open(url, newTab = true, private = false)
}
