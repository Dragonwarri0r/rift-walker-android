#!/usr/bin/env bash
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_BIN="${GRADLE_BIN:-"$ROOT_DIR/gradlew"}"
ADB_BIN="${ADB_BIN:-"${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/platform-tools/adb"}"
JQ_BIN="${JQ_BIN:-jq}"
PYTHON_BIN="${PYTHON_BIN:-python3}"

if [[ ! -x "$ADB_BIN" ]]; then
  ADB_BIN="adb"
fi

HOST_PORT="${HOST_PORT:-37913}"
DEVICE_PORT="${DEVICE_PORT:-37913}"
PACKAGE_NAME="${PACKAGE_NAME:-com.riftwalker.sample}"
ACTIVITY_NAME="${ACTIVITY_NAME:-com.riftwalker.sample/.MainActivity}"
APK_PATH="$ROOT_DIR/sample-app/build/outputs/apk/debug/sample-app-debug.apk"
REPORT_ROOT="${REPORT_ROOT:-"$ROOT_DIR/build/ai-debug/media-verification"}"
FIXTURE_HOST_DIR="${FIXTURE_HOST_DIR:-"$REPORT_ROOT/fixtures"}"
AUDIO_FIXTURE="${AUDIO_FIXTURE:-"$FIXTURE_HOST_DIR/audio-015.wav"}"
CAMERA_FIXTURE="${CAMERA_FIXTURE:-"$FIXTURE_HOST_DIR/camera-015.nv21"}"
DEVICE_FIXTURE_BASE="${DEVICE_FIXTURE_BASE:-/data/local/tmp/ai-debug-media/015}"
INSTRUMENTATION_REPORT="$ROOT_DIR/sample-app/build/ai-debug/instrumentation-debug-report.json"
RELEASE_SAFETY_REPORT="$ROOT_DIR/sample-app/build/ai-debug/release-safety-report.json"

RUN_ID="media_$(date +%Y%m%d_%H%M%S)"
REPORT_PATH="$REPORT_ROOT/report_$RUN_ID.json"
TMP_DIR="$REPORT_ROOT/.tmp_$RUN_ID"
SECTION_DIR="$TMP_DIR/sections"
FIXTURES_JSONL="$TMP_DIR/fixtures.jsonl"
CLEANUP_JSONL="$TMP_DIR/cleanup.jsonl"
DEVICE_FIXTURE_DIR="$DEVICE_FIXTURE_BASE/$RUN_ID"

RUN_FOUNDATION=0
RUN_AUDIO=0
RUN_CAMERA=0
RUN_BUILD_SAFETY=0
MODE_SPECIFIED=0
KEEP_APP_DATA=0
FINALIZED=0
SESSION_TOKEN=""
PING_BODY="{}"
RUNTIME_HTTP_STATUS=""
CURRENT_SECTION=""

ADB_ARGS=()
if [[ -n "${DEVICE_SERIAL:-}" ]]; then
  ADB_ARGS=(-s "$DEVICE_SERIAL")
fi

REGISTERED_FIXTURE_IDS=()
PUSHED_DEVICE_PATHS=()
SELECTED_MODES=()

log() {
  echo "[spec015-media] $*"
}

now_ms() {
  "$PYTHON_BIN" -c 'import time; print(int(time.time() * 1000))'
}

require_command() {
  local command_name="$1"
  if ! command -v "$command_name" >/dev/null 2>&1 && [[ ! -x "$command_name" ]]; then
    echo "Required command not found: $command_name" >&2
    return 1
  fi
}

run_adb() {
  "$ADB_BIN" "${ADB_ARGS[@]+"${ADB_ARGS[@]}"}" "$@"
}

json_array_from_jsonl() {
  local file="$1"
  if [[ -s "$file" ]]; then
    "$JQ_BIN" -s '.' "$file"
  else
    printf '[]'
  fi
}

normalize_json_body() {
  "$PYTHON_BIN" -c '
import json
import sys

body = sys.stdin.read()
candidate = body.strip()
while candidate:
    try:
        json.loads(candidate)
        print(candidate, end="")
        raise SystemExit(0)
    except json.JSONDecodeError:
        if not candidate.endswith("}"):
            break
        candidate = candidate[:-1].rstrip()
print(body, end="")
'
}

section_file() {
  printf '%s/%s.section.json' "$SECTION_DIR" "$1"
}

section_evidence_file() {
  printf '%s/%s.evidence.jsonl' "$SECTION_DIR" "$1"
}

section_errors_file() {
  printf '%s/%s.errors.jsonl' "$SECTION_DIR" "$1"
}

add_evidence() {
  local section="$1"
  local kind="$2"
  local tool="$3"
  local summary="$4"
  local payload="${5:-{}}"
  local normalized_payload
  local payload_json
  mkdir -p "$SECTION_DIR"
  normalized_payload="$(printf '%s' "$payload" | normalize_json_body)"
  if payload_json="$("$JQ_BIN" -c . <<<"$normalized_payload" 2>/dev/null)"; then
    :
  else
    payload_json="$("$JQ_BIN" -nc --arg raw "$payload" '{raw:$raw}')"
  fi
  "$JQ_BIN" -nc \
    --arg kind "$kind" \
    --arg tool "$tool" \
    --arg summary "$summary" \
    --argjson payload "$payload_json" \
    '{kind:$kind, tool:$tool, summary:$summary, payload:$payload}' \
    >>"$(section_evidence_file "$section")"
}

add_error() {
  local section="$1"
  local message="$2"
  mkdir -p "$SECTION_DIR"
  "$JQ_BIN" -nc --arg message "$message" '$message' >>"$(section_errors_file "$section")"
}

write_section() {
  local name="$1"
  local status="$2"
  local required="$3"
  local started="$4"
  local duration="$5"
  local evidence
  local errors
  evidence="$(json_array_from_jsonl "$(section_evidence_file "$name")")"
  errors="$(json_array_from_jsonl "$(section_errors_file "$name")")"
  "$JQ_BIN" -n \
    --arg name "$name" \
    --arg status "$status" \
    --argjson required "$required" \
    --argjson startedAtEpochMs "$started" \
    --argjson durationMs "$duration" \
    --argjson evidence "$evidence" \
    --argjson errors "$errors" \
    '{
      name: $name,
      status: $status,
      required: $required,
      startedAtEpochMs: $startedAtEpochMs,
      durationMs: $durationMs,
      evidence: $evidence,
      errors: $errors
    }' >"$(section_file "$name")"
}

