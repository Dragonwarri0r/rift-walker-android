# Implementation Plan: Media Input Verification Suite

## Goal

Build a connected-device verification suite that proves the implemented `013-audio-input-control`
and `014-camera-input-control` capabilities work beyond the tool layer. The suite should produce
clear evidence for target discovery, fixture staging, injection, app consumption, assertions,
cleanup, and release safety.

This spec is a verification layer. It should only add sample triggers, fixtures, scripts, reports,
and optional daemon scenarios. Any missing audio or camera injection behavior remains owned by
`013-audio-input-control` or `014-camera-input-control`.

## Current Gap

The existing media foundation can expose `media.*` tools and validate request shapes. Some 013/014
runtime and rewrite code is already present, but the current sample app does not yet provide a
triggerable end-to-end path that an agent can drive from ADB to prove fixture consumption on a real
device.

015 closes that gap by separating four outcomes:

- `passed`: verified on the connected device with app consumption evidence.
- `failed`: verification ran and a required assertion failed.
- `blocked`: verification was requested, but a prerequisite was unavailable, such as no discovered
  media target.
- `skipped`: verification was intentionally not requested by the selected script mode.

## Dependencies

- `012-media-input-foundation`: media capabilities, fixtures, target registry, audit, cleanup.
- `013-audio-input-control`: AudioRecord rewrite, audio bridge, `media.audio.*` behavior.
- `014-camera-input-control`: CameraX or ML Kit rewrite, camera bridge, `media.camera.*` behavior.
- `005-gradle-debug-integration`: debug-only instrumentation and release safety tasks.
- `011-dogfood-self-test`: connected-device script and scenario report patterns.

## Execution Graph

1. Freeze report contract and script modes.
2. Build the shell harness and JSON report writer.
3. Add deterministic host fixtures and device staging.
4. Add sample app media dogfood triggers.
5. Prove foundation media tools.
6. Prove audio target discovery and consumption.
7. Prove camera target discovery and consumption.
8. Run build safety checks and attach artifact paths.
9. Update testCase guidance so agents run 015 when validating 013/014.

Audio and camera verification can be implemented in parallel after the script foundation and fixtures
exist. Build safety can run in parallel with device verification once sample triggers compile.

## Deliverables

### Script

Add `scripts/spec015-media-input-verification.sh` with these modes:

```bash
scripts/spec015-media-input-verification.sh --foundation
scripts/spec015-media-input-verification.sh --audio
scripts/spec015-media-input-verification.sh --camera
scripts/spec015-media-input-verification.sh
```

Default mode runs foundation, audio, camera, instrumentation verification, and release safety. The
script must be idempotent and must run cleanup on exit.

### Report

Write a JSON report under `build/ai-debug/media-verification/` for every run. The report should
include:

- run id, timestamps, selected modes, and overall status,
- device serial, package, activity, host/device ports, and runtime ping response,
- section status and evidence for foundation, audio, camera, build safety, and cleanup,
- tool calls with redacted request/response bodies where useful,
- fixture ids, device paths, sha256 values, MIME types, and cleanup results,
- Gradle report paths for instrumentation and release safety.

### Fixtures

Add small deterministic host-side fixtures:

- WAV or raw PCM fixture for `media.audio.inject`,
- NV21 fixture for the first ML Kit-like `InputImage` verification path,
- PNG or JPEG fixture later when decoded bitmap/frame substitution is covered by 014.

The script should push fixtures to a device temp directory such as
`/data/local/tmp/ai-debug-media/015/` and register them with `media.fixture.register` using
`devicePath`, `sha256`, and `mimeType`. Large binary data must not be embedded in JSON.

### Sample App Triggers

Extend `sample-app` with intent extras that execute normal app/media code:

- `runAudioMediaDogfood=true`: drives a normal `AudioRecord.read(...)` code path.
- `runCameraMediaDogfood=true`: drives a normal CameraX analyzer or ML Kit-like `InputImage`
  factory path.
- `renderMediaDogfoodState=true`: optional visible state text for manual inspection.

The sample business path must not import AI debug media APIs. The only direct AI debug usage should
remain runtime startup and existing sample debug capability registration.

