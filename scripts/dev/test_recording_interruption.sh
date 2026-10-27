#!/usr/bin/env bash
# Simulates system kills of the SMART recorder and checks that the
# "Study Recording Stopped" notification (RecordingWatchdogWorker) appears afterwards.
#
# Usage:  scripts/test_recording_interruption.sh <kill|crash|trim|forcestop|status>
# Needs: adb on PATH, a debug build installed (for run-as), recording started in the app.
# PKG defaults to the debug build; set PKG=com.screenlake for a release build (kill needs root).
set -euo pipefail

PKG="${PKG:-com.screenlake.debug}"
SCENARIO="${1:-status}"

pid_of() { adb shell pidof "$PKG" 2>/dev/null | tr -d '\r' || true; }

run_watchdog() {
  # Force-run the watchdog's JobScheduler job instead of waiting up to 15 min.
  # Relaunching the process (any WorkManager job) also triggers the immediate check from
  # BaseApplication.onCreate(), so forcing every WorkManager job of the app is fine.
  local jobs
  jobs=$(adb shell dumpsys jobscheduler 2>/dev/null | tr -d '\r' \
        | grep -E "JOB #u[0-9a-z]+/[0-9]+: .*$PKG/androidx.work" \
        | sed -E 's|.*JOB #[^/]+/([0-9]+):.*|\1|' | sort -u || true)
  if [[ -z "$jobs" ]]; then echo "!! no WorkManager jobs found for $PKG"; return; fi
  for j in $jobs; do adb shell cmd jobscheduler run -f "$PKG" "$j" >/dev/null 2>&1 || true; done
  echo ">> forced WorkManager jobs: $(echo $jobs)"
  sleep 8
}

show_status() {
  echo "== process pid: $(pid_of || true)"
  echo "== notification id 1338 (Study Recording Stopped / Paused):"
  adb shell dumpsys notification --noredact | tr -d '\r' \
    | grep -E "NotificationRecord.*pkg=$PKG.*id=1338" -A12 \
    | grep -E "NotificationRecord|android.title=|android.text=" || echo "   (not showing)"
  echo "== most recent process exits (OS view):"
  adb shell dumpsys activity exit-info "$PKG" | tr -d '\r' \
    | grep -E "timestamp=|reason=" | head -n 6 || true
}

case "$SCENARIO" in
  kill)      # closest to a low-memory kill: SIGKILL, no onDestroy()
    P=$(pid_of); [[ -z "$P" ]] && { echo "app not running"; exit 1; }
    echo ">> SIGKILL pid $P"
    adb shell run-as "$PKG" kill -9 "$P" 2>/dev/null || adb shell su -c "kill -9 $P"
    sleep 3; run_watchdog; show_status ;;
  crash)     # Android 11+
    adb shell am crash "$PKG"; sleep 3; run_watchdog; show_status ;;
  trim)      # memory pressure without a kill: recorder should survive
    P=$(pid_of); [[ -z "$P" ]] && { echo "app not running"; exit 1; }
    adb shell am send-trim-memory "$P" RUNNING_CRITICAL; sleep 3; show_status ;;
  forcestop) # expected: NO notification (Android removes it and cancels scheduled work)
    adb shell am force-stop "$PKG"; sleep 3; run_watchdog; show_status ;;
  status) show_status ;;
  *) echo "unknown scenario: $SCENARIO"; exit 2 ;;
esac
