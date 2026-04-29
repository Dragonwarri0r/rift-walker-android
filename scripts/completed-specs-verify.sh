#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_BIN="${GRADLE_BIN:-"$ROOT_DIR/gradlew"}"

if [[ -n "${ADB_BIN:-}" && -x "$ADB_BIN" ]]; then
  :
elif [[ -n "${ANDROID_HOME:-}" && -x "$ANDROID_HOME/platform-tools/adb" ]]; then
  ADB_BIN="$ANDROID_HOME/platform-tools/adb"
elif [[ -n "${ANDROID_SDK_ROOT:-}" && -x "$ANDROID_SDK_ROOT/platform-tools/adb" ]]; then
  ADB_BIN="$ANDROID_SDK_ROOT/platform-tools/adb"
elif command -v adb >/dev/null 2>&1; then
  ADB_BIN="$(command -v adb)"
else
  echo "ADB not found. Set ADB_BIN or ensure adb is in PATH." >&2
  exit 1
fi

command -v curl >/dev/null 2>&1 || { echo "curl is required." >&2; exit 1; }
command -v jq >/dev/null 2>&1 || { echo "jq is required for deep JSON assertions." >&2; exit 1; }

HOST_PORT="${HOST_PORT:-37913}"
DEVICE_PORT="${DEVICE_PORT:-37913}"
PACKAGE_NAME="com.riftwalker.sample"
ACTIVITY_NAME="com.riftwalker.sample/.MainActivity"
APK_PATH="$ROOT_DIR/sample-app/build/outputs/apk/debug/sample-app-debug.apk"
BASE_URL="http://127.0.0.1:$HOST_PORT"
SESSION_HEADER="X-Ai-Debug-Token"
TMP_DIR="$(mktemp -d)"

ADB_ARGS=()
if [[ -n "${DEVICE_SERIAL:-}" ]]; then
  ADB_ARGS=(-s "$DEVICE_SERIAL")
fi

SESSION_TOKEN=""
SESSION_ID=""

log() {
  echo "[completed-specs-verify] $*"
}

fail() {
  echo "[completed-specs-verify] FAILED: $*" >&2
  exit 1
}

run_adb() {
  "$ADB_BIN" "${ADB_ARGS[@]+"${ADB_ARGS[@]}"}" "$@"
}

cleanup() {
  run_adb forward --remove "tcp:$HOST_PORT" >/dev/null 2>&1 || true
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

assert_jq() {
  local json="$1"
  local expr="$2"
  local label="$3"
  if ! jq -e "$expr" <<<"$json" >/dev/null; then
    echo "Assertion failed: $label" >&2
    echo "JQ: $expr" >&2
    echo "Payload: $json" >&2
    exit 1
  fi
}

assert_jq_arg() {
  local json="$1"
  local arg_name="$2"
  local arg_value="$3"
  local expr="$4"
  local label="$5"
  if ! jq -e --arg "$arg_name" "$arg_value" "$expr" <<<"$json" >/dev/null; then
    echo "Assertion failed: $label" >&2
    echo "JQ: $expr" >&2
    echo "Payload: $json" >&2
    exit 1
  fi
}

request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local auth="${4:-token}"
  local expected_status="${5:-200}"
  local output="$TMP_DIR/http-body.json"
  local status
  local args=(-sS --connect-timeout 3 --max-time 15 -w "%{http_code}" -o "$output" -X "$method")

  args+=(-H "content-type: application/json")
  case "$auth" in
    token)
      [[ -n "$SESSION_TOKEN" ]] || fail "SESSION_TOKEN is empty before authenticated request $path"
      args+=(-H "$SESSION_HEADER: $SESSION_TOKEN")
      ;;
    none)
      ;;
    bad)
      args+=(-H "$SESSION_HEADER: invalid-token")
      ;;
    *)
      fail "Unknown auth mode: $auth"
      ;;
  esac

  if [[ "$method" != "GET" ]]; then
    args+=(-d "$body")
  fi

  status="$(curl "${args[@]}" "$BASE_URL$path")" || {
    local curl_status=$?
    echo "curl failed with exit code $curl_status for $method $path" >&2
    [[ -f "$output" ]] && cat "$output" >&2
    exit "$curl_status"
  }

  if [[ "$status" != "$expected_status" ]]; then
    echo "Unexpected HTTP status for $method $path: expected $expected_status, got $status" >&2
    cat "$output" >&2
    exit 1
  fi

  if ! jq -e . "$output" >/dev/null; then
    echo "Response for $method $path is not valid JSON:" >&2
    cat "$output" >&2
    exit 1
  fi

  cat "$output"
}

