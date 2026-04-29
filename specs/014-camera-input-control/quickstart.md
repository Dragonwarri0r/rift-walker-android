# Quickstart: Camera Input Control

This feature is not implemented yet. The target workflow below defines the expected CameraX/ML Kit dogfood path.

## Prerequisites

- `012-media-input-foundation` fixture staging and target discovery are available.
- The sample app includes normal CameraX and ML Kit-like image processing code.

## Register Camera Fixture

```json
{
  "tool": "media.fixture.register",
  "arguments": {
    "fixtureId": "vip-card-001",
    "hostPath": "/absolute/path/fixtures/vip-card-001.png",
    "mimeType": "image/png",
    "metadata": {
      "width": 1280,
      "height": 720,
      "rotationDegrees": 90
    }
  }
}
```

## Discover Camera Targets

```json
{
  "tool": "media.targets.list",
  "arguments": {
    "kind": "CAMERA_X_ANALYZER",
    "includeObserved": true
  }
}
```

For ML Kit paths:

```json
{
  "tool": "media.targets.list",
  "arguments": {
    "kind": "MLKIT_INPUT_IMAGE",
    "includeObserved": true
  }
}
```

## Inject Frames

```json
{
  "tool": "media.camera.injectFrames",
  "arguments": {
    "targetId": "camera:camerax:setAnalyzer:com.riftwalker.sample.media.ScannerActivity#bindCamera()V@insn142",
    "fixtureIds": ["vip-card-001"],
    "mode": "replace_on_real_frame",
    "times": 1
  }
}
```

## Assert Consumption

```json
{
  "tool": "media.camera.assertConsumed",
  "arguments": {
    "targetId": "camera:camerax:setAnalyzer:com.riftwalker.sample.media.ScannerActivity#bindCamera()V@insn142",
    "fixtureId": "vip-card-001",
    "minFrames": 1,
    "timeoutMs": 3000,
    "pollIntervalMs": 100
  }
}
```

## Planned Smoke Scripts

```bash
scripts/spec014-camera-smoke.sh
scripts/spec014-negative-release-camera-leak.sh
```
