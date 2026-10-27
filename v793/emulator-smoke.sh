#!/usr/bin/env bash
set -euo pipefail
mkdir -p dist
pkg=com.multify.traderpro.vivoy73final
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am start -n "$pkg/com.multify.traderpro.MainActivity" | tee dist/emulator-activity-launch.txt
sleep 14
adb logcat -d -v threadtime > dist/emulator-startup-logcat.txt
adb shell dumpsys activity activities > dist/emulator-activity-state.txt
if grep -E -q 'VerifyError|Verifier rejected class|FATAL EXCEPTION' dist/emulator-startup-logcat.txt; then
  grep -E -A 20 'VerifyError|Verifier rejected class|FATAL EXCEPTION' dist/emulator-startup-logcat.txt | head -n 150 || true
  echo "Android runtime verification or startup FAILED"
  exit 1
fi
if ! adb shell pidof "$pkg" ; then
  echo "App process not running after launch"
  exit 1
fi
echo "Android 15 startup verified: no VerifyError or FATAL EXCEPTION"
