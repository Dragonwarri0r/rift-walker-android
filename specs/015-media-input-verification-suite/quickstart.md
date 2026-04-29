# Quickstart: Media Input Verification Suite

This spec verifies the implemented capabilities from `013-audio-input-control` and
`014-camera-input-control`. It is intentionally report-driven so an AI agent can tell the
difference between tool-layer success and true end-to-end media fixture consumption.

## Baseline

```bash
./gradlew :ai-debug-runtime:testDebugUnitTest :ai-debug-daemon:test
./gradlew :sample-app:assembleDebug :sample-app:verifyAiDebugDebugInstrumentation :sample-app:checkAiDebugReleaseSafety
```

## Device Setup

```bash
adb devices -l
adb shell mkdir -p /data/local/tmp/ai-debug-media/015
```

## Run Foundation Only

```bash
scripts/spec015-media-input-verification.sh --foundation
```

This validates media capabilities, fixture staging, list/delete, structured errors, and cleanup.

## Run Audio Verification

```bash
AUDIO_FIXTURE=/absolute/path/fixtures/wake-word-yes.wav \
scripts/spec015-media-input-verification.sh --audio
```

Expected result after audio dogfood support is implemented:

- `AUDIO_RECORD_READ` target discovered.
- Audio fixture registered.
- `media.audio.inject` installs a rule.
- Sample audio trigger consumes bytes.
- `media.audio.assertConsumed.passed == true`.

If no audio target is discovered, the report should mark audio as `blocked`, not `passed`.

## Run Camera Verification

```bash
CAMERA_FIXTURE=/absolute/path/fixtures/vip-card-001.nv21 \
scripts/spec015-media-input-verification.sh --camera
```

Expected result after camera dogfood support is implemented:

- `CAMERA_X_ANALYZER` and/or `MLKIT_INPUT_IMAGE_FACTORY` target discovered.
- Camera fixture registered. The default deterministic fixture is NV21 because the current
  ML Kit-like verification path can substitute fixture-backed `InputImage` values from NV21.
- `media.camera.injectFrames` installs a rule.
- Sample camera trigger consumes at least one frame.
- `media.camera.assertConsumed.passed == true`.

If no camera target is discovered, the report should mark camera as `blocked`, not `passed`.

## Run Full Suite

```bash
scripts/spec015-media-input-verification.sh
```

Report output:

```text
build/ai-debug/media-verification/report_*.json
```

Non-selected sections should be `skipped`. Requested sections that cannot discover a target should
be `blocked` with evidence, not reported as `passed`.

## Agent Review Checklist

- Confirm report top-level `status`.
- Confirm each section status: foundation, audio, camera, buildSafety, cleanup.
- Confirm skipped and blocked sections are intentional and include evidence.
- Confirm target ids are stable between discovery and injection.
- Confirm history record ids match assertion record ids.
- Confirm cleanup removed fixture/rule state.
- Confirm release safety reports are attached.
