#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_BIN="${GRADLE_BIN:-"$ROOT_DIR/gradlew"}"
ADB_BIN="${ADB_BIN:-"${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/platform-tools/adb"}"

if [[ ! -x "$ADB_BIN" ]]; then
  ADB_BIN="adb"
fi

if ! command -v "$ADB_BIN" >/dev/null 2>&1 && [[ ! -x "$ADB_BIN" ]]; then
  echo "ADB not found. Set ADB_BIN or ensure adb is in PATH." >&2
  exit 1
fi

HOST_PORT="${HOST_PORT:-37913}"
DEVICE_PORT="${DEVICE_PORT:-37913}"
PACKAGE_NAME="${PACKAGE_NAME:-com.riftwalker.sample}"
ACTIVITY_NAME="${ACTIVITY_NAME:-com.riftwalker.sample/.MainActivity}"
APK_PATH="$ROOT_DIR/sample-app/build/outputs/apk/debug/sample-app-debug.apk"
REPORT_ROOT="$ROOT_DIR/build/ai-debug/dogfood-reports"

ADB_ARGS=()
DAEMON_ARGS=("dogfood-sample" "--host-port=$HOST_PORT" "--device-port=$DEVICE_PORT" "--package=$PACKAGE_NAME" "--activity=$ACTIVITY_NAME" "--report-root=$REPORT_ROOT")
if [[ -n "${DEVICE_SERIAL:-}" ]]; then
  ADB_ARGS=(-s "$DEVICE_SERIAL")
  DAEMON_ARGS+=("--serial=$DEVICE_SERIAL")
fi

run_adb() {
  "$ADB_BIN" "${ADB_ARGS[@]+"${ADB_ARGS[@]}"}" "$@"
}

log() {
  echo "[spec011-dogfood] $*"
}

assert_contains() {
  local payload="$1"
  local expected="$2"
  local label="$3"
  if [[ "$payload" != *"$expected"* ]]; then
    echo "Assertion failed: expected $label" >&2
    echo "Payload: $payload" >&2
    exit 1
  fi
}

cleanup_forward() {
  run_adb forward --remove "tcp:$HOST_PORT" >/dev/null 2>&1 || true
}
trap 'cleanup_forward' EXIT

log "Building sample debug APK"
"$GRADLE_BIN" :sample-app:assembleDebug >/dev/null

log "Installing sample app"
run_adb install -r "$APK_PATH" >/dev/null

cleanup_forward

log "Running daemon dogfood scenario"
DOGFOOD_JSON="$("$GRADLE_BIN" -q :ai-debug-daemon:run --args="${DAEMON_ARGS[*]}")"
echo "$DOGFOOD_JSON"

assert_contains "$DOGFOOD_JSON" '"status":"passed"' "passed scenario status"
assert_contains "$DOGFOOD_JSON" '"name":"dogfood_sample_profile_branch"' "dogfood report name"
assert_contains "$DOGFOOD_JSON" '"tool":"network.assertCalled","status":"passed"' "network assert step"
assert_contains "$DOGFOOD_JSON" '"tool":"hook.overrideReturn","status":"passed"' "hook step"
assert_contains "$DOGFOOD_JSON" '"tool":"state.set","status":"passed"' "state mutation step"
assert_contains "$DOGFOOD_JSON" '"tool":"storage.sql.exec","status":"passed"' "storage mutation step"
assert_contains "$DOGFOOD_JSON" '"path":"' "report path"

log "Dogfood scenario passed."
