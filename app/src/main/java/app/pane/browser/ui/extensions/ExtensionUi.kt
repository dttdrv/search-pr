package app.pane.browser.ui.extensions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pane.browser.AppContainer
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.Separator
import app.pane.browser.ui.components.pressScale
import app.pane.browser.ui.navigation.Navigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.ContinuousRoundedShape
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.core.extensions.Amo
import app.pane.core.extensions.AmoAddon

/** Separator inset for rows that start with a 29pt tile: 16 padding + 29 tile + 12 gap. */
internal val TileRowInset = 57.dp

/**
 * An extension's own icon on continuous corners; a plain neutral tile while it loads or when the
 * extension has none (no stand-in glyph). [dimmed] is used for extensions that are turned off.
 */
@Composable
internal fun ExtensionIcon(icon: ImageBitmap?, size: Dp, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    val colors = PaneTheme.colors
    Box(
        modifier
            .size(size)
            .graphicsLayer { alpha = if (dimmed) 0.45f else 1f }
            .clip(ContinuousRoundedShape(size * 0.225f))
            .background(if (icon == null) colors.fill else Color.Transparent),
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

/** The "Get" capsule: turns into a spinner while installing and "Installed" after. */
@Composable
internal fun GetButton(state: GetState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    // The capsule is small; the touch target around it is not.
    Box(
        modifier
            .defaultMinSize(minWidth = 76.dp, minHeight = 48.dp)
            .pressScale(enabled = state == GetState.Get, pressedScale = 0.9f, haptic = true, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .height(32.dp)
                .widthIn(min = 76.dp)
                .clip(PaneShapes.pill)
                .background(colors.fill)
                .padding(horizontal = 14.dp),
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
                        style = PaneTheme.type.subheadline.copy(fontWeight = FontWeight.Bold),
                        color = colors.accent,
                        maxLines = 1,
                    )
                    GetState.Installing -> CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = colors.accent,
                        strokeWidth = 2.dp,
                    )
                    GetState.Installed -> Text(
                        "Installed",
                        style = PaneTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.secondaryLabel,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * One row of a lazily built inset-grouped list: the same card look as [GroupedSection] but one
 * lazy item per row, for lists too long to compose at once.
 */
@Composable
internal fun GroupedItem(
    index: Int,
    count: Int,
    separatorInset: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = PaneTheme.colors
    val top = if (index == 0) CornerSize(20.dp) else CornerSize(0.dp)
    val bottom = if (index == count - 1) CornerSize(20.dp) else CornerSize(0.dp)
    val shape = remember(index == 0, index == count - 1) { ContinuousRoundedShape(top, top, bottom, bottom) }
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(colors.surface),
    ) {
        content()
        if (index < count - 1) Separator(Modifier.padding(start = separatorInset))
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