skip_section() {
  local name="$1"
  local required="$2"
  local started
  started="$(now_ms)"
  write_section "$name" "skipped" "$required" "$started" 0
}

run_section() {
  local name="$1"
  local required="$2"
  local function_name="$3"
  local started
  local finished
  local status
  local rc
  CURRENT_SECTION="$name"
  started="$(now_ms)"
  log "Running $name section"
  "$function_name"
  rc=$?
  finished="$(now_ms)"
  case "$rc" in
    0) status="passed" ;;
    2) status="blocked" ;;
    3) status="skipped" ;;
    *) status="failed" ;;
  esac
  write_section "$name" "$status" "$required" "$started" "$((finished - started))"
  CURRENT_SECTION=""
}

http_get_capture() {
  local out_var="$1"
  local path="$2"
  local body_file
  local error_file
  local status
  local rc
  local raw_body
  local normalized_body
  body_file="$(mktemp "$TMP_DIR/http_body.XXXXXX")"
  error_file="$(mktemp "$TMP_DIR/http_error.XXXXXX")"
  status="$(curl -sS -o "$body_file" -w "%{http_code}" --connect-timeout 1 --max-time 5 "http://127.0.0.1:$HOST_PORT$path" 2>"$error_file")"
  rc=$?
  if [[ "$rc" -ne 0 ]]; then
    RUNTIME_HTTP_STATUS="000"
    printf -v "$out_var" '%s' "$("$JQ_BIN" -nc --arg message "$(cat "$error_file")" '{error:{code:"HTTP_CLIENT_ERROR",message:$message,recoverable:true}}')"
  else
    RUNTIME_HTTP_STATUS="$status"
    raw_body="$(cat "$body_file")"
    normalized_body="$(printf '%s' "$raw_body" | normalize_json_body)"
    printf -v "$out_var" '%s' "$normalized_body"
  fi
  rm -f "$body_file" "$error_file"
  [[ "$RUNTIME_HTTP_STATUS" == "200" ]]
}

runtime_post_capture() {
  local out_var="$1"
  local path="$2"
  local body="$3"
  local body_file
  local error_file
  local status
  local rc
  local raw_body
  local normalized_body
  body_file="$(mktemp "$TMP_DIR/http_body.XXXXXX")"
  error_file="$(mktemp "$TMP_DIR/http_error.XXXXXX")"
  status="$(curl -sS -o "$body_file" -w "%{http_code}" --connect-timeout 1 --max-time 8 \
    "http://127.0.0.1:$HOST_PORT$path" \
    -H "content-type: application/json" \
    -H "X-Ai-Debug-Token: $SESSION_TOKEN" \
    -d "$body" 2>"$error_file")"
  rc=$?
  if [[ "$rc" -ne 0 ]]; then
    RUNTIME_HTTP_STATUS="000"
    printf -v "$out_var" '%s' "$("$JQ_BIN" -nc --arg message "$(cat "$error_file")" '{error:{code:"HTTP_CLIENT_ERROR",message:$message,recoverable:true}}')"
  else
    RUNTIME_HTTP_STATUS="$status"
    raw_body="$(cat "$body_file")"
    normalized_body="$(printf '%s' "$raw_body" | normalize_json_body)"
    printf -v "$out_var" '%s' "$normalized_body"
  fi
  rm -f "$body_file" "$error_file"
  [[ "$RUNTIME_HTTP_STATUS" == "200" ]]
}

post_tool() {
  local section="$1"
  local tool="$2"
  local path="$3"
  local body="$4"
  local out_var="$5"
  local tool_response
  if runtime_post_capture tool_response "$path" "$body"; then
    tool_response="$(printf '%s' "$tool_response" | normalize_json_body)"
    add_evidence "$section" "toolResponse" "$tool" "$tool succeeded" "$tool_response"
    printf -v "$out_var" '%s' "$tool_response"
    return 0
  fi
  tool_response="$(printf '%s' "$tool_response" | normalize_json_body)"
  add_evidence "$section" "toolResponse" "$tool" "$tool failed with HTTP $RUNTIME_HTTP_STATUS" "$tool_response"
  printf -v "$out_var" '%s' "$tool_response"
  return 1
}

