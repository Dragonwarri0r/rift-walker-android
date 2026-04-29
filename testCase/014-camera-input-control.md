# 014 Camera Input Control Agent Test

## Goal

Guide an agent through deterministic camera/image fixture injection for CameraX analyzers,
ML Kit `InputImage` factories, and custom frame hooks.

## Maturity Note

The quickstart may still describe this feature as target workflow while implementation is in progress.
Report the deepest passing layer:

- Tool layer: `media.camera.*` tools are registered and validate inputs.
- Bridge layer: CameraX/ML Kit/custom call sites register targets.
- Dogfood layer: sample app consumes a fixture frame and asserts it.

## Agent Flow

For the serious connected-device validation path, prefer the 015 suite first:

```bash
bash scripts/spec015-media-input-verification.sh --camera
```

Use the manual steps below when diagnosing a failed or blocked 015 camera section.

1. Register an image fixture.

For the current ML Kit-like sample path, use NV21 so the bridge can create a fixture-backed
`InputImage` deterministically.

```json
{
  "tool": "media.fixture.register",
  "arguments": {
    "fixtureId": "vip-card-001",
    "hostPath": "/absolute/path/fixtures/vip-card-001.nv21",
    "devicePath": "/data/local/tmp/ai-debug-media/015/vip-card-001.nv21",
    "mimeType": "application/x-nv21",
    "metadata": {
      "width": 4,
      "height": 4,
      "rotationDegrees": 0,
      "format": "NV21"
    }
  }
}
```

Expected evidence:

- Fixture appears in `media.fixture.list` with `mimeType == application/x-nv21`, sha256, size, and metadata.

2. Trigger camera or image-processing code and discover targets.

For CameraX:

```json
{
  "tool": "media.targets.list",
  "arguments": {
    "kind": "CAMERA_X_ANALYZER"
  }
}
```

For ML Kit:

```json
{
  "tool": "media.targets.list",
  "arguments": {
    "kind": "MLKIT_INPUT_IMAGE_FACTORY"
  }
}
```

Expected evidence:

- Target includes stable `targetId`, `callSiteId`, `apiSignature`, `hitCount`, and observed width/height/rotation/format where available.

3. Inspect target snapshot.

```json
{"tool":"media.camera.snapshot","arguments":{"targetId":"<camera targetId>"}}
```

Expected evidence:

- The response contains `targets` and `activeRules`; the selected target appears when it has been discovered.
- If no target has been hit, `targets` and `activeRules` may be empty; keep this as evidence that
  the app has not reached the instrumented call site yet.

4. Inject frames.

```json
{
  "tool": "media.camera.injectFrames",
  "arguments": {
    "targetId": "<camera targetId>",
    "fixtureId": "vip-card-001",
    "mode": "mlkit_input_image",
    "loop": false,
    "times": 1
  }
}
```

Expected evidence:

- Response includes `ruleId` and `restoreToken`.

5. Drive the app and assert frame consumption.

```json
{
  "tool": "media.camera.assertConsumed",
  "arguments": {
    "targetId": "<camera targetId>",
    "fixtureId": "vip-card-001",
    "minFrames": 1,
    "timeoutMs": 3000,
    "pollIntervalMs": 100
  }
}
```

Expected evidence:

- `passed == true`.
- `consumedFrames >= 1`.
- `recordIds` match records in `media.camera.history`.

6. Inspect history.

```json
{"tool":"media.camera.history","arguments":{"targetId":"<camera targetId>","fixtureId":"vip-card-001","limit":50}}
```

Expected evidence:

- Records include fixture id, frame index, consumed/fallback flags, observed frame metadata, and timestamp.

7. Test fallback and cleanup.

```json
{"tool":"media.camera.clear","arguments":{"ruleIds":["<ruleId>"]}}
{"tool":"media.fixture.delete","arguments":{"fixtureId":"vip-card-001"}}
{"tool":"session.cleanup","arguments":{}}
```

Expected evidence:

- Clear returns `cleared == 1`.
- Later real camera or image factory calls fall back to original behavior.
- `audit.history` contains camera inject/clear and fixture events.

## Failure Triage

- No CameraX target: analyzer wrapping did not happen or the analyzer was never set.
- No ML Kit target: factory bridge did not hit or ML Kit dependency path is absent.
- Inject succeeds but no consumption: drive mode is wrong, app did not receive a frame, or target id is stale.
- Release safety catches camera bridge in release: debug instrumentation leaked and must be fixed before shipping.
