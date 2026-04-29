#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_BIN="${GRADLE_BIN:-"$ROOT_DIR/gradlew"}"
ADB_BIN="${ADB_BIN:-"${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/platform-tools/adb"}"

if [[ ! -x "$ADB_BIN" ]]; then
  ADB_BIN="adb"
fi

if [[ ! -x "$ADB_BIN" ]]; then
  echo "ADB not found. Set ADB_BIN or ensure adb is in PATH." >&2
  exit 1
fi

HOST_PORT="${HOST_PORT:-37913}"
DEVICE_PORT="${DEVICE_PORT:-37913}"
PACKAGE_NAME="com.riftwalker.sample"
ACTIVITY_NAME="com.riftwalker.sample/.MainActivity"
APK_PATH="$ROOT_DIR/sample-app/build/outputs/apk/debug/sample-app-debug.apk"
BASE_URL="http://127.0.0.1:$HOST_PORT"
SESSION_HEADER_PREFIX="X-Ai-Debug-Token: "

ADB_ARGS=()
if [[ -n "${DEVICE_SERIAL:-}" ]]; then
  ADB_ARGS=(-s "$DEVICE_SERIAL")
fi

run_adb() {
  "$ADB_BIN" "${ADB_ARGS[@]+"${ADB_ARGS[@]}"}" "$@"
}

log() {
  echo "[spec01-smoke] $*"
}

assert_contains() {
  local json="$1"
  local expected="$2"
  local label="$3"
  if [[ "$json" != *"$expected"* ]]; then
    echo "Assertion failed: expected $label" >&2
    echo "Payload: $json" >&2
    exit 1
  fi
}

http_get() {
  local path="$1"
  curl -sSf --connect-timeout 3 --max-time 8 \
    -H "content-type: application/json" \
    "$BASE_URL$path"
}

http_post() {
  local path="$1"
  local body="$2"
  local headers=("-H" "content-type: application/json")
  if [[ "${3:-}" == "with-token" ]]; then
    headers+=( "-H" "${SESSION_HEADER_PREFIX}${SESSION_TOKEN}" )
  fi
  curl -sSf --connect-timeout 3 --max-time 8 \
    -X POST "${headers[@]}" \
    -d "$body" \
    "$BASE_URL$path"
}

run_daemon_command() {
  local command="$1"
  "$GRADLE_BIN" -q :ai-debug-daemon:run --args="$command"
}

parse_session_token() {
  sed -n 's/.*"sessionToken":"\([^"]*\)".*/\1/p'
}

cleanup_forward() {
  run_adb forward --remove "tcp:$HOST_PORT" || true
}
trap 'cleanup_forward' EXIT

"$GRADLE_BIN" :sample-app:assembleDebug
run_adb install -r "$APK_PATH"
run_adb shell am force-stop "$PACKAGE_NAME" || true
run_adb shell am start -n "$ACTIVITY_NAME"
run_adb forward --remove "tcp:$HOST_PORT" || true
run_adb forward "tcp:$HOST_PORT" "tcp:$DEVICE_PORT"

sleep 1

PING_JSON="$(http_get "/runtime/ping")"
assert_contains "$PING_JSON" "\"sessionToken\":\"" "runtime sessionToken"
assert_contains "$PING_JSON" "\"packageName\":\"$PACKAGE_NAME\"" "packageName=$PACKAGE_NAME in runtime ping"
assert_contains "$PING_JSON" "\"debuggable\":true" "debuggable=true in runtime ping"
SESSION_TOKEN="$(echo "$PING_JSON" | parse_session_token)"
if [[ -z "${SESSION_TOKEN}" ]]; then
  echo "Failed to parse runtime session token" >&2
  exit 1
fi

CAPABILITIES_JSON="$(http_post "/capabilities/list" "{}" "with-token")"
assert_contains "$CAPABILITIES_JSON" "\"path\":\"runtime.ping\"" "runtime.ping capability"
assert_contains "$CAPABILITIES_JSON" "\"path\":\"state.set\"" "state.set capability"

DAEMON_PING="$(run_daemon_command "ping --host-port=$HOST_PORT")"
assert_contains "$DAEMON_PING" "\"packageName\":\"$PACKAGE_NAME\"" "daemon ping output"

DAEMON_CAPS="$(run_daemon_command "capabilities --host-port=$HOST_PORT")"
assert_contains "$DAEMON_CAPS" "\"path\":\"capabilities.list\"" "daemon capabilities output"
assert_contains "$DAEMON_CAPS" "\"path\":\"user.isVip\"" "daemon capabilities include sample state path"

AUDIT_JSON="$(http_post "/audit/history" "{}" "with-token")"
assert_contains "$AUDIT_JSON" "\"events\"" "audit history payload"

log "Smoke passed for spec01 runtime control plane."