sha256_file() {
  shasum -a 256 "$1" | awk '{print $1}'
}

ensure_fixtures() {
  mkdir -p "$FIXTURE_HOST_DIR"
  if [[ ! -f "$AUDIO_FIXTURE" ]]; then
    "$PYTHON_BIN" - "$AUDIO_FIXTURE" <<'PY'
import math
import struct
import sys
import wave

path = sys.argv[1]
sample_rate = 8000
frames = []
for index in range(256):
    sample = int(12000 * math.sin(2 * math.pi * 440 * index / sample_rate))
    frames.append(struct.pack("<h", sample))
with wave.open(path, "wb") as wav:
    wav.setnchannels(1)
    wav.setsampwidth(2)
    wav.setframerate(sample_rate)
    wav.writeframes(b"".join(frames))
PY
  fi
  if [[ ! -f "$CAMERA_FIXTURE" ]]; then
    "$PYTHON_BIN" - "$CAMERA_FIXTURE" <<'PY'
import sys

path = sys.argv[1]
width = 4
height = 4
y = bytes((64 + index * 3) % 256 for index in range(width * height))
vu = bytes([128, 128] * (width * height // 4))
with open(path, "wb") as output:
    output.write(y + vu)
PY
  fi
}

stage_fixture() {
  local host_path="$1"
  local device_name="$2"
  local out_var="$3"
  local staged_device_path="$DEVICE_FIXTURE_DIR/$device_name"
  run_adb shell mkdir -p "$DEVICE_FIXTURE_DIR" >/dev/null || return 1
  run_adb push "$host_path" "$staged_device_path" >/dev/null || return 1
  run_adb shell chmod 644 "$staged_device_path" >/dev/null || true
  PUSHED_DEVICE_PATHS+=("$staged_device_path")
  printf -v "$out_var" '%s' "$staged_device_path"
}

register_fixture() {
  local section="$1"
  local fixture_id="$2"
  local host_path="$3"
  local mime_type="$4"
  local metadata="$5"
  local out_var="$6"
  local device_path
  local sha
  local body
  local response
  stage_fixture "$host_path" "$(basename "$host_path")" device_path || {
    add_error "$section" "Failed to stage fixture $host_path"
    return 1
  }
  sha="$(sha256_file "$host_path")"
  body="$("$JQ_BIN" -nc \
    --arg fixtureId "$fixture_id" \
    --arg devicePath "$device_path" \
    --arg sha256 "$sha" \
    --arg mimeType "$mime_type" \
    --argjson metadata "$metadata" \
    '{fixtureId:$fixtureId, devicePath:$devicePath, sha256:$sha256, mimeType:$mimeType, metadata:$metadata, tags:["spec015","media-verification"]}')"
  post_tool "$section" "media.fixture.register" "/media/fixture/register" "$body" response || return 1
  REGISTERED_FIXTURE_IDS+=("$fixture_id")
  "$JQ_BIN" -nc \
    --arg fixtureId "$fixture_id" \
    --arg devicePath "$device_path" \
    --arg sha256 "$sha" \
    --arg mimeType "$mime_type" \
    --argjson registration "$response" \
    '{
      fixtureId:$fixtureId,
      devicePath:$devicePath,
      sha256:$sha256,
      mimeType:$mimeType,
      registration:$registration
    }' >>"$FIXTURES_JSONL"
  printf -v "$out_var" '%s' "$response"
}

delete_fixture() {
  local section="$1"
  local fixture_id="$2"
  local response
  local body
  body="$("$JQ_BIN" -nc --arg fixtureId "$fixture_id" '{fixtureId:$fixtureId}')"
  if post_tool "$section" "media.fixture.delete" "/media/fixture/delete" "$body" response; then
    return 0
  fi
  return 1
}

launch_app() {
  run_adb shell am start -n "$ACTIVITY_NAME" "$@" >/dev/null
}

wait_for_runtime() {
  local response
  local token
  local attempt
  for attempt in $(seq 1 50); do
    if http_get_capture response "/runtime/ping"; then
      response="$(printf '%s' "$response" | normalize_json_body)"
      token="$("$JQ_BIN" -r '.sessionToken // empty' <<<"$response")"
      if [[ -n "$token" ]]; then
        SESSION_TOKEN="$token"
        PING_BODY="$response"
        return 0
      fi
    fi
    sleep 0.2
  done
  return 1
}

setup_device_runtime() {
  log "Building sample debug APK"
  "$GRADLE_BIN" -p "$ROOT_DIR" :sample-app:assembleDebug >/dev/null || {
    add_error "foundation" "Gradle assembleDebug failed"
    return 1
  }
  log "Installing sample app"
  run_adb install -r "$APK_PATH" >/dev/null || {
    add_error "foundation" "ADB install failed for $APK_PATH"
    return 1
  }
  run_adb shell pm grant "$PACKAGE_NAME" android.permission.RECORD_AUDIO >/dev/null 2>&1 || true
  run_adb forward --remove "tcp:$HOST_PORT" >/dev/null 2>&1 || true
  run_adb forward "tcp:$HOST_PORT" "tcp:$DEVICE_PORT" >/dev/null || {
    add_error "foundation" "ADB forward tcp:$HOST_PORT tcp:$DEVICE_PORT failed"
    return 1
  }
  run_adb shell am force-stop "$PACKAGE_NAME" >/dev/null 2>&1 || true
  launch_app >/dev/null || {
    add_error "foundation" "Sample app launch failed"
    return 1
  }
  wait_for_runtime || {
    add_error "foundation" "runtime.ping did not return a session token"
    return 1
  }
  add_evidence "foundation" "runtime" "runtime.ping" "runtime is reachable" "$PING_BODY"
}

run_foundation() {
  local caps
  local targets
  local audio_register
  local camera_register
  local fixture_list
  local missing_response
  local audit
  local body

  require_command "$JQ_BIN" || return 1
  require_command "$PYTHON_BIN" || return 1
  require_command "$ADB_BIN" || return 1
  require_command "$GRADLE_BIN" || return 1
  ensure_fixtures || {
    add_error "foundation" "Failed to create deterministic fixtures"
    return 1
  }
  setup_device_runtime || return 1

  post_tool "foundation" "media.capabilities" "/media/capabilities" "{}" caps || return 1
  "$JQ_BIN" -e '
    (.fixtureMimeTypes | index("audio/wav")) and
    (.fixtureMimeTypes | index("application/x-nv21")) and
    (.targetKinds | index("AUDIO_RECORD_READ")) and
    (.targetKinds | index("CAMERA_X_ANALYZER")) and
    (.targetKinds | index("MLKIT_INPUT_IMAGE_FACTORY"))
  ' <<<"$caps" >/dev/null || {
    add_error "foundation" "media.capabilities is missing required MIME types or target kinds"
    return 1
  }

  post_tool "foundation" "media.targets.list" "/media/targets/list" "{}" targets || return 1
  "$JQ_BIN" -e '.targets | type == "array"' <<<"$targets" >/dev/null || {
    add_error "foundation" "media.targets.list did not return a targets array"
    return 1
  }

  register_fixture "foundation" "foundation-audio-$RUN_ID" "$AUDIO_FIXTURE" "audio/wav" '{"sampleRate":8000,"channels":1,"encoding":"PCM_16BIT"}' audio_register || return 1
  register_fixture "foundation" "foundation-camera-$RUN_ID" "$CAMERA_FIXTURE" "application/x-nv21" '{"width":4,"height":4,"rotationDegrees":0,"format":"NV21"}' camera_register || return 1
  post_tool "foundation" "media.fixture.list" "/media/fixture/list" '{"query":"foundation-","limit":20}' fixture_list || return 1
  "$JQ_BIN" -e '.fixtures | length >= 2' <<<"$fixture_list" >/dev/null || {
    add_error "foundation" "media.fixture.list did not include the foundation fixtures"
    return 1
  }

  body="$("$JQ_BIN" -nc --arg targetId "missing-audio-target" --arg fixtureId "foundation-audio-$RUN_ID" '{targetId:$targetId, fixtureId:$fixtureId}')"
  if runtime_post_capture missing_response "/media/audio/inject" "$body"; then
    missing_response="$(printf '%s' "$missing_response" | normalize_json_body)"
    add_evidence "foundation" "toolResponse" "media.audio.inject" "missing audio target unexpectedly succeeded" "$missing_response"
    add_error "foundation" "media.audio.inject succeeded for a missing target"
    return 1
  fi
  missing_response="$(printf '%s' "$missing_response" | normalize_json_body)"
  add_evidence "foundation" "toolResponse" "media.audio.inject" "missing audio target returned structured error" "$missing_response"
  "$JQ_BIN" -e '.error.code == "RUNTIME_ERROR"' <<<"$missing_response" >/dev/null || {
    add_error "foundation" "missing audio target did not return a structured error"
    return 1
  }

  body="$("$JQ_BIN" -nc --arg targetId "missing-camera-target" --arg fixtureId "foundation-camera-$RUN_ID" '{targetId:$targetId, fixtureId:$fixtureId}')"
  if runtime_post_capture missing_response "/media/camera/injectFrames" "$body"; then
    missing_response="$(printf '%s' "$missing_response" | normalize_json_body)"
    add_evidence "foundation" "toolResponse" "media.camera.injectFrames" "missing camera target unexpectedly succeeded" "$missing_response"
    add_error "foundation" "media.camera.injectFrames succeeded for a missing target"
    return 1
  fi
  missing_response="$(printf '%s' "$missing_response" | normalize_json_body)"
  add_evidence "foundation" "toolResponse" "media.camera.injectFrames" "missing camera target returned structured error" "$missing_response"
  "$JQ_BIN" -e '.error.code == "RUNTIME_ERROR"' <<<"$missing_response" >/dev/null || {
    add_error "foundation" "missing camera target did not return a structured error"
    return 1
  }

  delete_fixture "foundation" "foundation-audio-$RUN_ID" || return 1
  delete_fixture "foundation" "foundation-camera-$RUN_ID" || return 1

  post_tool "foundation" "audit.history" "/audit/history" "{}" audit || return 1
  "$JQ_BIN" -e '.events | any(.tool == "media.capabilities") and any(.tool == "media.fixture.register")' <<<"$audit" >/dev/null || {
    add_error "foundation" "audit.history did not contain expected media events"
    return 1
  }
}

