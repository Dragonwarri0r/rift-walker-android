# MCP Tool Contract: Media Input Foundation

Large media payloads are never embedded in JSON. The daemon stages host files to the selected device and registers runtime fixture metadata.

## `media.capabilities`

Request:

```json
{}
```

Response:

```json
{
  "foundation": {
    "targetDiscovery": true,
    "fixtureStaging": true
  },
  "audio": {
    "audioRecordRead": false
  },
  "camera": {
    "cameraXAnalyzer": false,
    "mlKitInputImageFactories": false
  }
}
```

## `media.targets.list`

Request:

```json
{
  "kind": "AUDIO_RECORD|CAMERA_X_ANALYZER|MLKIT_INPUT_IMAGE|CUSTOM_FRAME",
  "query": "scanner",
  "includeObserved": true
}
```

Response:

```json
{
  "targets": [
    {
      "targetId": "audio:audiorecord:read:com.example.voice.WakeWordEngine#captureLoop()V@insn87",
      "kind": "AUDIO_RECORD",
      "callSiteId": "com.example.voice.WakeWordEngine#captureLoop()V@insn87",
      "apiSignature": "android.media.AudioRecord#read([BII)I",
      "hitCount": 3,
      "lastHitEpochMs": 1777430000000,
      "observed": {
        "sampleRateHz": 16000,
        "channelCount": 1,
        "encoding": "pcm_16bit"
      }
    }
  ]
}
```

## `media.fixture.register`

Daemon request:

```json
{
  "fixtureId": "wake-word-yes",
  "hostPath": "/absolute/path/fixtures/wake-word-yes.wav",
  "mimeType": "audio/wav",
  "metadata": {
    "sampleRateHz": 16000,
    "channelCount": 1,
    "encoding": "pcm_16bit"
  }
}
```

Runtime registration payload after daemon staging:

```json
{
  "fixtureId": "wake-word-yes",
  "devicePath": "/data/local/tmp/ai-debug/fixtures/wake-word-yes.wav",
  "sha256": "abc123...",
  "sizeBytes": 42000,
  "mimeType": "audio/wav",
  "metadata": {
    "sampleRateHz": 16000,
    "channelCount": 1,
    "encoding": "pcm_16bit"
  }
}
```

Response:

```json
{
  "fixtureId": "wake-word-yes",
  "devicePath": "/data/local/tmp/ai-debug/fixtures/wake-word-yes.wav",
  "sha256": "abc123...",
  "registered": true,
  "restoreToken": "cleanup_fixture_001"
}
```

## `media.fixture.list`

Request:

```json
{"mimePrefix":"audio/"}
```

Response:

```json
{
  "fixtures": [
    {
      "fixtureId": "wake-word-yes",
      "devicePath": "/data/local/tmp/ai-debug/fixtures/wake-word-yes.wav",
      "mimeType": "audio/wav",
      "sha256": "abc123...",
      "sizeBytes": 42000
    }
  ]
}
```

## `media.fixture.delete`

Request:

```json
{"fixtureId":"wake-word-yes"}
```

Response:

```json
{"deleted":1}
```
