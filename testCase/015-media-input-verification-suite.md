# 015 Media Input Verification Suite Agent Test

## Goal

Run the connected-device verification suite that proves 013 audio and 014 camera media input
control end-to-end. This case is the preferred serious validation path for media input work.

## Run Modes

```bash
bash scripts/spec015-media-input-verification.sh --foundation
bash scripts/spec015-media-input-verification.sh --audio
bash scripts/spec015-media-input-verification.sh --camera
bash scripts/spec015-media-input-verification.sh
```

No flag runs foundation, audio, camera, debug instrumentation verification, and release safety.

## Expected Evidence

- Report path under `build/ai-debug/media-verification/report_*.json`.
- `foundation.status == passed`.
- Audio mode discovers `AUDIO_RECORD_READ`, injects the WAV fixture, and passes
  `media.audio.assertConsumed`.
- Camera mode discovers `MLKIT_INPUT_IMAGE_FACTORY` or `CAMERA_X_ANALYZER`, injects the NV21 fixture,
  and passes `media.camera.assertConsumed`.
- Cleanup clears media rules, deletes fixtures, removes pushed device files, and removes the ADB
  forward.

## Interpretation

- `passed`: requested sections have real connected-device evidence.
- `blocked`: a requested prerequisite is missing, such as no discovered media target.
- `failed`: a requested assertion or cleanup step ran and failed.
- `skipped`: a section was not selected by the chosen mode.

Never treat a foundation-only pass as proof that 013 or 014 works end-to-end.
