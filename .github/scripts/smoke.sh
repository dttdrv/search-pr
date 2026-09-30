#!/usr/bin/env bash
# Run the native UI walkthrough against real Compose and Gecko surfaces and retain diagnostics.
set -euo pipefail
mkdir -p shots
diagnostics() {
  local result=$?
  free -m > shots/host-memory.txt
  sudo dmesg --ctime > shots/host-kernel.txt 2>&1 || true
  cp -r /tmp/android-runner/emu-crash* shots/ > /dev/null 2>&1 || true
  timeout 30 adb logcat -d > shots/logcat.txt || true
  timeout 30 adb pull /sdcard/Android/data/app.pane.browser/files/smoke/. shots/ > /dev/null 2>&1 || true
  if [ "$result" != 0 ]; then
    timeout 30 adb shell dumpsys activity lastanr > shots/last-anr.txt || true
    timeout 30 adb shell dumpsys dropbox --print data_app_anr > shots/anr.txt || true
    timeout 20 adb root > /dev/null 2>&1 || true
    timeout 20 adb wait-for-device || true
    timeout 30 adb pull /data/anr shots/anr-traces > /dev/null 2>&1 || true
  fi
}
trap diagnostics EXIT

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm clear app.pane.browser > /dev/null
adb logcat -c
timeout 900 adb shell am instrument -w -e class app.pane.browser.BrowserSmokeTest \
  app.pane.browser.test/androidx.test.runner.AndroidJUnitRunner | tee shots/instrumentation.txt
grep -q 'OK (1 test)' shots/instrumentation.txt
adb logcat -d > shots/logcat.txt
if grep -qE 'FATAL EXCEPTION|ANR in app.pane.browser' shots/logcat.txt; then
  echo 'Crash or Pane ANR detected'
  exit 1
fi
echo 'Smoke test passed'
