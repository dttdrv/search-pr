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
 * Draws page snapshots over the live engine view while a gesture is in flight: the iOS-style
 * back swipe (current page slides away revealing the previous one with parallax), sideways tab
 * swipes, and a cover that holds the destination still until Gecko paints it.
 */
@Composable
fun PageOverlayLayer(
    chrome: BrowserChrome,
    previousThumb: Bitmap?,
    nextThumb: Bitmap?,
    modifier: Modifier = Modifier,
) {
    val overlay = chrome.overlay
    // Keep the last cover around while it fades out.
    val lastCover = remember { mutableStateOf<Bitmap?>(null) }
    if (overlay?.kind == PageOverlay.Kind.Cover && overlay.current != null) lastCover.value = overlay.current
    val coverAlpha by animateFloatAsState(
        if (overlay?.kind == PageOverlay.Kind.Cover) 1f else 0f,
        Motion.fade(if (overlay?.kind == PageOverlay.Kind.Cover) 0 else 180),
        label = "cover",
        finishedListener = { if (it == 0f) lastCover.value = null },
    )
    val colors = PaneTheme.colors

    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        when (overlay?.kind) {
            PageOverlay.Kind.Back -> {
                val p = chrome.back.value
                val dir = if (chrome.backFromRight) -1f else 1f
                // What's revealed underneath, drifting in from a third of the way over.
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = -dir * width * 0.3f * (1f - p) }
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
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = 0.18f * (1f - p) }.background(Color.Black))
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = dir * width * p
                            shadowElevation = 18.dp.toPx() * (1f - p)
                        }
                        .background(colors.background),
                ) {
                    overlay.current?.let { Snapshot(it) }
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
        val cover = lastCover.value
        if (cover != null && coverAlpha > 0f) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = coverAlpha }) { Snapshot(cover) }
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
