package app.pane.browser.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.ContinuousRoundedShape
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A site's own icon at [size], from Pane's local cache; until it is known (or if the site has none)
 * a neutral tile with a globe. This is the only way sites are represented by an icon anywhere in
 * the app: never a letter standing in for a logo.
 *
 * Looking the icon up only reads the cache (memory first, then a small file off the main thread);
 * nothing here ever goes to the network.
 */
@Composable
fun SiteIcon(url: String, size: Dp, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    val favicons = LocalAppContainer.current.favicons
    // Icons arrive after their page loads, so lists look again whenever the store learns one.
    val version by favicons.version.collectAsStateWithLifecycle()
    val cached = remember(url) { favicons.peek(url) }
    val icon by produceState<Bitmap?>(cached, url, version) {
        value = favicons.peek(url) ?: withContext(Dispatchers.IO) { favicons.get(url) }
    }

    // An icon that was already in memory is simply there; one that turns up later fades in.
    val fade = remember(url) { Animatable(if (cached != null) 1f else 0f) }
    LaunchedEffect(icon != null) {
        if (icon != null) fade.animateTo(1f, Motion.fade()) else fade.snapTo(0f)
    }

    val shape = remember(size) { ContinuousRoundedShape(size * 0.28f) }
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(colors.fill),
        contentAlignment = Alignment.Center,
    ) {
        Icon(PaneIcons.Globe, null, tint = colors.tertiaryLabel, modifier = Modifier.size(size * 0.56f))

        val bitmap = icon
        if (bitmap != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = fade.value }
                    // Icons are often transparent, so they sit on white in both themes.
                    .background(Color.White)
                    .border(0.5.dp, colors.separator, shape)
                    .padding(size * 0.1f),
            ) {
                Image(
                    bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.Medium,
                )
            }
        }
    }
}
