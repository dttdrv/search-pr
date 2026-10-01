package app.pane.browser.ui.theme

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

// the self-test builds the frost as the screen does, small: a page of hard black and white stripes
// (the same width as the screen, so the radius sits on it as it will), and a band over its bottom
// third with the same blur and colour filter, drawn to a bitmap. A band that works leaves a row with
// no stripes in it; one that draws nothing leaves nothing, and one whose blur is ignored leaves the
// stripes. (It need not be even: a blur that skips pixels turns the stripes into a slow ramp.)
private const val Stripe = 4f
private const val Tone = 0.15f
private val ProbeBand = 96.dp
private const val StepTimeout = 3_000L

/** How many radii the ladder tries: the full one, then halves of it. */
private const val Steps = 4

/** What a row of pixels read back from the self-test's band says: null when the blur worked, else why not. */
internal fun blurFault(row: IntArray): String? {
    val levels = row.map { ((it shr 8) and 0xFF) / 255f }
    return when {
        row.any { it ushr 24 != 0xFF } -> "empty"
        // stripes jump from black to white between neighbours; a blur leaves no such edge
        levels.zipWithNext { a, b -> abs(a - b) }.max() > Tone -> "sharp"
        // black and white stripes of one width average to the middle
        abs(levels.average() - 0.5) > Tone -> "no page"
        else -> null
    }
}

/** What the self-test found: [faults] has one entry per radius tried, from [full] down by halves, null for the one that worked. */
internal class FrostVerdict(val full: Float, val faults: List<String?>) {
    /** The radius, in px, the real band blurs by; null when none worked and the frost stays solid. */
    val radius: Float? = faults.indexOf(null).takeIf { it >= 0 }?.let { full / (1 shl it) }
}

/** Tries the radii from [full] down by halves with [fault] until one works. */
internal suspend fun frostVerdict(full: Float, fault: suspend (Float) -> String?): FrostVerdict {
    val faults = mutableListOf<String?>()
    for (step in 0 until Steps) {
        faults += fault(full / (1 shl step))
        if (faults.last() == null) break
    }
    return FrostVerdict(full, faults)
}

/** One line for the owner to paste back: the frost's mode, and the self-test behind it. */
internal fun frostSummary(verdict: FrostVerdict?, density: Float, sdk: Int): String {
    fun dp(px: Float) = (px / density).roundToInt()
    val tried = verdict?.faults?.mapIndexed { step, fault -> "${dp(verdict.full / (1 shl step))} dp ${fault ?: "ok"}" }?.joinToString(", ")
    return when {
        sdk < Build.VERSION_CODES.TIRAMISU -> "Frost: solid, API $sdk is below 33"
        verdict == null -> "Frost: solid, self-test not finished"
        verdict.radius != null -> "Frost: blur radius ${dp(verdict.radius)} dp. Self-test: $tried"
        else -> "Frost: solid, the blur failed the self-test: $tried"
    }
}

/** The self-test's verdict, once per process; null until it has run. */
internal object FrostCheck {
    var verdict by mutableStateOf<FrostVerdict?>(null)
        private set
    private var running = false

    suspend fun run(graphics: GraphicsContext, density: Density, width: Int) {
        if (verdict != null || running) return
        running = true
        try {
            verdict = probe(graphics, density, width)
        } finally {
            running = false
        }
    }
}

/** The blur radius the real band uses: the one that passed the self-test, null while it runs and where none did. */
@Composable
internal fun rememberFrostRadius(): Float? {
    val graphics = LocalGraphicsContext.current
    val density = LocalDensity.current
    val width = LocalWindowInfo.current.containerSize.width
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) LaunchedEffect(Unit) { FrostCheck.run(graphics, density, width) }
    return FrostCheck.verdict?.radius
}

private suspend fun probe(graphics: GraphicsContext, density: Density, width: Int): FrostVerdict {
    val rows = with(density) { ProbeBand.roundToPx() }
    val page = graphics.createGraphicsLayer()
    val band = graphics.createGraphicsLayer()
    try {
        page.asFrostPage()
        page.record(density, LayoutDirection.Ltr, IntSize(width, 3 * rows)) {
            drawRect(Color.White)
            var x = 0f
            while (x < width) {
                drawRect(Color.Black, Offset(x, 0f), Size(Stripe, size.height))
                x += 2 * Stripe
            }
        }
        val run = 8 * Stripe.toInt()
        return frostVerdict(density.frostRadius()) { radius ->
            try {
                band.asFrostBand(radius)
                band.record(density, LayoutDirection.Ltr, IntSize(width, rows)) {
                    drawRect(Color.White)
                    translate(top = -2f * rows) { drawLayer(page) }
                }
                val shot = withTimeoutOrNull(StepTimeout) { band.toImageBitmap() }
                val row = IntArray(run)
                shot?.readPixels(row, startX = (width - run) / 2, startY = rows / 2, width = run, height = 1)
                if (shot == null) "timeout" else blurFault(row)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.javaClass.simpleName
            }
        }
    } finally {
        graphics.releaseGraphicsLayer(page)
        graphics.releaseGraphicsLayer(band)
    }
}
