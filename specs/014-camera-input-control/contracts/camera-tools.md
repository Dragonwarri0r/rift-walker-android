# MCP Tool Contract: Camera Input Control

This contract depends on fixture and target tools from `012-media-input-foundation`.

## `media.camera.injectFrames`

Request:

```json
{
  "targetId": "camera:camerax:setAnalyzer:com.example.camera.ScannerActivity#bindCamera()V@insn142",
  "fixtureIds": ["vip-card-001"],
  "mode": "replace_on_real_frame|drive_analyzer",
  "fps": 10,
  "loop": false,
  "times": 1,
  "metadataOverride": {
    "rotationDegrees": 90
  }
}
```

Response:

```json
{
  "ruleId": "media_camera_rule_001",
  "targetId": "camera:camerax:setAnalyzer:com.example.camera.ScannerActivity#bindCamera()V@insn142",
  "fixtureIds": ["vip-card-001"],
  "restoreToken": "cleanup_media_camera_001"
}
```

## `media.camera.history`

Request:

```json
{
  "targetId": "camera:camerax:setAnalyzer:com.example.camera.ScannerActivity#bindCamera()V@insn142",
  "fixtureId": "vip-card-001",
  "limit": 50
}
```

Response:

```json
{
  "records": [
    {
      "id": "media_camera_evt_001",
      "targetId": "camera:camerax:setAnalyzer:com.example.camera.ScannerActivity#bindCamera()V@insn142",
      "event": "frame_injected",
      "fixtureId": "vip-card-001",
      "frameIndex": 0,
      "width": 1280,
      "height": 720,
      "rotationDegrees": 90,
      "fallback": false,
      "timestampEpochMs": 1777430000000
    }
  ]
}
```

## `media.camera.assertConsumed`

Request:

```json
{
  "targetId": "camera:camerax:setAnalyzer:com.example.camera.ScannerActivity#bindCamera()V@insn142",
  "fixtureId": "vip-card-001",
  "minFrames": 1,
  "timeoutMs": 3000,
  "pollIntervalMs": 100
}
```

Response:

```json
{
  "passed": true,
  "consumedFrames": 1,
  "recordIds": ["media_camera_evt_001"]
}
```

## `media.camera.snapshot`

Request:

```json
{
  "targetId": "camera:camerax:setAnalyzer:com.example.camera.ScannerActivity#bindCamera()V@insn142"
}
```

Response:

```json
{
  "targetId": "camera:camerax:setAnalyzer:com.example.camera.ScannerActivity#bindCamera()V@insn142",
  "lastObserved": {
    "width": 1280,
    "height": 720,
    "rotationDegrees": 90,
    "format": "YUV_420_888"
  }
}
```

## `media.camera.clear`

Request:

```json
{
  "ruleId": "media_camera_rule_001",
  "targetId": null
}
```

Response:

```json
{"cleared":1}
```