get_json() {
  request "GET" "$1" "" "${2:-token}" "${3:-200}"
}

post_json() {
  local body="${2:-}"
  if [[ -z "$body" ]]; then
    body="{}"
  fi
  request "POST" "$1" "$body" "${3:-token}" "${4:-200}"
}

wait_for_runtime() {
  local output="$TMP_DIR/ping.json"
  for _ in {1..30}; do
    if curl -sS --connect-timeout 2 --max-time 5 "$BASE_URL/runtime/ping" -o "$output" >/dev/null 2>&1 &&
      jq -e '.sessionToken and .sessionId' "$output" >/dev/null 2>&1; then
      cat "$output"
      return
    fi
    sleep 0.5
  done
  fail "Runtime did not respond on $BASE_URL/runtime/ping"
}

run_daemon_command() {
  local command="$1"
  "$GRADLE_BIN" -q :ai-debug-daemon:run --args="$command --host-port=$HOST_PORT"
}

trigger_profile_fetch() {
  run_adb shell am start -n "$ACTIVITY_NAME" --ez fetchProfile true >/dev/null
}

wait_for_network_record() {
  local rule_id="$1"
  local expr="$2"
  local label="$3"
  local history
  for _ in {1..30}; do
    history="$(post_json "/network/history" '{"limit":50,"urlRegex":".*/api/profile","includeBodies":true}')"
    if jq -e --arg rule "$rule_id" "$expr" <<<"$history" >/dev/null; then
      echo "$history"
      return
    fi
    sleep 0.5
  done
  echo "Last network history:" >&2
  echo "$history" >&2
  fail "Timed out waiting for network record: $label"
}

setup_device_app() {
  log "Building and installing debug sample app"
  "$GRADLE_BIN" :sample-app:assembleDebug >/dev/null
  run_adb install -r "$APK_PATH" >/dev/null
  run_adb shell am force-stop "$PACKAGE_NAME" >/dev/null 2>&1 || true
  run_adb shell pm clear "$PACKAGE_NAME" >/dev/null
  run_adb shell am start -n "$ACTIVITY_NAME" >/dev/null
  run_adb forward --remove "tcp:$HOST_PORT" >/dev/null 2>&1 || true
  run_adb forward "tcp:$HOST_PORT" "tcp:$DEVICE_PORT" >/dev/null
}

