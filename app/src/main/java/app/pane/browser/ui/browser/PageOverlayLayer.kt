package app.pane.browser.ui.browser

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneTheme

/**
 * What a back step reveals under the live page as it slides away (see [backSlide]): the page before it,
 * drifting in from a third of the way over and lightening as it comes.
 */
@Composable
fun BackReveal(chrome: BrowserChrome) {
    val overlay = chrome.overlay?.takeIf { it.kind == PageOverlay.Kind.Back && it.current == null } ?: return
    val colors = PaneTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationX = -backDirection(chrome) * size.width * 0.3f * (1f - chrome.back.value) }
            .background(colors.background),
    ) {
        val behind = overlay.behind
        if (behind != null) {
            Snapshot(behind)
        } else if (!overlay.behindTitle.isNullOrBlank()) {
            Text(
                overlay.behindTitle,
                style = PaneTheme.type.title3,
                color = colors.tertiaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = 0.18f * (1f - chrome.back.value) }.background(Color.Black))
    }
}

/** The live page following a back step: moved by the gpu as it draws, so it keeps its own resolution and frames. */
fun Modifier.backSlide(chrome: BrowserChrome): Modifier = graphicsLayer {
    val p = if (chrome.overlay?.let { it.kind == PageOverlay.Kind.Back && it.current == null } == true) chrome.back.value else 0f
    translationX = backDirection(chrome) * size.width * p
    shadowElevation = if (p > 0f) 18.dp.toPx() * (1f - p) else 0f
}

private fun backDirection(chrome: BrowserChrome) = if (chrome.backFromRight) -1f else 1f

/**
 * Draws page snapshots over the live engine view while a transition is in flight: a page leaving as a new tab
 * takes its place, sideways tab swipes, and a cover that holds the destination still until the page paints it.
 */
@Composable
fun PageOverlayLayer(
    chrome: BrowserChrome,
    previousThumb: Bitmap?,
    nextThumb: Bitmap?,
    modifier: Modifier = Modifier,
) {
    val overlay = chrome.overlay
    // Keep the last cover around while it fades out; a cover with nothing to show holds the page's ground.
    val covering = overlay?.kind == PageOverlay.Kind.Cover
    val lastCover = remember { mutableStateOf<Bitmap?>(null) }
    if (covering) lastCover.value = overlay?.current
    val coverAlpha by animateFloatAsState(
        if (covering) 1f else 0f,
        Motion.fade(if (covering) 0 else 180),
        label = "cover",
        finishedListener = { if (it == 0f) lastCover.value = null },
    )
    val colors = PaneTheme.colors

    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        when (overlay?.kind) {
            PageOverlay.Kind.Back -> overlay.current?.let { leaving ->
                // the page leaving as another takes its place; the live one is already underneath
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = backDirection(chrome) * width * chrome.back.value
                            shadowElevation = 18.dp.toPx() * (1f - chrome.back.value)
                        }
                        .background(colors.background),
                ) {
                    Snapshot(leaving)
                }
            }
            PageOverlay.Kind.TabSwipe -> {
                val f = chrome.tabSwipe
                val neighbor = if (f < 0) nextThumb else previousThumb
                if (f != 0f) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationX = (f + if (f < 0) 1f else -1f) * width }
                            .background(colors.groupedBackground),
                    ) {
                        neighbor?.let { Snapshot(it) }
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = f * width }
                        .background(colors.background),
                ) {
                    overlay.current?.let { Snapshot(it) }
                }
            }
            else -> Unit
        }
        if (coverAlpha > 0f) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = coverAlpha }.background(colors.background)) {
                lastCover.value?.let { Snapshot(it) }
            }
        }
    }
}

@Composable
private fun Snapshot(bitmap: Bitmap) {
    Image(
        bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = Alignment.TopCenter,
        modifier = Modifier.fillMaxSize(),
    )
}
