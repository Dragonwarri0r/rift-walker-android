#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_FILE="$ROOT_DIR/sample-app/build.gradle.kts"
BACKUP_FILE="$(mktemp)"
LOG_FILE="$(mktemp)"

cleanup() {
  cp "$BACKUP_FILE" "$BUILD_FILE"
  rm -f "$ROOT_DIR/sample-app/build/ai-debug/release-safety-report.json"
  rm -f "$BACKUP_FILE" "$LOG_FILE"
}
trap cleanup EXIT

cp "$BUILD_FILE" "$BACKUP_FILE"

cat >> "$BUILD_FILE" <<'GRADLE'

dependencies {
    releaseImplementation(project(":ai-debug-runtime"))
}
GRADLE

set +e
"$ROOT_DIR/gradlew" -p "$ROOT_DIR" :sample-app:checkAiDebugReleaseSafety --rerun-tasks >"$LOG_FILE" 2>&1
STATUS=$?
set -e

if [[ "$STATUS" -eq 0 ]]; then
  cat "$LOG_FILE"
  echo "Expected checkAiDebugReleaseSafety to fail when ai-debug-runtime leaks into release." >&2
  exit 1
fi

if ! grep -q "AI debug runtime leaked into release classpath" "$LOG_FILE"; then
  cat "$LOG_FILE"
  echo "Release safety failed for an unexpected reason." >&2
  exit 1
fi

echo "Negative release leak check passed."
