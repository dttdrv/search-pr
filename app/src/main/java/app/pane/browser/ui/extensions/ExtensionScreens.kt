package app.pane.browser.ui.extensions

import androidx.compose.runtime.Composable
import app.pane.browser.ui.components.PlaceholderScreen

// STUBS: owned by the extensions work stream.

@Composable fun ExtensionsScreen() = PlaceholderScreen("Extensions")
@Composable fun AddonStoreScreen() = PlaceholderScreen("Add-ons")
@Composable fun ExtensionDetailScreen(extensionId: String) = PlaceholderScreen("Extension")
@Composable fun ExtensionOptionsScreen(extensionId: String) = PlaceholderScreen("Options")

/** Install/permission prompts and extension popups, drawn above everything. */
@Composable fun ExtensionOverlays() = Unit