verify_runtime_control_plane() {
  log "Verifying spec001 runtime control plane"

  local ping_json
  ping_json="$(wait_for_runtime)"
  SESSION_TOKEN="$(jq -r '.sessionToken' <<<"$ping_json")"
  SESSION_ID="$(jq -r '.sessionId' <<<"$ping_json")"

  assert_jq "$ping_json" ".packageName == \"$PACKAGE_NAME\"" "runtime.ping packageName"
  assert_jq "$ping_json" ".processId | type == \"number\" and . > 0" "runtime.ping processId"
  assert_jq "$ping_json" ".debuggable == true" "runtime.ping debuggable"
  assert_jq "$ping_json" ".apiLevel | type == \"number\" and . >= 23" "runtime.ping apiLevel"
  assert_jq "$ping_json" ".runtimeVersion | type == \"string\" and length > 0" "runtime.ping runtimeVersion"
  assert_jq "$ping_json" ".sessionId | startswith(\"session_\")" "runtime.ping sessionId"
  assert_jq "$ping_json" ".sessionToken | type == \"string\" and length >= 24" "runtime.ping sessionToken"

  local unauth_json bad_auth_json
  unauth_json="$(post_json "/capabilities/list" '{}' none 401)"
  bad_auth_json="$(post_json "/capabilities/list" '{}' bad 401)"
  assert_jq "$unauth_json" '.error.code == "UNAUTHORIZED" and .error.recoverable == true' "missing token is rejected"
  assert_jq "$bad_auth_json" '.error.code == "UNAUTHORIZED" and .error.recoverable == true' "bad token is rejected"

  local caps_json network_caps vip_caps daemon_ping daemon_caps
  caps_json="$(post_json "/capabilities/list" '{}')"
  assert_jq "$caps_json" '.capabilities | length >= 30' "capability count covers runtime/network/state/storage/override"
  assert_jq "$caps_json" 'all(.capabilities[]; (.path|type=="string") and (.kind|type=="string") and (.schema|type=="object") and (.mutable|type=="boolean") and (.restore|type=="string") and (.audit|type=="string") and (.description|type=="string") and (.policy|type=="object"))' "capability descriptor shape"
  for path in runtime.ping capabilities.list audit.history session.cleanup network.history network.mock network.mutateResponse network.fail network.clearRules network.assertCalled state.list state.get state.set state.reset state.snapshot state.restore state.diff action.list action.invoke prefs.list prefs.get prefs.set prefs.delete storage.sql.query storage.sql.exec storage.snapshot storage.restore override.set override.get override.list override.clear user.isVip feature.newCheckout sample.refreshLocalState; do
    assert_jq_arg "$caps_json" path "$path" '.capabilities | any(.path == $path)' "capability path $path"
  done

  network_caps="$(post_json "/capabilities/list" '{"kind":"network"}')"
  assert_jq "$network_caps" 'all(.capabilities[]; .kind == "network") and (.capabilities | any(.path == "network.mock")) and (.capabilities | all(.path != "state.set"))' "capabilities kind filter"

  vip_caps="$(post_json "/capabilities/list" '{"query":"vip"}')"
  assert_jq "$vip_caps" '.capabilities | any(.path == "user.isVip")' "capabilities query filter"

  daemon_ping="$(run_daemon_command "ping")"
  assert_jq "$daemon_ping" ".packageName == \"$PACKAGE_NAME\" and .sessionId == \"$SESSION_ID\"" "daemon ping wraps runtime.ping"
  daemon_caps="$(run_daemon_command "capabilities")"
  assert_jq "$daemon_caps" '.capabilities | any(.path == "capabilities.list") and any(.path == "user.isVip")' "daemon capabilities wraps runtime capabilities"
}

