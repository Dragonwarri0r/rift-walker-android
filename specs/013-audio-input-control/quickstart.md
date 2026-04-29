# Quickstart: Audio Input Control

This feature is not implemented yet. The target workflow below defines the expected AudioRecord dogfood path.

## Prerequisites

- `012-media-input-foundation` fixture staging and target discovery are available.
- The sample app includes normal business code that calls `AudioRecord.read(...)`.

## Register Audio Fixture

```json
{
  "tool": "media.fixture.register",
  "arguments": {
    "fixtureId": "wake-word-yes",
    "hostPath": "/absolute/path/fixtures/wake-word-yes.wav",
    "mimeType": "audio/wav",
    "metadata": {
      "sampleRateHz": 16000,
      "channelCount": 1,
      "encoding": "pcm_16bit"
    }
  }
}
```

## Discover Audio Target

```json
{
  "tool": "media.targets.list",
  "arguments": {
    "kind": "AUDIO_RECORD",
    "includeObserved": true
  }
}
```

## Inject Fixture

```json
{
  "tool": "media.audio.inject",
  "arguments": {
    "targetId": "audio:audiorecord:read:com.riftwalker.sample.media.AudioLoop#run()V@insn87",
    "fixtureId": "wake-word-yes",
    "mode": "once",
    "readBehavior": {
      "blockingMode": "respect_call",
      "eofBehavior": "return_zero"
    }
  }
}
```

## Assert Consumption

```json
{
  "tool": "media.audio.assertConsumed",
  "arguments": {
    "targetId": "audio:audiorecord:read:com.riftwalker.sample.media.AudioLoop#run()V@insn87",
    "fixtureId": "wake-word-yes",
    "minFrames": 1600,
    "timeoutMs": 3000,
    "pollIntervalMs": 100
  }
}
```

## Planned Smoke Scripts

```bash
scripts/spec013-audio-smoke.sh
scripts/spec013-negative-release-audio-leak.sh
```