poll_target() {
  local section="$1"
  local kind="$2"
  local out_var="$3"
  local response="{}"
  local found_target_id=""
  local body
  local attempt
  body="$("$JQ_BIN" -nc --arg kind "$kind" '{kind:$kind, limit:20}')"
  for attempt in $(seq 1 40); do
    post_tool "$section" "media.targets.list" "/media/targets/list" "$body" response >/dev/null || return 1
    found_target_id="$("$JQ_BIN" -r '.targets[0].targetId // empty' <<<"$response")"
    if [[ -n "$found_target_id" ]]; then
      printf -v "$out_var" '%s' "$found_target_id"
      return 0
    fi
    sleep 0.25
  done
  add_error "$section" "No $kind target was discovered after polling"
  add_evidence "$section" "toolResponse" "media.targets.list" "final target poll for $kind" "$response"
  return 2
}

run_audio() {
  local target_id
  local register_response
  local inject_response
  local assert_response
  local history_response
  local clear_response
  local fixture_id="audio-015-$RUN_ID"
  local body
  local rule_id

  if [[ -z "$SESSION_TOKEN" ]]; then
    add_error "audio" "runtime session is unavailable because foundation did not complete"
    return 2
  fi
  launch_app --ez runAudioMediaDogfood true --ez renderMediaDogfoodState true >/dev/null || {
    add_error "audio" "Failed to launch audio dogfood trigger"
    return 1
  }
  poll_target "audio" "AUDIO_RECORD_READ" target_id || return $?
  add_evidence "audio" "target" "media.targets.list" "discovered AUDIO_RECORD_READ target" "$("$JQ_BIN" -nc --arg targetId "$target_id" '{targetId:$targetId}')"

  register_fixture "audio" "$fixture_id" "$AUDIO_FIXTURE" "audio/wav" '{"sampleRate":8000,"channels":1,"encoding":"PCM_16BIT"}' register_response || return 1
  body="$("$JQ_BIN" -nc --arg targetId "$target_id" --arg fixtureId "$fixture_id" '{targetId:$targetId, fixtureId:$fixtureId, times:1, behavior:{eof:"short_read"}}')"
  post_tool "audio" "media.audio.inject" "/media/audio/inject" "$body" inject_response || return 1
  rule_id="$("$JQ_BIN" -r '.ruleId // empty' <<<"$inject_response")"

  launch_app --ez runAudioMediaDogfood true --ez renderMediaDogfoodState true >/dev/null || {
    add_error "audio" "Failed to relaunch audio dogfood trigger after injection"
    return 1
  }
  body="$("$JQ_BIN" -nc --arg targetId "$target_id" --arg fixtureId "$fixture_id" '{targetId:$targetId, fixtureId:$fixtureId, minBytes:1, minReads:1, timeoutMs:5000, pollIntervalMs:100}')"
  post_tool "audio" "media.audio.assertConsumed" "/media/audio/assertConsumed" "$body" assert_response || return 1
  "$JQ_BIN" -e '.passed == true and .consumedBytes >= 1 and .readCount >= 1' <<<"$assert_response" >/dev/null || {
    add_error "audio" "media.audio.assertConsumed did not pass"
    return 1
  }

  body="$("$JQ_BIN" -nc --arg targetId "$target_id" --arg fixtureId "$fixture_id" '{targetId:$targetId, fixtureId:$fixtureId, limit:20}')"
  post_tool "audio" "media.audio.history" "/media/audio/history" "$body" history_response || return 1
  "$JQ_BIN" -e '.records | length >= 1' <<<"$history_response" >/dev/null || {
    add_error "audio" "media.audio.history did not contain consumed fixture records"
    return 1
  }

  if [[ -n "$rule_id" ]]; then
    body="$("$JQ_BIN" -nc --arg ruleId "$rule_id" '{ruleIds:[$ruleId]}')"
  else
    body="$("$JQ_BIN" -nc --arg targetId "$target_id" '{targetId:$targetId}')"
  fi
  post_tool "audio" "media.audio.clear" "/media/audio/clear" "$body" clear_response || return 1
}

