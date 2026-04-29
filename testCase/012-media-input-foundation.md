# 012 Media Input Foundation Agent Test

## Goal

Verify shared media infrastructure before audio/camera-specific injection:
capabilities, target discovery, fixture staging, metadata validation, audit, cleanup, and release safety.

## Maturity Note

This spec may be partially implemented while audio and camera dogfood call sites are still evolving.
Treat this case as two layers:

- Foundation contract: media tools accept requests and manage fixture records.
- Real call-site discovery: targets appear only after instrumented media call sites are hit.

## Agent Flow

1. Confirm media capabilities.

```json
{"tool":"media.capabilities","arguments":{}}
{"tool":"capabilities.list","arguments":{"query":"media"}}
```

Expected evidence:

- `targetKinds` includes media target kinds such as `AUDIO_RECORD_READ`,
  `CAMERA_X_ANALYZER`, and `MLKIT_INPUT_IMAGE_FACTORY`.
- `fixtureMimeTypes` includes `audio/wav`, `audio/pcm`, `image/png`, and `image/jpeg`.
- `releaseGuarantees` describes release no-op/safety behavior.
- Capability list includes `media.targets.list`, `media.fixture.register`,
  `media.fixture.list`, and `media.fixture.delete`.

2. List targets before media activity.

```json
{"tool":"media.targets.list","arguments":{}}
```

Expected evidence:

- Empty target list is acceptable and recoverable before media call sites execute.

3. Register a fixture through the daemon.

Use a small local fixture file. The daemon, not the agent, should push it to the device and
compute sha256.

Make sure the staging directory exists before the first register call:

```bash
adb shell mkdir -p /data/local/tmp/ai-debug/fixtures
```

```json
{
  "tool": "media.fixture.register",
  "arguments": {
    "fixtureId": "agent-foundation-audio",
    "hostPath": "/absolute/path/fixtures/agent-foundation-audio.wav",
    "devicePath": "/data/local/tmp/ai-debug/fixtures/agent-foundation-audio.wav",
    "mimeType": "audio/wav",
    "metadata": {
      "sampleRateHz": 16000,
      "channelCount": 1,
      "encoding": "pcm_16bit"
    }
  }
}
```

Expected evidence:

- Response includes `verified == true`.
- `fixture.fixtureId`, `fixture.devicePath`, `fixture.sha256`, `fixture.mimeType`, and `fixture.sizeBytes` are populated.
- The device path is under a debug/session fixture staging location.

4. List and filter fixtures.

```json
{"tool":"media.fixture.list","arguments":{"mimeType":"audio/wav"}}
```

Expected evidence:

- Registered fixture appears with matching `mimeType`, `sha256`, and `sizeBytes`.

5. Negative validation.

Try a missing host path or invalid sha256 registration path if direct runtime registration is exposed.

Expected evidence:

- Structured, recoverable failure.
- No fixture record appears in `media.fixture.list`.

6. Delete and cleanup.

```json
{"tool":"media.fixture.delete","arguments":{"fixtureId":"agent-foundation-audio"}}
{"tool":"media.fixture.list","arguments":{"mimeType":"audio/wav"}}
{"tool":"session.cleanup","arguments":{}}
```

Expected evidence:

- Delete returns `deleted == true`.
- Fixture no longer appears.
- `audit.history` contains register/list/delete events.

7. Release safety.

Run the release safety task and inspect the report.

Expected evidence:

- Release output contains no media runtime foundation classes, fixture staging logic, or bridge base classes.

## Failure Triage

- Fixture registers without sha256/size: daemon staging validation is incomplete.
- Runtime accepts a missing staged file: device-side validation is broken.
- Targets never appear after media call-site execution: instrumentation or bridge registration is missing.