verify_network_control() {
  log "Verifying spec002 network control"

  post_json "/network/clearRules" '{}' >/dev/null

  local negative_assert_json mock_json mock_rule history_json assert_json clear_json redaction_json redaction_rule redacted_history fail_json fail_rule fail_history mutate_json mutate_rule mutate_clear_json clear_all_json
  negative_assert_json="$(post_json "/network/assertCalled" '{"match":{"method":"GET","urlRegex":".*/never-called"},"minCount":1}')"
  assert_jq "$negative_assert_json" '.passed == false and .count == 0 and (.recordIds | length == 0)' "network.assertCalled negative path"

  mock_json="$(post_json "/network/mock" '{"match":{"method":"GET","urlRegex":".*/api/profile"},"response":{"status":201,"headers":{"content-type":"application/json"},"body":{"data":{"isVip":true,"name":"DeepMock"}}},"times":3}')"
  mock_rule="$(jq -r '.ruleId' <<<"$mock_json")"
  assert_jq "$mock_json" '.ruleId | startswith("rule_")' "network.mock rule id"
  assert_jq "$mock_json" '.restoreToken | startswith("cleanup_")' "network.mock restore token"

  trigger_profile_fetch
  history_json="$(wait_for_network_record "$mock_rule" '.records | any(.[]; ((.matchedRuleIds | index($rule)) != null) and (.status == 201) and (.responseBody | fromjson | .data.name == "DeepMock"))' "mocked profile response")"
  assert_jq_arg "$history_json" rule "$mock_rule" '.records[] | select((.matchedRuleIds | index($rule)) != null) | .method == "GET" and .url == "https://example.com/api/profile" and .durationMs >= 0 and .bodyRedacted == false' "mocked history record fields"

  assert_json="$(post_json "/network/assertCalled" '{"match":{"method":"GET","urlRegex":".*/api/profile"},"minCount":1}')"
  assert_jq "$assert_json" '.passed == true and .count >= 1 and (.recordIds | length >= 1)' "network.assertCalled positive path"

  local history_without_bodies
  history_without_bodies="$(post_json "/network/history" '{"limit":10,"urlRegex":".*/api/profile","includeBodies":false}')"
  assert_jq "$history_without_bodies" 'all(.records[]; has("responseBody") | not)' "network.history omits bodies by default"

  clear_json="$(post_json "/network/clearRules" '{"ruleIds":["'"$mock_rule"'"]}')"
  assert_jq "$clear_json" '.cleared == 1' "network.clearRules clears live mock rule"

  redaction_json="$(post_json "/network/mock" '{"match":{"method":"GET","urlRegex":".*/api/profile"},"response":{"status":200,"headers":{"content-type":"application/json"},"body":{"data":{"name":"Ada","accessToken":"secret-token","email":"ada@example.com"}}},"times":2}')"
  redaction_rule="$(jq -r '.ruleId' <<<"$redaction_json")"
  trigger_profile_fetch
  redacted_history="$(wait_for_network_record "$redaction_rule" '.records | any(.[]; ((.matchedRuleIds | index($rule)) != null) and (.bodyRedacted == true) and (.responseBody | contains("<redacted>")) and (.responseBody | contains("secret-token") | not) and (.responseBody | contains("ada@example.com") | not))' "redacted response body")"
  assert_jq_arg "$redacted_history" rule "$redaction_rule" '.records[] | select((.matchedRuleIds | index($rule)) != null) | .responseBody | fromjson | .data.accessToken == "<redacted>" and .data.email == "<redacted>"' "network history redacts sensitive JSON fields"
  post_json "/network/clearRules" '{"ruleIds":["'"$redaction_rule"'"]}' >/dev/null

  fail_json="$(post_json "/network/fail" '{"match":{"method":"GET","urlRegex":".*/api/profile"},"failure":{"type":"disconnect","delayMs":0},"times":2}')"
  fail_rule="$(jq -r '.ruleId' <<<"$fail_json")"
  trigger_profile_fetch
  fail_history="$(wait_for_network_record "$fail_rule" '.records | any(.[]; ((.matchedRuleIds | index($rule)) != null) and (.status == null) and (.error | contains("disconnect")))' "injected disconnect failure")"
  assert_jq_arg "$fail_history" rule "$fail_rule" '.records[] | select((.matchedRuleIds | index($rule)) != null) | .error | contains("Injected disconnect")' "network.fail records injected error"
  clear_json="$(post_json "/network/clearRules" '{"ruleIds":["'"$fail_rule"'"]}')"
  assert_jq "$clear_json" '.cleared == 1' "network.clearRules clears remaining failure rule"

  mutate_json="$(post_json "/network/mutateResponse" '{"match":{"method":"GET","urlRegex":".*/api/untriggered"},"patch":[{"op":"replace","path":"$.data.isVip","value":false}],"times":1}')"
  mutate_rule="$(jq -r '.ruleId' <<<"$mutate_json")"
  assert_jq "$mutate_json" '.ruleId | startswith("rule_")' "network.mutateResponse installs rule"
  mutate_clear_json="$(post_json "/network/clearRules" '{"ruleIds":["'"$mutate_rule"'"]}')"
  assert_jq "$mutate_clear_json" '.cleared == 1' "network.clearRules clears mutation rule"

  post_json "/network/mock" '{"match":{"urlRegex":".*/unused-a"},"response":{"status":200,"body":{"ok":"a"}}}' >/dev/null
  post_json "/network/fail" '{"match":{"urlRegex":".*/unused-b"},"failure":{"type":"timeout"}}' >/dev/null
  clear_all_json="$(post_json "/network/clearRules" '{}')"
  assert_jq "$clear_all_json" '.cleared == 2' "network.clearRules clears all remaining rules"
}

