#!/usr/bin/env bash
# Run the native UI walkthrough against real Compose and Gecko surfaces and retain diagnostics.
set -euo pipefail
mkdir -p shots
logcat_pid=""
snapshots_pid=""
diagnostics() {
  local result=$?
  for collector in "$logcat_pid" "$snapshots_pid"; do
    [ -z "$collector" ] || kill "$collector" 2>/dev/null || true
  done
  sudo journalctl -u systemd-oomd --no-pager > shots/host-oomd.txt 2>&1 || true
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
adb logcat -v threadtime > shots/logcat-live.txt &
logcat_pid=$!
(
  while sleep 5; do
    timeout 10 adb pull /sdcard/Android/data/app.pane.browser/files/smoke/. shots/ > /dev/null 2>&1 || true
  done
) &
snapshots_pid=$!
timeout 900 adb shell am instrument -w -e startupGlass "${PANE_STARTUP_GLASS:-full}" -e class app.pane.browser.BrowserSmokeTest \
  app.pane.browser.test/androidx.test.runner.AndroidJUnitRunner | tee shots/instrumentation.txt
grep -q 'OK (1 test)' shots/instrumentation.txt
adb logcat -d > shots/logcat.txt
if grep -qE 'FATAL EXCEPTION|ANR in app.pane.browser' shots/logcat.txt; then
  echo 'Crash or Pane ANR detected'
  exit 1
fi
echo 'Smoke test passed'
