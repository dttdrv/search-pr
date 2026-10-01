package app.pane.browser.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import app.pane.browser.R

/** The bundled mark for a search engine, or null for one without a logo. Shipped in the APK: nothing is fetched. */
@DrawableRes
fun engineIconRes(engineId: String): Int? = when (engineId) {
    "google" -> R.drawable.engine_google
    "bing" -> R.drawable.engine_bing
    "ddg" -> R.drawable.engine_duckduckgo
    "qwant" -> R.drawable.engine_qwant
    else -> null
}

/** A search engine's logo on a small white disc. Draws nothing for an engine without one. */
@Composable
fun EngineIcon(engineId: String, size: Dp, modifier: Modifier = Modifier) {
    val res = engineIconRes(engineId) ?: return
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            // Logos are drawn for a light ground; give them one in dark mode too.
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(res),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size * 0.8f),
        )
    }
}
