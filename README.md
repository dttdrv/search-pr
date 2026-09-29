# Pane

A minimal, private Android browser in monochrome and liquid glass, with real Firefox extensions.

Pane is built on **GeckoView** (Mozilla's engine) and **Jetpack Compose**. The chrome is designed
around one idea: the page is the interface. The address bar, tabs and menu float over the page as
frosted glass, and get out of the way when you scroll. See [DESIGN.md](DESIGN.md).

## Highlights

**Look and motion**
- Ink on paper: black and white with no accent colour, in your phone's own system font.
- Liquid glass bars, menus and sheets: the page behind them is bent along the rim, softly blurred
  and frosted, with a specular highlight that wakes when you press. One setting (Appearance ›
  Glass effects) chooses Full, Light or Off, and Pane drops to Light on its own in battery saver.
- The page runs edge to edge. The status area takes the colour of the page's own top edge and
  dissolves into it as you scroll; the glass tone follows the page behind the bar. Fixed footers
  stay above the floating bar and drop to the screen edge when it melts away.
- Everything moves on springs from one small set of tokens, so gestures hand off to animations
  without a seam: the address pill *becomes* the search field, the menu grows out of its button,
  a tab's page flies into its card.
- Swipe the address pill sideways to move between tabs (past the last one opens a new tab); swipe
  up for the tab overview.
- The system back gesture slides the page away and reveals the previous one in parallax, and can
  be cancelled halfway through.
- Swipeable switches and segmented controls, Android's own overscroll stretch, and restrained
  haptics.

**Privacy & security** (all on by default)
- Web content runs in Android *isolated processes*: no permissions and no access to app data,
  even if a renderer is compromised.
- Strict Enhanced Tracking Protection, Total Cookie Protection, bounce-tracking protection,
  fingerprinting protection, query-parameter stripping and Safe Browsing.
- HTTPS-Only mode, DNS over HTTPS (Quad9 by default), Global Privacy Control.
- Private tabs: separate engine context, never written to disk, screenshots blocked, optional
  biometric lock.
- No telemetry, no crash reporting, no accounts. Backups and device transfer exclude all browsing
  data. Optional "clear everything on exit", finished at next launch if interrupted.
- Site permissions (location, camera, microphone, notifications, DRM, local network, …) are asked
  in plain language with a Remember switch; unremembered answers expire. Audible autoplay is
  blocked. Pages that spam dialogs can be silenced.
- `javascript:`, `data:`, `file:` and `content:` URLs are never run from the address bar or from
  other apps; pages can't open other apps without asking you first.
- Search suggestions and add-on store requests go through Gecko's own network stack
  (same DoH/TLS settings), anonymously.
- Site icons are fetched only for pages you open in normal tabs (never private ones), cached on
  the device, and cleared with browsing data.
- Links you share or copy have tracking parameters removed.

**Passwords & passkeys**
- Pane keeps no passwords itself and names no password manager. Login forms go to whichever
  Android autofill service the phone has set up, with the site's address, so it matches logins by
  website, fills them, and offers to save new ones after you sign in. Pane's own fields (address
  bar, find, search) are kept out of autofill; the HTTP sign-in prompt is marked as
  username/password.
- Passkeys on Android 14+ go through Credential Manager on the site's behalf, like other
  browsers, using whichever provider the phone has chosen.
- **Settings › Passwords** shows which autofill service is active and opens the system pickers
  for the autofill service and for passkeys.
- CI checks this for real: the emulator test installs a stand-in autofill service (debug builds
  only), opens github.com/login, and fails unless the form reaches it with its domain and
  password field.

**Edge to edge**
- The page and bars draw under the status and navigation bars; in landscape the UI keeps clear
  of a side navigation bar or camera cutout.

**Features**
- Firefox add-ons: an in-app store backed by addons.mozilla.org (search, ratings, one-tap
  install), install from file, browser-action buttons with badges, popups, options pages,
  per-extension private-browsing permission, daily update checks. Onboarding offers uBlock Origin.
- Built-in reader view (Mozilla Readability; light, sepia and dark themes, text size, reading
  time), find in page, desktop mode, add to home screen, site info sheet.
- Context menus for links, images and media: open in (private) tab, copy clean link, share, save.
- Native, iOS-style pickers for `<select>`, colours and date/time, plus sign-in, file upload
  and share prompts.
- Bookmarks with favourites, history grouped by day, a download manager.
- Search engines with `@keyword` shortcuts (`@w`, `@yt`, `@gh`, …), inline autocomplete of known
  sites, open-tab suggestions.
- Session restore, recently closed tabs, per-site permissions, clear browsing data.
- Launcher shortcuts for a new tab and a new private tab; "Search in Pane" in the text selection
  menu; hardware keyboard shortcuts (Ctrl+T/W/L/R/F, Ctrl+Tab, Alt+←/→).
- Optionally closes tabs you haven't looked at for a while (Settings › Tabs & toolbar).
- Settings open on this week's blocked-tracker count and a search over every setting, then group
  into Browsing, Look & feel and Privacy & data. Glass effects can be dialled to Full, Light or
  Off, and everything can be put back to its defaults from About.

## Project layout

```
core/   Engine-agnostic logic in plain Kotlin (URL fixup, search, tab store, privacy helpers,
        AMO client…). Builds and tests without the Android SDK.
app/    The Android app: GeckoView integration (engine/), storage (data/), extensions,
        downloads and the Compose UI (ui/).
```

## Install

Grab the APK from the [latest release](../../releases/latest): `Pane-<version>-arm64-v8a.apk`
fits almost every phone. Android 8.0 or newer.

## Building

Requirements: JDK 17+ and an Android SDK with platform 37.1.

```sh
./gradlew :app:assembleDebug                    # all ABIs
./gradlew :app:assembleDebug -Ppane.abis=arm64-v8a   # just one, much smaller
./gradlew -p core test                          # core unit tests, no Android SDK needed
```

CI (`.github/workflows/`) builds, lints and tests every push, builds a minified (R8) release,
and runs an emulator smoke test on Android 14 and Android 9 that walks through onboarding
(including a real uBlock Origin install), a live page, the menu, tabs, search suggestions,
settings, the add-on store, history, private browsing, landscape and autofill, failing on any
crash and uploading screenshots.

### Releases

The version lives in `VERSION`. Bumping it on the default branch (or pushing a tag such as
`v1.0.1`, or running `release.yml` by hand with a tag name) builds one APK per CPU type
(arm64-v8a, armeabi-v7a, x86_64) and publishes them with checksums as a GitHub Release,
creating the tag.

Signing uses the repository secret `PANE_KEYSTORE_BASE64` (a base64-encoded PKCS12 keystore;
optional `PANE_KEYSTORE_PASSWORD`, default `pane-release`, and `PANE_KEY_ALIAS`, default `pane`).
Keep the same key for every release so updates install over the previous version:

```sh
keytool -genkeypair -keystore pane.p12 -storetype PKCS12 -alias pane -keyalg EC -groupname secp256r1 \
  -validity 36500 -storepass pane-release -dname "CN=Pane"
base64 -w0 pane.p12   # paste into Settings › Secrets and variables › Actions › PANE_KEYSTORE_BASE64
```

Without the secret, a release is signed with a one-off key: it installs fine, but the next
release won't update it in place. Run the workflow from an existing tag to rebuild its APKs
after adding the secret. Plain CI release builds use the debug
key.

## Licences

Pane's dependencies: GeckoView (MPL-2.0), AndroidX and Jetpack Compose (Apache-2.0), Kotlin and
kotlinx libraries (Apache-2.0), Mozilla Readability (Apache-2.0).