verify_state_storage_override() {
  log "Verifying spec003 state, storage, and overrides"

  local state_list state_get state_snapshot state_snapshot_id state_set state_diff state_restore state_reset action_list action_invoke unknown_state
  state_list="$(post_json "/state/list" '{"query":"vip","tag":"sample"}')"
  assert_jq "$state_list" '.states | any(.path == "user.isVip" and .mutable == true and (.tags | index("sample") != null))' "state.list query and tag filtering"

  state_get="$(post_json "/state/get" '{"path":"user.isVip"}')"
  assert_jq "$state_get" '.path == "user.isVip" and .value == false and .mutable == true' "initial user.isVip state"

  state_snapshot="$(post_json "/state/snapshot" '{"name":"before_state_mutation","paths":["user.isVip","feature.newCheckout"]}')"
  state_snapshot_id="$(jq -r '.snapshotId' <<<"$state_snapshot")"
  assert_jq "$state_snapshot" '.snapshotId | startswith("state_snap_")' "state.snapshot id"
  assert_jq "$state_snapshot" '(.paths | index("user.isVip") != null) and (.paths | index("feature.newCheckout") != null)' "state.snapshot paths"

  state_set="$(post_json "/state/set" '{"path":"user.isVip","value":true}')"
  assert_jq "$state_set" '.path == "user.isVip" and (.restoreToken | startswith("cleanup_"))' "state.set restore token"
  state_get="$(post_json "/state/get" '{"path":"user.isVip"}')"
  assert_jq "$state_get" '.value == true' "state.get after state.set"

  state_diff="$(post_json "/state/diff" '{"snapshotId":"'"$state_snapshot_id"'"}')"
  assert_jq "$state_diff" '.diffs | any(.path == "user.isVip" and .before == false and .after == true and .changed == true)' "state.diff detects mutation"

  state_restore="$(post_json "/state/restore" '{"snapshotId":"'"$state_snapshot_id"'"}')"
  assert_jq "$state_restore" '.restored | index("user.isVip") != null' "state.restore restored user.isVip"
  state_get="$(post_json "/state/get" '{"path":"user.isVip"}')"
  assert_jq "$state_get" '.value == false' "state.restore returns user.isVip to baseline"

  post_json "/state/set" '{"path":"user.isVip","value":true}' >/dev/null
  state_reset="$(post_json "/state/reset" '{"path":"user.isVip"}')"
  assert_jq "$state_reset" '.path == "user.isVip"' "state.reset response"
  state_get="$(post_json "/state/get" '{"path":"user.isVip"}')"
  assert_jq "$state_get" '.value == false' "state.reset returns user.isVip to default"

  action_list="$(post_json "/action/list" '{"query":"refresh","tag":"sample"}')"
  assert_jq "$action_list" '.actions | any(.path == "sample.refreshLocalState")' "action.list finds sample action"
  action_invoke="$(post_json "/action/invoke" '{"path":"sample.refreshLocalState"}')"
  assert_jq "$action_invoke" '.path == "sample.refreshLocalState"' "action.invoke returns action path"

  unknown_state="$(post_json "/state/get" '{"path":"missing.path"}' token 500)"
  assert_jq "$unknown_state" '.error.code == "RUNTIME_ERROR" and (.error.message | contains("Unknown state path"))' "unknown state returns error shape"

  local prefs_list storage_snapshot storage_snapshot_id prefs_set prefs_get prefs_delete sql_initial sql_exec sql_changed storage_restore sql_restored
  prefs_list="$(post_json "/prefs/list" '{"fileName":"sample_flags"}')"
  assert_jq "$prefs_list" '.fileName == "sample_flags" and (.entries | any(.key == "newCheckout" and .type == "boolean" and .value == false))' "prefs.list initial flag"

  storage_snapshot="$(post_json "/storage/snapshot" '{"name":"before_storage_mutation","prefsFiles":["sample_flags"],"databaseNames":["sample.db"]}')"
  storage_snapshot_id="$(jq -r '.snapshotId' <<<"$storage_snapshot")"
  assert_jq "$storage_snapshot" '(.prefsFiles | index("sample_flags") != null) and (.databaseNames | index("sample.db") != null)' "storage.snapshot captures prefs and database"

  prefs_set="$(post_json "/prefs/set" '{"fileName":"sample_flags","key":"newCheckout","value":true,"type":"boolean"}')"
  assert_jq "$prefs_set" '.fileName == "sample_flags" and .key == "newCheckout" and (.restoreToken | startswith("cleanup_"))' "prefs.set restore token"
  prefs_get="$(post_json "/prefs/get" '{"fileName":"sample_flags","key":"newCheckout"}')"
  assert_jq "$prefs_get" '.exists == true and .type == "boolean" and .value == true' "prefs.get after set"
  prefs_delete="$(post_json "/prefs/delete" '{"fileName":"sample_flags","key":"newCheckout"}')"
  assert_jq "$prefs_delete" '.fileName == "sample_flags" and .key == "newCheckout" and (.restoreToken | startswith("cleanup_"))' "prefs.delete restore token"
  prefs_get="$(post_json "/prefs/get" '{"fileName":"sample_flags","key":"newCheckout"}')"
  assert_jq "$prefs_get" '.exists == false' "prefs.get after delete"

  sql_initial="$(post_json "/storage/sql/query" '{"databaseName":"sample.db","sql":"SELECT id, vip, name FROM user_profile WHERE id = ?","args":["current"]}')"
  assert_jq "$sql_initial" '.columns == ["id","vip","name"] and (.rows | length == 1) and .rows[0].vip == 0 and .truncated == false' "initial SQL query"
  sql_exec="$(post_json "/storage/sql/exec" '{"databaseName":"sample.db","sql":"UPDATE user_profile SET vip = ? WHERE id = ?","args":["1","current"]}')"
  assert_jq "$sql_exec" '.databaseName == "sample.db" and (.restoreToken | startswith("cleanup_"))' "storage.sql.exec restore token"
  sql_changed="$(post_json "/storage/sql/query" '{"databaseName":"sample.db","sql":"SELECT vip FROM user_profile WHERE id = ?","args":["current"]}')"
  assert_jq "$sql_changed" '.rows[0].vip == 1' "SQL query after exec"

  storage_restore="$(post_json "/storage/restore" '{"snapshotId":"'"$storage_snapshot_id"'"}')"
  assert_jq "$storage_restore" '(.restoredPrefsFiles | index("sample_flags") != null) and (.restoredDatabaseNames | index("sample.db") != null)' "storage.restore response"
  prefs_get="$(post_json "/prefs/get" '{"fileName":"sample_flags","key":"newCheckout"}')"
  assert_jq "$prefs_get" '.exists == true and .value == false' "storage.restore restores prefs"
  sql_restored="$(post_json "/storage/sql/query" '{"databaseName":"sample.db","sql":"SELECT vip FROM user_profile WHERE id = ?","args":["current"]}')"
  assert_jq "$sql_restored" '.rows[0].vip == 0' "storage.restore restores sqlite database"

  local override_clear override_set override_get override_list override_expired feature_state audit_json cleanup_json old_token post_cleanup_ping
  override_clear="$(post_json "/override/clear" '{}')"
  assert_jq "$override_clear" '.cleared >= 0' "override.clear all baseline"

  override_set="$(post_json "/override/set" '{"key":"feature.newCheckout","value":true,"ttlMs":150}')"
  assert_jq "$override_set" '.key == "feature.newCheckout" and .exists == true and .value == true and (.expiresAtEpochMs | type == "number")' "override.set with TTL"
  override_get="$(post_json "/override/get" '{"key":"feature.newCheckout"}')"
  assert_jq "$override_get" '.exists == true and .value == true' "override.get before TTL expiry"
  sleep 0.25
  override_expired="$(post_json "/override/get" '{"key":"feature.newCheckout"}')"
  assert_jq "$override_expired" '.exists == false' "override TTL expiry"

  post_json "/prefs/set" '{"fileName":"sample_flags","key":"newCheckout","value":false,"type":"boolean"}' >/dev/null
  post_json "/override/set" '{"key":"feature.newCheckout","value":true}' >/dev/null
  feature_state="$(post_json "/state/get" '{"path":"feature.newCheckout"}')"
  assert_jq "$feature_state" '.value == true' "override influences feature flag state"
  override_list="$(post_json "/override/list" '{}')"
  assert_jq "$override_list" '.overrides | any(.key == "feature.newCheckout" and .value == true)' "override.list includes active override"
  override_clear="$(post_json "/override/clear" '{"key":"feature.newCheckout"}')"
  assert_jq "$override_clear" '.cleared == 1' "override.clear key"
  feature_state="$(post_json "/state/get" '{"path":"feature.newCheckout"}')"
  assert_jq "$feature_state" '.value == false' "override.clear returns feature flag to prefs-backed value"
  override_clear="$(post_json "/override/clear" '{"key":"feature.newCheckout"}')"
  assert_jq "$override_clear" '.cleared == 0' "override.clear is idempotent for missing key"

  audit_json="$(post_json "/audit/history" '{"sessionId":"'"$SESSION_ID"'"}')"
  for tool in capabilities.list network.mock network.fail network.clearRules state.set state.restore action.invoke prefs.set prefs.delete storage.sql.exec storage.restore override.set override.clear; do
    assert_jq_arg "$audit_json" tool "$tool" '.events | any(.tool == $tool and .status == "success")' "audit history contains $tool"
  done
  assert_jq "$audit_json" '.events | any(.effect == "mutate") and any(.effect == "read") and any(.effect == "action")' "audit history contains read, mutate, and action effects"

  old_token="$SESSION_TOKEN"
  cleanup_json="$(post_json "/session/cleanup" '{}')"
  assert_jq "$cleanup_json" '.cleaned | type == "number" and . >= 1' "session.cleanup runs cleanup hooks"
  post_json "/capabilities/list" '{}' token 401 >/dev/null
  SESSION_TOKEN="$old_token"
  post_json "/capabilities/list" '{}' token 401 >/dev/null
  post_cleanup_ping="$(get_json "/runtime/ping" none)"
  assert_jq "$post_cleanup_ping" ".sessionId != \"$SESSION_ID\" and .sessionToken != \"$old_token\"" "runtime.ping creates a new session after cleanup"
  SESSION_TOKEN="$(jq -r '.sessionToken' <<<"$post_cleanup_ping")"
  SESSION_ID="$(jq -r '.sessionId' <<<"$post_cleanup_ping")"
}

main() {
  log "Using adb: $ADB_BIN"
  run_adb devices >/dev/null
  setup_device_app
  verify_runtime_control_plane
  verify_network_control
  verify_state_storage_override
  log "Deep verification passed for completed specs 001, 002, and 003."
}

main "$@"