run_camera() {
  local target_id
  local register_response
  local inject_response
  local snapshot_before
  local snapshot_after
  local assert_response
  local history_response
  local clear_response
  local fixture_id="camera-015-$RUN_ID"
  local body
  local rule_id

  if [[ -z "$SESSION_TOKEN" ]]; then
    add_error "camera" "runtime session is unavailable because foundation did not complete"
    return 2
  fi
  launch_app --ez runCameraMediaDogfood true --ez renderMediaDogfoodState true >/dev/null || {
    add_error "camera" "Failed to launch camera dogfood trigger"
    return 1
  }
  poll_target "camera" "MLKIT_INPUT_IMAGE_FACTORY" target_id
  local poll_rc=$?
  if [[ "$poll_rc" -ne 0 ]]; then
    if [[ "$poll_rc" -eq 2 ]]; then
      poll_target "camera" "CAMERA_X_ANALYZER" target_id || return $?
    else
      return "$poll_rc"
    fi
  fi
  add_evidence "camera" "target" "media.targets.list" "discovered camera media target" "$("$JQ_BIN" -nc --arg targetId "$target_id" '{targetId:$targetId}')"

  register_fixture "camera" "$fixture_id" "$CAMERA_FIXTURE" "application/x-nv21" '{"width":4,"height":4,"rotationDegrees":0,"format":"NV21"}' register_response || return 1
  body="$("$JQ_BIN" -nc --arg targetId "$target_id" --arg fixtureId "$fixture_id" '{targetId:$targetId, fixtureId:$fixtureId, mode:"mlkit_input_image", times:1}')"
  post_tool "camera" "media.camera.injectFrames" "/media/camera/injectFrames" "$body" inject_response || return 1
  rule_id="$("$JQ_BIN" -r '.ruleId // empty' <<<"$inject_response")"

  body="$("$JQ_BIN" -nc --arg targetId "$target_id" '{targetId:$targetId}')"
  post_tool "camera" "media.camera.snapshot" "/media/camera/snapshot" "$body" snapshot_before || return 1

  launch_app --ez runCameraMediaDogfood true --ez renderMediaDogfoodState true >/dev/null || {
    add_error "camera" "Failed to relaunch camera dogfood trigger after injection"
    return 1
  }
  body="$("$JQ_BIN" -nc --arg targetId "$target_id" --arg fixtureId "$fixture_id" '{targetId:$targetId, fixtureId:$fixtureId, minFrames:1, timeoutMs:5000, pollIntervalMs:100}')"
  post_tool "camera" "media.camera.assertConsumed" "/media/camera/assertConsumed" "$body" assert_response || return 1
  "$JQ_BIN" -e '.passed == true and .consumedFrames >= 1' <<<"$assert_response" >/dev/null || {
    add_error "camera" "media.camera.assertConsumed did not pass"
    return 1
  }

  body="$("$JQ_BIN" -nc --arg targetId "$target_id" --arg fixtureId "$fixture_id" '{targetId:$targetId, fixtureId:$fixtureId, limit:20}')"
  post_tool "camera" "media.camera.history" "/media/camera/history" "$body" history_response || return 1
  "$JQ_BIN" -e '.records | length >= 1' <<<"$history_response" >/dev/null || {
    add_error "camera" "media.camera.history did not contain consumed fixture records"
    return 1
  }

  body="$("$JQ_BIN" -nc --arg targetId "$target_id" '{targetId:$targetId}')"
  post_tool "camera" "media.camera.snapshot" "/media/camera/snapshot" "$body" snapshot_after || return 1
  "$JQ_BIN" -e '.targets | length >= 1' <<<"$snapshot_after" >/dev/null || {
    add_error "camera" "media.camera.snapshot did not contain target evidence"
    return 1
  }

  if [[ -n "$rule_id" ]]; then
    body="$("$JQ_BIN" -nc --arg ruleId "$rule_id" '{ruleIds:[$ruleId]}')"
  else
    body="$("$JQ_BIN" -nc --arg targetId "$target_id" '{targetId:$targetId}')"
  fi
  post_tool "camera" "media.camera.clear" "/media/camera/clear" "$body" clear_response || return 1
}

