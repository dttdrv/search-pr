package app.pane.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val runtime = (application as PaneApp).runtime
        setContent {
            val session = remember { GeckoSession().apply { open(runtime); loadUri("https://example.com") } }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> GeckoView(ctx).apply { setSession(session) } },
            )
        }
    }
}
