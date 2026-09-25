#!/usr/bin/env bash
# Launches Pane on an emulator, walks through the main surfaces, captures screenshots and fails
# on any crash. Each section starts from the browser in the foreground.
set -u
APK=$(ls app/build/outputs/apk/debug/*.apk | head -1)
mkdir -p shots
shot() { sleep "${2:-2}"; adb exec-out screencap -p > "shots/$1.png"; echo "shot $1"; }
ui() { python3 .github/scripts/ui.py "$@"; }
SIZE=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1)
W=${SIZE%x*}; H=${SIZE#*x}
echo "screen ${W}x${H}"
scroll_up() { adb shell input swipe $((W / 2)) $((H * 70 / 100)) $((W / 2)) $((H * 25 / 100)) 450; sleep 1; }
front() { adb shell am start -n app.pane.browser/.MainActivity > /dev/null; sleep 2; }
back() { adb shell input keyevent KEYCODE_BACK; sleep "${1:-2}"; }
# Opens the "…" menu and taps an entry, scrolling the sheet if it's further down.
menu() {
  ui desc "Menu" || return 1
  sleep 2
  for _ in 1 2 3; do
    ui text "$1" && return 0
    scroll_up
  done
  return 1
}

adb install -r "$APK"
adb logcat -c
adb shell am start -W -n app.pane.browser/.MainActivity
shot 01-onboarding 12
ui text "Start Browsing"
shot 02-start-page 4
# Onboarding opted into uBlock Origin: wait for the install sheet from addons.mozilla.org and approve it.
for _ in 1 2 3 4 5 6; do
  if ui text "Add" > /dev/null 2>&1; then echo "approved uBlock Origin install"; break; fi
  sleep 5
done
shot 02b-after-install 6

adb shell am start -W -a android.intent.action.VIEW -d "https://en.wikipedia.org/wiki/Web_browser" app.pane.browser
shot 03-page 18

ui desc "Menu" && shot 04-menu 3
scroll_up
shot 04b-menu-scrolled 1
back

if ui desc-contains " tabs"; then shot 05-tabs 4; ui text "Done"; sleep 3; fi

if ui desc-contains "Address"; then
  sleep 2
  adb shell input text "privacy"
  shot 06-search 4
  ui text "Cancel" || back
  sleep 2
fi

front
if menu "Settings"; then shot 07-settings 3; back; fi

front
if menu "Extensions"; then
  shot 08-extensions 4
  if ui text "Browse Add-ons"; then shot 08b-addon-store 8; back; fi
  back
fi

front
if menu "History"; then shot 08c-history 3; back; fi

front
if menu "Bookmark"; then sleep 1; fi
front
if menu "Bookmarks"; then shot 08d-bookmarks 3; back; fi

front
if menu "Reader View"; then shot 08e-reader 5; fi

# Private mode: new private tab from the menu, then the overview.
front
if menu "New Private Tab"; then
  sleep 2
  ui text "Cancel" || back
  shot 09-private 3
  if ui desc-contains " tabs"; then shot 10-private-tabs 4; ui text "Done"; sleep 2; fi
fi

adb logcat -d > shots/logcat.txt
echo "==== Pane log excerpt ===="
grep -E "app.pane|GeckoView|Gecko  " shots/logcat.txt | grep -iE "error|exception|fatal" | head -60
if grep -q "FATAL EXCEPTION" shots/logcat.txt; then
  echo "==== CRASH ===="
  grep -A40 "FATAL EXCEPTION" shots/logcat.txt | head -120
  exit 1
fi
if ! adb shell pidof app.pane.browser > /dev/null; then
  echo "==== Pane is not running at the end of the smoke test ===="
  exit 1
fi
echo "Smoke test passed"
