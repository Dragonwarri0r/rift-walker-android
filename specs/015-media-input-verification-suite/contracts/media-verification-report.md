# Contract: Media Input Verification Report

The media verification suite writes one JSON report per run under:

```text
build/ai-debug/media-verification/report_<timestamp-or-uuid>.json
```

## Script CLI

```bash
scripts/spec015-media-input-verification.sh [--foundation] [--audio] [--camera] [--keep-app-data]
```

Environment variables:

```bash
DEVICE_SERIAL=emulator-5554
HOST_PORT=37913
DEVICE_PORT=37913
REPORT_ROOT=build/ai-debug/media-verification
AUDIO_FIXTURE=/absolute/path/fixtures/wake-word-yes.wav
CAMERA_FIXTURE=/absolute/path/fixtures/vip-card-001.nv21
```

Mode behavior:

- No mode flag: run foundation, audio, camera, and build safety sections.
- `--foundation`: run only common media foundation checks.
- `--audio`: run foundation and audio sections.
- `--camera`: run foundation and camera sections.
- Non-selected sections must be marked `skipped`.
- Requested sections that cannot reach a prerequisite, such as target discovery, must be marked `blocked`.

## Report Shape

```json
{
  "runId": "media_run_001",
  "status": "passed|failed|blocked",
  "selectedModes": ["foundation", "audio", "camera", "buildSafety"],
  "startedAtEpochMs": 1777459000000,
  "finishedAtEpochMs": 1777459005000,
  "durationMs": 5000,
  "device": {
    "serial": "emulator-5554",
    "model": "Pixel_8",
    "apiLevel": 35,
    "hostPort": 37913,
    "devicePort": 37913
  },
  "runtime": {
    "packageName": "com.riftwalker.sample",
    "activity": "com.riftwalker.sample/.MainActivity",
    "sessionId": "session_...",
    "runtimeVersion": "0.1.0"
  },
  "sections": [
    {
      "name": "foundation",
      "status": "passed",
      "required": true,
      "startedAtEpochMs": 1777459000100,
      "durationMs": 300,
      "evidence": [
        {
          "kind": "toolResponse",
          "tool": "media.capabilities",
          "summary": "audio/wav,application/x-nv21 supported",
          "payload": {}
        }
      ],
      "errors": []
    },
    {
      "name": "audio",
      "status": "blocked",
      "required": true,
      "evidence": [
        {
          "kind": "toolResponse",
          "tool": "media.targets.list",
          "summary": "no AUDIO_RECORD_READ target discovered",
          "payload": {"targets": []}
        }
      ],
      "errors": ["Sample audio trigger did not hit an AudioRecord read target"]
    },
    {
      "name": "camera",
      "status": "skipped",
      "required": false,
      "evidence": [],
      "errors": []
    }
  ],
  "fixtures": [
    {
      "fixtureId": "audio-015",
      "devicePath": "/data/local/tmp/ai-debug-media/015/audio.wav",
      "sha256": "abc123...",
      "mimeType": "audio/wav",
      "sizeBytes": 1024
    },
    {
      "fixtureId": "camera-015",
      "devicePath": "/data/local/tmp/ai-debug-media/015/camera.nv21",
      "sha256": "def456...",
      "mimeType": "application/x-nv21",
      "sizeBytes": 24,
      "metadata": {
        "width": 4,
        "height": 4,
        "rotationDegrees": 0,
        "format": "NV21"
      }
    }
  ],
  "artifacts": {
    "instrumentationReport": "/abs/path/sample-app/build/ai-debug/instrumentation-debug-report.json",
    "releaseSafetyReport": "/abs/path/sample-app/build/ai-debug/release-safety-report.json",
    "scenarioReport": null
  },
  "cleanup": {
    "status": "passed",
    "results": [
      {"tool": "media.audio.clear", "status": "passed", "payload": {"cleared": 1}},
      {"tool": "media.fixture.delete", "status": "passed", "payload": {"deleted": true}}
    ]
  }
}
```

## Required Section Assertions

### Foundation

- `media.capabilities.fixtureMimeTypes` includes audio and image fixture MIME types.
- `media.capabilities.targetKinds` includes audio and camera target kinds.
- `media.fixture.register` verifies sha256 and size.
- `media.fixture.list` returns registered fixtures.
- `media.fixture.delete` removes registered fixtures.
- Missing target injection returns a structured runtime error, not a crash.

### Audio

- `media.targets.list(kind=AUDIO_RECORD_READ)` returns at least one target after the audio trigger.
- Target evidence includes `targetId`, `callSiteId`, `apiSignature`, `hitCount`, and observed metadata when available.
- `media.audio.inject` installs a rule with `ruleId` and `restoreToken`.
- `media.audio.assertConsumed` returns `passed=true`.
- `media.audio.history` contains matching record ids.
- `media.audio.clear` removes active audio rules.

### Camera

- `media.targets.list` returns `CAMERA_X_ANALYZER` and/or `MLKIT_INPUT_IMAGE_FACTORY` after the camera trigger.
- `media.camera.injectFrames` installs a rule with `ruleId` and `restoreToken`.
- `media.camera.assertConsumed` returns `passed=true`.
- `media.camera.history` contains matching record ids.
- `media.camera.snapshot` returns discovered targets and active/consumed rule metadata.
- `media.camera.clear` removes active camera rules.

### Build Safety

- `:sample-app:verifyAiDebugDebugInstrumentation` passes.
- `:sample-app:checkAiDebugReleaseSafety` passes.
- Reports are linked in `artifacts`.
