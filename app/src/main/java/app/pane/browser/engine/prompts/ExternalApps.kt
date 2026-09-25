package app.pane.browser.engine.prompts

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.core.net.toUri
import app.pane.browser.engine.PromptRequest
import app.pane.core.prompts.PromptText
import java.net.URISyntaxException

/** A page wants to hand a link to another app; the UI asks first. */
class ExternalAppRequest internal constructor(
    override val tabId: String?,
    val intent: Intent,
    /** Who will open it, when exactly one app can and we're allowed to see it. */
    val appLabel: String?,
    val fallbackUrl: String?,
    val isPrivate: Boolean,
    /** What the link points at, shortened for display. */
    val target: String,
) : PromptRequest {
    override val id: Long = PromptRequest.nextId()

    override fun dismiss() = Unit
}

/**
 * Turns links a page wants opened elsewhere (`mailto:`, `tel:`, `intent:`, app schemes) into safe
 * intents, and finds out who would handle them.
 */
object ExternalApps {
    /** Who can open an intent, as far as package visibility lets us tell. */
    sealed interface Handler {
        data class App(val label: String) : Handler

        /** More than one app; Android will ask. */
        data object Several : Handler

        /** Android 11+ hides apps we haven't declared; the link may still open. */
        data object Unknown : Handler

        data object None : Handler
    }

    private val blockedSchemes = setOf("file", "content", "javascript", "about", "chrome", "jar", "resource")
    private const val GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION

    /**
     * A sanitized intent for [uri]: it may only reach activities that declare themselves
     * browsable, never a named component, and carries no URI permission grants. Null when the
     * link must not leave the browser at all.
     */
    fun intentFor(uri: String): Intent? {
        val intent = if (uri.startsWith("intent:", ignoreCase = true)) {
            try {
                Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
            } catch (_: URISyntaxException) {
                return null
            } catch (_: IllegalArgumentException) {
                return null
            }
        } else {
            Intent(Intent.ACTION_VIEW, uri.toUri())
        }
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.component = null
        intent.selector = null
        intent.clipData = null
        intent.removeFlags(GRANT_FLAGS)
        val scheme = intent.data?.scheme?.lowercase()
        if (scheme != null && scheme in blockedSchemes) return null
        if (intent.data == null && intent.`package` == null) return null
        return intent
    }

    /** For an `intent:` link to an app that isn't installed: its store page. */
    fun storeIntentFor(intent: Intent): Intent? {
        val pkg = intent.`package`?.takeIf { it.matches(packagePattern) } ?: return null
        return Intent(Intent.ACTION_VIEW, "market://details?id=$pkg".toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
    }

    private val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")

    fun handlerFor(context: Context, intent: Intent): Handler {
        val pm = context.packageManager
        val info = resolve(pm, intent) ?: return if (Build.VERSION.SDK_INT >= 30) Handler.Unknown else Handler.None
        val activity = info.activityInfo ?: return Handler.Unknown
        return when (activity.packageName) {
            // Never bounce a link back into ourselves.
            context.packageName -> Handler.None
            // The system chooser stands in when several apps match.
            "android" -> Handler.Several
            else -> Handler.App(info.loadLabel(pm).toString())
        }
    }

    /** Starts [intent]; false if nothing took it. */
    fun launch(context: Context, intent: Intent): Boolean {
        val toStart = Intent(intent)
        if (context !is Activity) toStart.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(toStart)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    /** The part of the link worth showing: its data, or the app it targets. */
    fun describe(intent: Intent, rawUri: String): String =
        PromptText.shorten(intent.dataString ?: intent.`package` ?: rawUri, max = 90)

    // MATCH_DEFAULT_ONLY is one of the flag bits; the Long conversion just hides that from lint.
    @SuppressLint("WrongConstant")
    @Suppress("DEPRECATION")
    private fun resolve(pm: PackageManager, intent: Intent): ResolveInfo? = if (Build.VERSION.SDK_INT >= 33) {
        pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
    } else {
        pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    }
}
