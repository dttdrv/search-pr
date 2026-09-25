package app.pane.browser.ui.extensions

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.Separator
import app.pane.browser.ui.components.pressDim
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.theme.PaneTheme
import org.mozilla.geckoview.GeckoView

/**
 * An extension's options page in its own engine session, under a compact navigation bar. The
 * session lives exactly as long as the view showing it.
 */
@Composable
fun ExtensionOptionsScreen(extensionId: String) {
    val container = LocalAppContainer.current
    val manager = container.extensions
    val navigator = LocalNavigator.current
    val colors = PaneTheme.colors
    val installed by manager.installed.collectAsStateWithLifecycle()
    val ext = installed.firstOrNull { it.id == extensionId }
    val background = colors.background.toArgb()

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(Modifier.fillMaxWidth().background(colors.chrome)) {
            Spacer(Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding()))
            NavBar(title = ext?.name ?: "Settings", onBack = navigator::pop)
            Separator()
        }
        if (ext?.optionsPageUrl == null) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (ext == null) "This extension isn’t installed anymore." else "This extension has no settings.",
                    style = PaneTheme.type.subheadline,
                    color = colors.secondaryLabel,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding()
                    .imePadding(),
                factory = { context ->
                    GeckoView(context).also { view ->
                        // A TextureView follows the screen's slide and fade; a SurfaceView would not.
                        view.setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW)
                        view.coverUntilFirstPaint(background)
                        manager.openOptionsSession(extensionId)?.let { view.setSession(it) }
                    }
                },
                onRelease = { view ->
                    try {
                        view.releaseSession()?.close()
                    } catch (e: Exception) {
                        Log.w("ExtensionOptions", "Couldn't close the options page", e)
                    }
                },
            )
        }
    }
}

/** A 44pt bar with a back button and a centred title. */
@Composable
internal fun NavBar(title: String, onBack: () -> Unit, backLabel: String = "Back") {
    val colors = PaneTheme.colors
    Box(Modifier.fillMaxWidth().height(44.dp)) {
        Row(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp)
                .height(44.dp)
                .pressDim(onClick = onBack)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(PaneIcons.Back, contentDescription = "Back", tint = colors.accent, modifier = Modifier.size(26.dp))
            Text(backLabel, style = PaneTheme.type.body, color = colors.accent, maxLines = 1)
        }
        Text(
            title,
            style = PaneTheme.type.headline,
            color = colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 96.dp),
        )
    }
}
