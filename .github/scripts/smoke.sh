#!/usr/bin/env bash
# Launches Pane on an emulator, walks through the main surfaces, captures screenshots and fails
# on any crash.
set -u
APK=$(ls app/build/outputs/apk/debug/*.apk | head -1)
mkdir -p shots
shot() { sleep "${2:-2}"; adb exec-out screencap -p > "shots/$1.png"; echo "shot $1"; }
ui() { python3 .github/scripts/ui.py "$@" || true; }

adb install -r "$APK"
adb logcat -c
adb shell am start -W -n app.pane.browser/.MainActivity
shot 01-onboarding 12
ui text "Start Browsing"
shot 02-start-page 4

adb shell am start -W -a android.intent.action.VIEW -d "https://en.wikipedia.org/wiki/Web_browser" app.pane.browser
shot 03-page 18

ui desc "Menu"
shot 04-menu 3
adb shell input keyevent KEYCODE_BACK
sleep 2

ui desc-contains " tabs"
shot 05-tabs 4
ui text "Done"
sleep 3

ui desc-contains "Address"
sleep 2
adb shell input text "privacy"
shot 06-search 4
ui text "Cancel"
sleep 2

ui desc "Menu"
sleep 2
# Settings sits at the bottom of the menu sheet.
adb shell input swipe 540 1900 540 700 300
sleep 1
ui text "Settings"
shot 07-settings 3
adb shell input keyevent KEYCODE_BACK
sleep 2

ui desc "Menu"
sleep 2
ui text "Extensions"
shot 08-extensions 4
ui text "Browse Add-ons"
shot 08b-addon-store 8
adb shell input keyevent KEYCODE_BACK
sleep 2
adb shell input keyevent KEYCODE_BACK
sleep 2

ui desc "Menu"
sleep 2
adb shell input swipe 540 1900 540 700 300
sleep 1
ui text "History"
shot 08c-history 3
adb shell input keyevent KEYCODE_BACK
sleep 2

ui desc "Menu"
sleep 2
ui text "Bookmark"
sleep 2
adb shell input keyevent KEYCODE_BACK
sleep 1
ui desc "Menu"
sleep 2
adb shell input swipe 540 1900 540 700 300
sleep 1
ui text "Bookmarks"
shot 08d-bookmarks 3
adb shell input keyevent KEYCODE_BACK
sleep 2

# Private mode: new private tab from the menu, then back to the overview.
ui desc "Menu"
sleep 2
ui text "New Private Tab"
sleep 2
ui text "Cancel"
shot 09-private 3
ui desc-contains " tabs"
shot 10-private-tabs 4
ui text "Done"
sleep 2

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
