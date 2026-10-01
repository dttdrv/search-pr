package app.pane.browser.ui.browser

/**
 * Whether the ground under the system bars is dark, when a screen is laid over the page and paints
 * that ground itself; null while the page shows through (its own colours decide then). A pushed screen
 * and onboarding use the app's ground. The tab overview and the address editor take the private
 * ground over private tabs ([privateGround]), the app's otherwise.
 */
internal fun coverIsDark(pushed: Boolean, tabs: Boolean, editing: Boolean, privateGround: Boolean, appDark: Boolean): Boolean? = when {
    pushed -> appDark
    tabs || editing -> privateGround || appDark
    else -> null
}
