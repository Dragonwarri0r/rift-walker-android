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
  echo "[spec02-smoke] $*"
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
  curl -sSf --connect-timeout 3 --max-time 8 \
    -X POST \
    -H "content-type: application/json" \
    -H "${SESSION_HEADER_PREFIX}${SESSION_TOKEN}" \
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
assert_contains "$PING_JSON" "\"sessionToken\":\"" "runtime session token"
assert_contains "$PING_JSON" "\"packageName\":\"$PACKAGE_NAME\"" "spec02 ping package name"
assert_contains "$PING_JSON" "\"apiLevel\":" "spec02 ping API level"

SESSION_TOKEN="$(echo "$PING_JSON" | parse_session_token)"
if [[ -z "${SESSION_TOKEN}" ]]; then
  echo "Failed to parse runtime session token" >&2
  exit 1
fi

MOCK_RESPONSE="$(http_post "/network/mock" '{
  "match": {"method": "GET", "urlRegex": ".*/api/profile"},
  "response": {
    "status": 200,
    "headers": {"content-type": "application/json"},
    "body": {"data": {"isVip": true, "name": "Spec02"}}
  },
  "times": 5
}')"
RULE_ID="$(echo "$MOCK_RESPONSE" | sed -n 's/.*"ruleId":"\([A-Za-z0-9._-]*\)".*/\1/p')"
if [[ -z "$RULE_ID" ]]; then
  echo "Mock rule response missing ruleId" >&2
  echo "Payload: $MOCK_RESPONSE" >&2
  exit 1
fi

assert_contains "$MOCK_RESPONSE" "\"restoreToken\"" "mock restore token"

run_adb shell am start -n "$ACTIVITY_NAME" --ez fetchProfile true
sleep 2

NETWORK_HISTORY_JSON="$(run_daemon_command "network-history --host-port=$HOST_PORT")"
assert_contains "$NETWORK_HISTORY_JSON" "\"url\":\"https://example.com/api/profile\"" "network history profile request"
assert_contains "$NETWORK_HISTORY_JSON" "\"matchedRuleIds\"" "network history matched rule ids"
assert_contains "$NETWORK_HISTORY_JSON" "$RULE_ID" "network history matched rule id"

ASSERT_CALLED_JSON="$(http_post "/network/assertCalled" '{
  "match": {"method": "GET", "urlRegex": ".*/api/profile"},
  "minCount": 1
}')"
assert_contains "$ASSERT_CALLED_JSON" "\"passed\":true" "network.assertCalled passed"
assert_contains "$ASSERT_CALLED_JSON" "\"count\":1" "network.assertCalled count"

CLEAR_RULES_JSON="$(http_post "/network/clearRules" '{"ruleIds":["'"$RULE_ID"'"]}')"
assert_contains "$CLEAR_RULES_JSON" "\"cleared\":1" "network.clearRules returned cleared=1"

log "Smoke passed for spec02 network control."
