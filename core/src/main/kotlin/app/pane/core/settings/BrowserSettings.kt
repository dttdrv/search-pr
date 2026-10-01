package app.pane.core.settings

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode {
    System, Light, Dark;

    /** Whether the app is dark: forced by the choice, or, for [System], whatever the system is. */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        System -> systemDark
        Light -> false
        Dark -> true
    }
}

/**
 * What happens to a plain-http address. [First] tries the secure address and quietly falls back to
 * the one that was asked for; [Only] never falls back by itself and asks first.
 */
@Serializable
enum class HttpsMode { Off, First, Only }

/** What the start page says above its search field. */
@Serializable
enum class StartTitle { Name, Custom, Clock, None }

/** Where the start page's favourite tiles come from. */
@Serializable
enum class StartFavorites { Frequent, Bookmarks }

/**
 * User preferences. Defaults match a normal, well-behaved browser: ads and trackers are blocked and
 * third-party cookies refused, but never at the cost of a site not loading or a login not working.
 * Old stored settings keep decoding: unknown keys are ignored and missing ones take these defaults
 * (see [SettingsJson] for the two keys that are translated rather than dropped).
 */
@Serializable
data class BrowserSettings(
    val searchEngineId: String = "ddg",
    val searchSuggestions: Boolean = true,
    val searchSuggestionsInPrivate: Boolean = false,
    val blockAds: Boolean = true,
    /** The filter lists (by id) the ad blocker loads; lists added later are opt-in. */
    val enabledFilterLists: List<String> = BrowserSettings.DEFAULT_FILTER_LISTS,
    val blockThirdPartyCookies: Boolean = false,
    val httpsMode: HttpsMode = HttpsMode.First,
    /** Tells sites not to sell or share what they know about you (`Sec-GPC` and `navigator.globalPrivacyControl`). */
    val globalPrivacyControl: Boolean = true,
    /** Google's list of dangerous pages, checked by the system WebView. */
    val safeBrowsing: Boolean = true,
    val stripTrackingParams: Boolean = true,
    val javascriptEnabled: Boolean = true,
    val blockPopups: Boolean = true,
    val autoplayBlocked: Boolean = true,
    val lockPrivateTabs: Boolean = false,
    val clearOnExit: Boolean = false,
    val rememberHistory: Boolean = true,
    val secureScreenInPrivate: Boolean = true,
    val theme: ThemeMode = ThemeMode.System,
    /** Darken pages that have no dark style of their own; only in a dark theme. */
    val darkPages: Boolean = false,
    val hideToolbarOnScroll: Boolean = true,
    val tintToolbarWithPage: Boolean = true,
    val textScale: Float = 1.0f,
    val forceZoom: Boolean = true,
    val desktopModeByDefault: Boolean = false,
    val showHomeFavorites: Boolean = true,
    /** The start page's heading: the app name, the user's own words, the time, or nothing. */
    val startTitle: StartTitle = StartTitle.Name,
    /** The words shown when [startTitle] is [StartTitle.Custom] (at most [START_TITLE_MAX] characters). */
    val startTitleText: String = "",
    /** The centred search pill on the start page; tapping it opens the address editor. */
    val startShowSearch: Boolean = true,
    /** How many favourite tiles the start page shows: 4, 8 or 12 (see [startFavoritesLimit]). */
    val startFavoritesCount: Int = 8,
    val startFavoritesSource: StartFavorites = StartFavorites.Frequent,
    val haptics: Boolean = true,
    val reduceMotion: Boolean = false,
    val restoreTabs: Boolean = true,
    val closeTabsAfterDays: Int = 0,
    val onboardingDone: Boolean = false,
    /** Bumped when the defaults or the shape of the model change (see SettingsStore). */
    val defaultsVersion: Int = BrowserSettings.DEFAULTS_VERSION,
) {
    /** [startFavoritesCount] snapped to a size the grid supports, whatever was stored. */
    val startFavoritesLimit: Int
        get() = START_FAVORITES_COUNTS.minByOrNull { kotlin.math.abs(it - startFavoritesCount) } ?: 8

    /** The custom start title, trimmed and capped at [START_TITLE_MAX]. */
    val startTitleCustom: String
        get() = startTitleText.trim().take(START_TITLE_MAX)

    companion object {
        const val DEFAULTS_VERSION = 5

        /** The standard uBlock Origin and EasyList lists, by the ids the app's catalogue gives them. */
        val DEFAULT_FILTER_LISTS: List<String> = listOf(
            "ublock-filters", "ublock-privacy", "ublock-badware", "ublock-unbreak", "ublock-resource-abuse",
            "easylist", "easyprivacy", "peter-lowe",
        )
        const val START_TITLE_MAX = 24
        val START_FAVORITES_COUNTS: List<Int> = listOf(4, 8, 12)
    }
}