run_build_safety() {
  local verify_log="$TMP_DIR/verify-instrumentation.log"
  local release_log="$TMP_DIR/release-safety.log"
  local transform_dir="$ROOT_DIR/sample-app/build/intermediates/classes/debug/transformDebugClassesWithAsm"
  if "$GRADLE_BIN" -p "$ROOT_DIR" :sample-app:verifyAiDebugDebugInstrumentation >"$verify_log" 2>&1; then
    add_evidence "buildSafety" "gradle" ":sample-app:verifyAiDebugDebugInstrumentation" "debug instrumentation verification passed" "$("$JQ_BIN" -nc --arg report "$INSTRUMENTATION_REPORT" --arg log "$verify_log" '{report:$report, log:$log}')"
  else
    add_evidence "buildSafety" "gradle" ":sample-app:verifyAiDebugDebugInstrumentation" "debug instrumentation verification failed" "$("$JQ_BIN" -nc --arg log "$verify_log" --arg output "$(tail -n 80 "$verify_log" 2>/dev/null)" '{log:$log, output:$output}')"
    add_error "buildSafety" ":sample-app:verifyAiDebugDebugInstrumentation failed"
    return 1
  fi
  if grep -R -a -q "audioRecordRead" "$transform_dir" &&
    grep -R -a -q "audio:audiorecord:read" "$transform_dir"; then
    add_evidence "buildSafety" "classScan" "debug.media.audio" "debug artifact contains AudioRecord media bridge evidence" "$("$JQ_BIN" -nc --arg transformDir "$transform_dir" '{transformDir:$transformDir, bridge:"audioRecordRead"}')"
  else
    add_error "buildSafety" "debug artifact is missing AudioRecord media bridge evidence"
    return 1
  fi
  if grep -R -a -q "inputImageFromByteArray" "$transform_dir" &&
    grep -R -a -q "camera:mlkit:inputImage" "$transform_dir"; then
    add_evidence "buildSafety" "classScan" "debug.media.camera" "debug artifact contains ML Kit InputImage media bridge evidence" "$("$JQ_BIN" -nc --arg transformDir "$transform_dir" '{transformDir:$transformDir, bridge:"inputImageFromByteArray"}')"
  else
    add_error "buildSafety" "debug artifact is missing ML Kit InputImage media bridge evidence"
    return 1
  fi

  if "$GRADLE_BIN" -p "$ROOT_DIR" :sample-app:checkAiDebugReleaseSafety >"$release_log" 2>&1; then
    add_evidence "buildSafety" "gradle" ":sample-app:checkAiDebugReleaseSafety" "release safety verification passed" "$("$JQ_BIN" -nc --arg report "$RELEASE_SAFETY_REPORT" --arg log "$release_log" '{report:$report, log:$log}')"
  else
    add_evidence "buildSafety" "gradle" ":sample-app:checkAiDebugReleaseSafety" "release safety verification failed" "$("$JQ_BIN" -nc --arg log "$release_log" --arg output "$(tail -n 80 "$release_log" 2>/dev/null)" '{log:$log, output:$output}')"
    add_error "buildSafety" ":sample-app:checkAiDebugReleaseSafety failed"
    return 1
  fi
}