## Verification Design

### Foundation Section

Checks:

- `runtime.ping` returns a session token.
- `media.capabilities` includes audio and camera MIME types plus target kinds.
- `media.targets.list` returns the expected response shape.
- fixture register/list/delete works for both audio and image fixtures.
- missing-target injection returns a structured error.
- audit history includes media tool events.

Foundation failure should fail the run. Missing audio/camera call sites should not fail foundation.

### Audio Section

Checks:

- launch sample with `runAudioMediaDogfood=true`,
- poll `media.targets.list(kind=AUDIO_RECORD_READ)` until an audio target appears,
- register audio fixture,
- call `media.audio.inject`,
- trigger the audio path again,
- call `media.audio.assertConsumed` with `minBytes` and `minReads`,
- verify matching records in `media.audio.history`,
- clear audio rules and verify cleanup.

If no `AUDIO_RECORD_READ` target is discovered, mark audio as `blocked` with the latest target list,
instrumentation report path, and sample launch evidence.

### Camera Section

Checks:

- launch sample with `runCameraMediaDogfood=true`,
- poll `media.targets.list` for `CAMERA_X_ANALYZER` or `MLKIT_INPUT_IMAGE_FACTORY`,
- register image fixture,
- call `media.camera.injectFrames`,
- trigger the camera path again,
- call `media.camera.assertConsumed` with `minFrames`,
- verify matching records in `media.camera.history`,
- verify `media.camera.snapshot` includes target and rule evidence,
- clear camera rules and verify cleanup.

If no camera target is discovered, mark camera as `blocked` with the latest target list,
instrumentation report path, and sample launch evidence.

### Build Safety Section

Checks:

- `:sample-app:verifyAiDebugDebugInstrumentation`,
- `:sample-app:checkAiDebugReleaseSafety`,
- optional negative release leak checks for audio and camera bridge signatures.

Build safety failures should fail the run because 015 must not weaken the debug-only guarantee.

### Cleanup Section

Cleanup runs even after partial failures:

- clear audio rules,
- clear camera rules,
- delete registered fixtures,
- remove pushed device fixture files,
- remove ADB port forwarding,
- optionally force-stop the sample app.

Cleanup errors should be recorded. If cleanup cannot remove active media rules or fixtures, the
overall run should fail even if verification assertions passed.

## File Ownership

- `scripts/spec015-media-input-verification.sh`: primary connected-device runner.
- `sample-app/src/main/kotlin/com/riftwalker/sample/MainActivity.kt`: intent triggers and normal
  sample media paths.
- `sample-app/src/main/assets/ai-debug-fixtures/` or `testCase/fixtures/media/`: deterministic host
  fixtures or fixture generation inputs.
- `ai-debug-daemon/src/main/kotlin/com/riftwalker/aidebug/daemon/`: optional built-in media scenario
  support after the shell script is stable.
- `testCase/013-audio-input-control.md` and `testCase/014-camera-input-control.md`: agent guidance
  pointing to 015 for serious validation.

## Validation Commands

```bash
./gradlew :ai-debug-runtime:testDebugUnitTest :ai-debug-daemon:test
./gradlew :sample-app:assembleDebug :sample-app:verifyAiDebugDebugInstrumentation :sample-app:checkAiDebugReleaseSafety
bash scripts/spec015-media-input-verification.sh --foundation
bash scripts/spec015-media-input-verification.sh --audio
bash scripts/spec015-media-input-verification.sh --camera
bash scripts/spec015-media-input-verification.sh
```

## Risks And Mitigations

- Audio permissions can block target execution. The script should grant `RECORD_AUDIO` before audio
  triggers and record permission state in the report.
- CameraX can be hardware and lifecycle dependent. Prefer an ML Kit-like `InputImage` factory path
  for the first deterministic camera proof, then add CameraX analyzer coverage.
- A tool-layer-only pass can create false confidence. The report must keep foundation checks separate
  from end-to-end consumption checks.
- Device timing can be flaky. Poll target discovery and assertion endpoints with bounded timeouts and
  include the final response body when blocked or failed.
