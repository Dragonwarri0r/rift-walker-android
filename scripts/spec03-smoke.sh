#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_BIN="${GRADLE_BIN:-"$ROOT_DIR/gradlew"}"
ADB_BIN="${ADB_BIN:-"${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/platform-tools/adb"}"

if [[ ! -x "$ADB_BIN" ]]; then
  ADB_BIN="adb"
fi

if [[ ! -x "$ADB_BIN" ]]; then
  echo "ADB not found. Set ADB_BIN or ensure adb is in your PATH." >&2
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
  echo "[spec03-smoke] $*"
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

http_get_with_token() {
  local path="$1"
  curl -sSf --connect-timeout 3 --max-time 8 \
    -H "content-type: application/json" \
    -H "${SESSION_HEADER_PREFIX}${SESSION_TOKEN}" \
    "$BASE_URL$path"
}

http_post() {
  local path="$1"
  local body="$2"
  curl -sSf --connect-timeout 3 --max-time 8 \
    -X POST \
    -H "content-type: application/json" \
    -H "${SESSION_HEADER_PREFIX}${SESSION_TOKEN}" \
    -d "$body" \
    "$BASE_URL$path"
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
assert_contains "$PING_JSON" "\"packageName\":\"$PACKAGE_NAME\"" "spec03 ping package name"
SESSION_TOKEN="$(echo "$PING_JSON" | parse_session_token)"
if [[ -z "$SESSION_TOKEN" ]]; then
  echo "Failed to parse runtime session token" >&2
  exit 1
fi

STATE_LIST_JSON="$(http_post "/state/list" '{"query":"vip"}')"
assert_contains "$STATE_LIST_JSON" "\"path\":\"user.isVip\"" "state.list includes user.isVip"

STATE_SET_JSON="$(http_post "/state/set" '{"path":"user.isVip","value":true}')"
assert_contains "$STATE_SET_JSON" "\"path\":\"user.isVip\"" "state.set result path"
assert_contains "$STATE_SET_JSON" "\"restoreToken\"" "state.set restore token"

STATE_GET_JSON="$(http_post "/state/get" '{"path":"user.isVip"}')"
assert_contains "$STATE_GET_JSON" "\"value\":true" "state.get returns true"

PREFS_SET_JSON="$(http_post "/prefs/set" '{"fileName":"sample_flags","key":"newCheckout","value":true,"type":"boolean"}')"
assert_contains "$PREFS_SET_JSON" "\"restoreToken\"" "prefs.set restore token"

PREFS_GET_JSON="$(http_post "/prefs/get" '{"fileName":"sample_flags","key":"newCheckout"}')"
assert_contains "$PREFS_GET_JSON" "\"exists\":true" "prefs.get exists"
assert_contains "$PREFS_GET_JSON" "\"value\":true" "prefs.get value true"

ACTION_JSON="$(http_post "/action/invoke" '{"path":"sample.refreshLocalState"}')"
assert_contains "$ACTION_JSON" "\"path\":\"sample.refreshLocalState\"" "action.invoke target"

SQL_EXEC_JSON="$(http_post "/storage/sql/exec" '{"databaseName":"sample.db","sql":"UPDATE user_profile SET vip = ? WHERE id = ?","args":["1","current"]}')"
assert_contains "$SQL_EXEC_JSON" "\"restoreToken\"" "storage.sql.exec restore token"

SQL_QUERY_JSON="$(http_post "/storage/sql/query" '{"databaseName":"sample.db","sql":"SELECT id, vip, name FROM user_profile WHERE id = ?","args":["current"]}')"
assert_contains "$SQL_QUERY_JSON" "\"columns\"" "storage.sql.query columns"
assert_contains "$SQL_QUERY_JSON" "\"vip\"" "storage.sql.query vip column"
assert_contains "$SQL_QUERY_JSON" "\"vip\":1" "storage.sql.query changed vip"

OVERRIDE_SET_JSON="$(http_post "/override/set" '{"key":"feature.newCheckout","value":true}')"
assert_contains "$OVERRIDE_SET_JSON" "\"exists\":true" "override.set result"

OVERRIDE_GET_JSON="$(http_post "/override/get" '{"key":"feature.newCheckout"}')"
assert_contains "$OVERRIDE_GET_JSON" "\"exists\":true" "override.get returns override"

AUDIT_JSON="$(http_get_with_token "/audit/history")"
assert_contains "$AUDIT_JSON" "\"events\"" "audit history payload"

OVERRIDE_CLEAR_JSON="$(http_post "/override/clear" '{"key":"feature.newCheckout"}')"
assert_contains "$OVERRIDE_CLEAR_JSON" "\"cleared\":1" "override.clear returns cleared"

CLEANUP_JSON="$(http_post "/session/cleanup" '{}')"
assert_contains "$CLEANUP_JSON" "\"cleaned\":" "session.cleanup response"

log "Smoke passed for spec03 state/storage/override."
