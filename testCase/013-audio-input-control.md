# 013 Audio Input Control Agent Test

## Goal

Guide an agent through deterministic AudioRecord fixture injection without business source changes:
discover an `AUDIO_RECORD_READ` target, inject a fixture, verify consumed bytes/reads, then clean up.

## Maturity Note

The quickstart may still describe this feature as target workflow while implementation is in progress.
When running this case, explicitly report which layer passed:

- Tool layer: `media.audio.*` tools are registered and validate inputs.
- Bridge layer: AudioRecord call sites register targets and consume fixtures.
- Dogfood layer: sample app behavior proves injection end-to-end.

## Agent Flow

For the serious connected-device validation path, prefer the 015 suite first:

```bash
bash scripts/spec015-media-input-verification.sh --audio
```

Use the manual steps below when diagnosing a failed or blocked 015 audio section.

1. Prepare an audio fixture.

Use `media.fixture.register` from case 012 with a small WAV or raw PCM file:

```json
{
  "tool": "media.fixture.register",
  "arguments": {
    "fixtureId": "wake-word-yes",
    "hostPath": "/absolute/path/fixtures/wake-word-yes.wav",
    "devicePath": "/data/local/tmp/ai-debug-media/015/wake-word-yes.wav",
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

- Fixture appears in `media.fixture.list`.

2. Trigger audio code and discover the target.

Cause the app path that calls `AudioRecord.read(...)` to execute. Then call:

```json
{
  "tool": "media.targets.list",
  "arguments": {
    "kind": "AUDIO_RECORD_READ"
  }
}
```

Expected evidence:

- At least one target has kind `AUDIO_RECORD_READ`.
- Target includes stable `targetId`, `callSiteId`, `apiSignature`, `hitCount`, and observed format metadata.
- Observed metadata matches fixture expectations or explains conversion needs.

3. Inject the fixture.

```json
{
  "tool": "media.audio.inject",
  "arguments": {
    "targetId": "<audio targetId>",
    "fixtureId": "wake-word-yes",
    "loop": false,
    "behavior": {
      "blockingMode": "respect_call",
      "eof": "short_read"
    }
  }
}
```

Expected evidence:

- Response includes `ruleId` and `restoreToken`.

4. Drive the app and assert consumption.

Trigger enough reads for at least one frame block, then call:

```json
{
  "tool": "media.audio.assertConsumed",
  "arguments": {
    "targetId": "<audio targetId>",
    "fixtureId": "wake-word-yes",
    "minBytes": 3200,
    "minReads": 1,
    "timeoutMs": 3000,
    "pollIntervalMs": 100
  }
}
```

Expected evidence:

- `passed == true`.
- `consumedBytes >= minBytes`.
- `readCount >= minReads`.
- `recordIds` references records returned by `media.audio.history`.

5. Inspect history.

```json
{"tool":"media.audio.history","arguments":{"targetId":"<audio targetId>","fixtureId":"wake-word-yes","limit":50}}
```

Expected evidence:

- Read records show requested bytes, returned bytes, cumulative consumed bytes, and `fallback == false`.
- Lifecycle records appear if `startRecording`, `stop`, or `release` are instrumented.

6. Validate fallback and expiry.

Clear the rule or use `times: 1`, then trigger another read.

Expected evidence:

- Later history records show fallback behavior or no fixture consumption.

7. Cleanup.

```json
{"tool":"media.audio.clear","arguments":{"ruleIds":["<ruleId>"]}}
{"tool":"media.fixture.delete","arguments":{"fixtureId":"wake-word-yes"}}
{"tool":"session.cleanup","arguments":{}}
```

## Failure Triage

- No `AUDIO_RECORD_READ` target: ASM rewrite or bridge registration is missing.
- Injection installs but consumption stays zero: target id mismatch or app path did not read.
- Wrong frame counts: inspect sample rate, channel count, encoding, and bytes-per-frame conversion.
- Fallback does not resume after clear: rule cleanup is broken.
