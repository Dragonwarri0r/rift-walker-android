# MCP Tool Contract: Audio Input Control

This contract depends on fixture and target tools from `012-media-input-foundation`.

## `media.audio.inject`

Request:

```json
{
  "targetId": "audio:audiorecord:read:com.example.voice.WakeWordEngine#captureLoop()V@insn87",
  "fixtureId": "wake-word-yes",
  "mode": "once|loop|silence|sine|noise",
  "readBehavior": {
    "blockingMode": "respect_call|force_blocking|force_non_blocking",
    "shortReadEvery": null,
    "eofBehavior": "return_zero|fallback|error",
    "errorAfterFrames": null,
    "errorCode": null
  },
  "times": null
}
```

Response:

```json
{
  "ruleId": "media_audio_rule_001",
  "targetId": "audio:audiorecord:read:com.example.voice.WakeWordEngine#captureLoop()V@insn87",
  "fixtureId": "wake-word-yes",
  "restoreToken": "cleanup_media_audio_001"
}
```

## `media.audio.history`

Request:

```json
{
  "targetId": "audio:audiorecord:read:com.example.voice.WakeWordEngine#captureLoop()V@insn87",
  "fixtureId": "wake-word-yes",
  "limit": 50
}
```

Response:

```json
{
  "records": [
    {
      "id": "media_audio_evt_001",
      "targetId": "audio:audiorecord:read:com.example.voice.WakeWordEngine#captureLoop()V@insn87",
      "event": "read",
      "fixtureId": "wake-word-yes",
      "requestedBytes": 3200,
      "returnedBytes": 3200,
      "consumedFrames": 1600,
      "fallback": false,
      "timestampEpochMs": 1777430000000
    }
  ]
}
```

## `media.audio.assertConsumed`

Request:

```json
{
  "targetId": "audio:audiorecord:read:com.example.voice.WakeWordEngine#captureLoop()V@insn87",
  "fixtureId": "wake-word-yes",
  "minBytes": 3200,
  "minFrames": 1600,
  "timeoutMs": 3000,
  "pollIntervalMs": 100
}
```

Response:

```json
{
  "passed": true,
  "consumedBytes": 3200,
  "consumedFrames": 1600,
  "recordIds": ["media_audio_evt_001"]
}
```

## `media.audio.clear`

Request:

```json
{
  "ruleId": "media_audio_rule_001",
  "targetId": null
}
```

Response:

```json
{"cleared":1}
```