run_cleanup_section() {
  local started
  local finished
  local status="passed"
  local response
  local fixture_id
  local device_path
  started="$(now_ms)"
  CURRENT_SECTION="cleanup"
  if [[ -n "$SESSION_TOKEN" ]]; then
    if runtime_post_capture response "/media/audio/clear" "{}"; then
      add_evidence "cleanup" "toolResponse" "media.audio.clear" "audio rules cleared" "$response"
    else
      add_evidence "cleanup" "toolResponse" "media.audio.clear" "audio cleanup failed with HTTP $RUNTIME_HTTP_STATUS" "$response"
      add_error "cleanup" "media.audio.clear cleanup failed"
      status="failed"
    fi
    if runtime_post_capture response "/media/camera/clear" "{}"; then
      add_evidence "cleanup" "toolResponse" "media.camera.clear" "camera rules cleared" "$response"
    else
      add_evidence "cleanup" "toolResponse" "media.camera.clear" "camera cleanup failed with HTTP $RUNTIME_HTTP_STATUS" "$response"
      add_error "cleanup" "media.camera.clear cleanup failed"
      status="failed"
    fi
    for fixture_id in "${REGISTERED_FIXTURE_IDS[@]+"${REGISTERED_FIXTURE_IDS[@]}"}"; do
      if runtime_post_capture response "/media/fixture/delete" "$("$JQ_BIN" -nc --arg fixtureId "$fixture_id" '{fixtureId:$fixtureId}')"; then
        add_evidence "cleanup" "toolResponse" "media.fixture.delete" "fixture cleanup attempted for $fixture_id" "$response"
      else
        add_evidence "cleanup" "toolResponse" "media.fixture.delete" "fixture cleanup failed for $fixture_id" "$response"
        add_error "cleanup" "media.fixture.delete cleanup failed for $fixture_id"
        status="failed"
      fi
    done
  fi
  for device_path in "${PUSHED_DEVICE_PATHS[@]+"${PUSHED_DEVICE_PATHS[@]}"}"; do
    if run_adb shell rm -f "$device_path" >/dev/null 2>&1; then
      "$JQ_BIN" -nc --arg path "$device_path" '{"tool":"adb.rm","status":"passed","path":$path}' >>"$CLEANUP_JSONL"
    else
      "$JQ_BIN" -nc --arg path "$device_path" '{"tool":"adb.rm","status":"failed","path":$path}' >>"$CLEANUP_JSONL"
      add_error "cleanup" "failed to remove pushed fixture $device_path"
      status="failed"
    fi
  done
  run_adb shell rmdir "$DEVICE_FIXTURE_DIR" >/dev/null 2>&1 || true
  run_adb forward --remove "tcp:$HOST_PORT" >/dev/null 2>&1 || true
  if [[ "$KEEP_APP_DATA" -ne 1 ]]; then
    run_adb shell am force-stop "$PACKAGE_NAME" >/dev/null 2>&1 || true
  fi
  finished="$(now_ms)"
  write_section "cleanup" "$status" "true" "$started" "$((finished - started))"
  CURRENT_SECTION=""
}

selected_modes_json() {
  if [[ "${#SELECTED_MODES[@]}" -eq 0 ]]; then
    printf '[]'
  else
    printf '%s\n' "${SELECTED_MODES[@]}" | "$JQ_BIN" -R . | "$JQ_BIN" -s .
  fi
}

device_json() {
  local serial
  local model
  local api
  serial="$(run_adb get-serialno 2>/dev/null | tr -d '\r' || true)"
  model="$(run_adb shell getprop ro.product.model 2>/dev/null | tr -d '\r' || true)"
  api="$(run_adb shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || true)"
  "$JQ_BIN" -nc \
    --arg serial "$serial" \
    --arg model "$model" \
    --arg apiLevel "$api" \
    --argjson hostPort "$HOST_PORT" \
    --argjson devicePort "$DEVICE_PORT" \
    '{serial:$serial, model:$model, apiLevel:($apiLevel | tonumber? // null), hostPort:$hostPort, devicePort:$devicePort}'
}

