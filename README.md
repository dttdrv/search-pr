# Pane

A minimal Android browser built on the system WebView, with a small floating bar instead of a
toolbar.

Pane is written in Kotlin with **Jetpack Compose**. Pages are rendered by the Android WebView that
ships with the phone (through `androidx.webkit`), so there is no engine inside the APK. The chrome
is designed around one idea: the page is the interface. A small floating pill holds the address,
tabs and menu, and gets out of the way when you scroll. See [DESIGN.md](DESIGN.md).

## What it does

**Look and motion**
- Flat and black-and-white: true black and white, hairlines instead of boxes, one red used only as
  a status light, in your phone's own system font. No blur, no glass, which is gentle on the battery.
- The page runs truly edge to edge, under the status bar and the floating pill.
- Everything moves on springs from one small set of tokens, so gestures hand off to animations
  without a seam: the address pill *becomes* the search field, the menu grows out of its button,
  a tab's page flies into its card.
- Swipe the address pill sideways to move between tabs; swipe up for the tab overview.
- The system back gesture slides the page away and reveals the previous one, and can be cancelled
  halfway through.

**Browsing**
- Tabs and private tabs, session restore, recently closed tabs, optional closing of tabs you
  haven't looked at for a while.
- Bookmarks with favourites, history grouped by day, a download manager, find in page, desktop
  mode, add to home screen.
- Search engines with `@keyword` shortcuts (`@w`, `@yt`, `@gh`, ...), suggestions, open-tab
  suggestions.
- Per-site permissions asked in plain language, with a Remember switch.
- Launcher shortcuts for a new tab and a new private tab; "Search in Pane" in the text selection
  menu; hardware keyboard shortcuts.

**Privacy settings** (Settings > Privacy)
- Block ads and trackers, block third-party cookies, upgrade to HTTPS, remove tracking parameters
  from links you share or copy.
- Remember history, clear data on exit, lock private tabs with the phone's screen lock.
- No telemetry, no crash reporting, no accounts. Backups and device transfer exclude all browsing
  data.

**Ad blocking** (Settings > Privacy > Filter lists). Pane reads the standard uBlock Origin and
EasyList filter lists itself, with no extension runtime: request blocking (`||host^`, wildcards,
`$third-party`, `$domain`, resource types, `@@` exceptions, `$important`, `$badfilter`) and
element hiding, in a compact index that loads in milliseconds and stays within a few MB. The lists
(uBlock filters, privacy, badware, unbreak and resource abuse, EasyList, EasyPrivacy and Peter
Lowe's list) are GPL or CC BY-SA licensed, so they are not bundled: the app downloads them on the
device and refreshes them every few days on an unmetered connection, or when you tap the update
glyph. Scriptlets, HTML filtering, `$csp`, `$removeparam` and procedural cosmetic filters need
an extension runtime and are skipped.

**Passwords & passkeys**
- Pane keeps no passwords itself. Login forms go to whichever Android autofill service the phone
  has set up, and passkeys to its Credential Manager provider. Settings > Passwords shows which
  autofill service is active and opens the system picker.

## Project layout

```
core/   Engine-agnostic logic in plain Kotlin (URL fixup, search, tab store, settings model,
        privacy helpers). Builds and tests without the Android SDK.
app/    The Android app: WebView integration (engine/), storage (data/), downloads and the
        Compose UI (ui/).
```

## Install

Grab `Pane-<version>.apk` from the [latest release](../../releases/latest). It is one small APK for
every phone (Pane uses the system's Android System WebView, so keep that updated). Android 8.0 or
newer.

## Building

Requirements: JDK 17+ and an Android SDK with platform 37.1.

```sh
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease                  # minified (R8), about 2 MB
./gradlew -p core test                          # core unit tests, no Android SDK needed
```

CI (`.github/workflows/`) builds, lints and tests every push, builds a minified (R8) release,
and runs a native UI walkthrough on Android 14 and Android 9 covering onboarding, a live page,
Find/Back, external links, the menu, tabs, search, repeated appearance changes, bookmarks,
history, private-history isolation, screenshot protection, landscape and autofill. It fails on
assertion failures, crashes or Pane ANRs and uploads screenshots and diagnostics.

### Releases

The version lives in `VERSION`. Bumping it on the default branch (or pushing a tag such as
`v1.0.1`, or running `release.yml` by hand with a tag name) builds one universal APK and
publishes it with a checksum as a GitHub Release, creating the tag.

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

Pane's dependencies: AndroidX and Jetpack Compose (Apache-2.0), Kotlin and kotlinx libraries
(Apache-2.0). Pages are rendered by the Android WebView already on the phone.
