#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_BIN="${GRADLE_BIN:-"$ROOT_DIR/gradlew"}"
ADB_BIN="${ADB_BIN:-"${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/platform-tools/adb"}"

if [[ ! -x "$ADB_BIN" ]]; then
  ADB_BIN="adb"
fi

HOST_PORT="${HOST_PORT:-37913}"
DEVICE_PORT="${DEVICE_PORT:-37913}"
ACTIVITY_NAME="com.riftwalker.sample/.MainActivity"
APK_PATH="$ROOT_DIR/sample-app/build/outputs/apk/debug/sample-app-debug.apk"

ADB_ARGS=()
if [[ -n "${DEVICE_SERIAL:-}" ]]; then
  ADB_ARGS=(-s "$DEVICE_SERIAL")
fi

"$GRADLE_BIN" :sample-app:assembleDebug
"$ADB_BIN" "${ADB_ARGS[@]+"${ADB_ARGS[@]}"}" install -r "$APK_PATH"
"$ADB_BIN" "${ADB_ARGS[@]+"${ADB_ARGS[@]}"}" shell am start -n "$ACTIVITY_NAME"
"$ADB_BIN" "${ADB_ARGS[@]+"${ADB_ARGS[@]}"}" forward "tcp:$HOST_PORT" "tcp:$DEVICE_PORT"

sleep 1

SESSION_TOKEN="$(curl -sS "http://127.0.0.1:$HOST_PORT/runtime/ping" | sed -n 's/.*"sessionToken":"\([^"]*\)".*/\1/p')"
if [[ -z "$SESSION_TOKEN" ]]; then
  echo "Failed to read runtime session token" >&2
  exit 1
fi

post_runtime() {
  local path="$1"
  local body="$2"
  curl -sS "http://127.0.0.1:$HOST_PORT/$path" \
    -H "content-type: application/json" \
    -H "X-Ai-Debug-Token: $SESSION_TOKEN" \
    -d "$body"
}

SEARCH_RESULT="$(post_runtime "debug/objectSearch" '{"query":"vip","includeFields":true,"limit":20}')"
echo "$SEARCH_RESULT"
OBJECT_HANDLE="$(printf '%s' "$SEARCH_RESULT" | sed -n 's/.*"handle":"\([^"]*\)".*/\1/p' | head -n 1)"
if [[ -z "$OBJECT_HANDLE" ]]; then
  echo "Failed to find object handle for vip" >&2
  exit 1
fi

echo
post_runtime "probe/getField" "{\"target\":\"$OBJECT_HANDLE\",\"fieldPath\":\"isVip\"}"
echo
post_runtime "probe/setField" "{\"target\":\"$OBJECT_HANDLE\",\"fieldPath\":\"isVip\",\"value\":true}"
echo
post_runtime "debug/eval" "{\"language\":\"debug-dsl\",\"code\":\"env.probe.getField(\\\"$OBJECT_HANDLE\\\", \\\"isVip\\\")\",\"sideEffects\":\"read_only\"}"
echo
post_runtime "hook/overrideReturn" '{"methodId":"com.riftwalker.sample.MainActivity#isNewCheckoutEnabled()","returnValue":true,"times":1}'
echo
post_runtime "action/invoke" '{"path":"sample.refreshLocalState"}'
echo