finalize_report() {
  local finished
  local sections
  local fixtures
  local cleanup
  local status
  local selected
  local device
  local runtime
  local artifacts
  mkdir -p "$REPORT_ROOT"
  finished="$(now_ms)"
  sections="$("$JQ_BIN" -s '.' \
    "$(section_file foundation)" \
    "$(section_file audio)" \
    "$(section_file camera)" \
    "$(section_file buildSafety)" \
    "$(section_file cleanup)")"
  status="$("$JQ_BIN" -r 'if any(.status == "failed") then "failed" elif any(.required == true and .status == "blocked") then "blocked" else "passed" end' <<<"$sections")"
  fixtures="$(json_array_from_jsonl "$FIXTURES_JSONL")"
  cleanup="$(cat "$(section_file cleanup)")"
  selected="$(selected_modes_json)"
  device="$(device_json)"
  runtime="$("$JQ_BIN" -nc --arg packageName "$PACKAGE_NAME" --arg activity "$ACTIVITY_NAME" --argjson ping "$PING_BODY" '{packageName:$packageName, activity:$activity, ping:$ping, sessionId:($ping.sessionId // null), runtimeVersion:($ping.runtimeVersion // null)}')"
  artifacts="$("$JQ_BIN" -nc \
    --arg instrumentationReport "$INSTRUMENTATION_REPORT" \
    --arg releaseSafetyReport "$RELEASE_SAFETY_REPORT" \
    '{instrumentationReport:$instrumentationReport, releaseSafetyReport:$releaseSafetyReport, scenarioReport:null}')"
  "$JQ_BIN" -n \
    --arg runId "$RUN_ID" \
    --arg status "$status" \
    --argjson selectedModes "$selected" \
    --argjson startedAtEpochMs "$STARTED_AT_MS" \
    --argjson finishedAtEpochMs "$finished" \
    --argjson device "$device" \
    --argjson runtime "$runtime" \
    --argjson sections "$sections" \
    --argjson fixtures "$fixtures" \
    --argjson artifacts "$artifacts" \
    --argjson cleanup "$cleanup" \
    '{
      runId:$runId,
      status:$status,
      selectedModes:$selectedModes,
      startedAtEpochMs:$startedAtEpochMs,
      finishedAtEpochMs:$finishedAtEpochMs,
      durationMs:($finishedAtEpochMs - $startedAtEpochMs),
      device:$device,
      runtime:$runtime,
      sections:$sections,
      fixtures:$fixtures,
      artifacts:$artifacts,
      cleanup:$cleanup
    }' >"$REPORT_PATH"
  log "Report written: $REPORT_PATH"
  case "$status" in
    passed) return 0 ;;
    blocked) return 2 ;;
    *) return 1 ;;
  esac
}

on_exit() {
  local rc="$1"
  if [[ "$FINALIZED" -eq 0 ]]; then
    mkdir -p "$SECTION_DIR"
    for section in foundation audio camera buildSafety; do
      if [[ ! -f "$(section_file "$section")" ]]; then
        if [[ -n "$CURRENT_SECTION" && "$section" == "$CURRENT_SECTION" ]]; then
          add_error "$section" "script exited before section completed"
          write_section "$section" "failed" "true" "$(now_ms)" 0
        else
          skip_section "$section" "false"
        fi
      fi
    done
    if [[ ! -f "$(section_file cleanup)" ]]; then
      run_cleanup_section
    fi
    finalize_report >/dev/null || true
  fi
  exit "$rc"
}

usage() {
  cat <<'USAGE'
Usage: scripts/spec015-media-input-verification.sh [--foundation] [--audio] [--camera] [--keep-app-data]

No mode flag runs foundation, audio, camera, instrumentation verification, and release safety.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --foundation)
      MODE_SPECIFIED=1
      RUN_FOUNDATION=1
      ;;
    --audio)
      MODE_SPECIFIED=1
      RUN_FOUNDATION=1
      RUN_AUDIO=1
      ;;
    --camera)
      MODE_SPECIFIED=1
      RUN_FOUNDATION=1
      RUN_CAMERA=1
      ;;
    --keep-app-data)
      KEEP_APP_DATA=1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage >&2
      exit 1
      ;;
  esac
  shift
done

if [[ "$MODE_SPECIFIED" -eq 0 ]]; then
  RUN_FOUNDATION=1
  RUN_AUDIO=1
  RUN_CAMERA=1
  RUN_BUILD_SAFETY=1
fi

[[ "$RUN_FOUNDATION" -eq 1 ]] && SELECTED_MODES+=("foundation")
[[ "$RUN_AUDIO" -eq 1 ]] && SELECTED_MODES+=("audio")
[[ "$RUN_CAMERA" -eq 1 ]] && SELECTED_MODES+=("camera")
[[ "$RUN_BUILD_SAFETY" -eq 1 ]] && SELECTED_MODES+=("buildSafety")

mkdir -p "$SECTION_DIR" "$REPORT_ROOT" "$FIXTURE_HOST_DIR"
: >"$FIXTURES_JSONL"
: >"$CLEANUP_JSONL"
STARTED_AT_MS="$(now_ms)"
trap 'on_exit $?' EXIT

if [[ "$RUN_FOUNDATION" -eq 1 ]]; then
  run_section "foundation" "true" run_foundation
else
  skip_section "foundation" "false"
fi

if [[ "$RUN_AUDIO" -eq 1 ]]; then
  run_section "audio" "true" run_audio
else
  skip_section "audio" "false"
fi

if [[ "$RUN_CAMERA" -eq 1 ]]; then
  run_section "camera" "true" run_camera
else
  skip_section "camera" "false"
fi

if [[ "$RUN_BUILD_SAFETY" -eq 1 ]]; then
  run_section "buildSafety" "true" run_build_safety
else
  skip_section "buildSafety" "false"
fi

run_cleanup_section
finalize_report
REPORT_RC=$?
FINALIZED=1
exit "$REPORT_RC"
