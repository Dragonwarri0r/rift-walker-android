# Implementation Plan: Audio Input Control

## Scope

Implement no-source-change audio injection for app code that uses `AudioRecord`. This spec owns AudioRecord ASM rewrite, audio bridge entrypoints, WAV/PCM parsing, read semantics, audio history, consumption assertions, and audio-specific release checks.

## Dependencies

- `012-media-input-foundation` for fixture staging, target registry, media capability registration, common MCP routing, audit, and release-safety extension points.
- `005-gradle-debug-integration` for ASM visitor registration and verification tasks.

## Technical Approach

1. Add audio protocol models:
   - `MediaAudioInjectRequest/Response`,
   - `MediaAudioClearRequest/Response`,
   - `MediaAudioHistoryRequest/Response`,
   - `MediaAudioAssertConsumedRequest/Response`,
   - audio read behavior and generated stream options.
2. Add runtime audio controller:
   - rule store,
   - WAV/PCM decoder,
   - format conversion where feasible,
   - cursor accounting,
   - lifecycle/read history.
3. Add bridge entrypoints for supported AudioRecord read overloads and lifecycle methods.
4. Add Gradle ASM rewrite:
   - detect AudioRecord virtual calls,
   - inject stable `callSiteId`,
   - route through `AiDebugMediaHookBridge`,
   - preserve original fallback path in bridge implementation.
5. Add MCP tools and HTTP routes:
   - `media.audio.inject`,
   - `media.audio.clear`,
   - `media.audio.history`,
   - `media.audio.assertConsumed`.
6. Add tests:
   - pure JVM/runtime unit tests for fixture cursor and read semantics,
   - plugin verification for call-site rewrite,
   - sample dogfood for device-level consumption.

## Implementation Slices

### Slice A - Protocol And Runtime Rule Store

Add audio request/response models and an `AudioInputController` rule store that can be tested without ASM. Use manually registered `AUDIO_RECORD` targets from the foundation registry.

### Slice B - Fixture Cursor And Read Semantics

Implement WAV/PCM decoding and fixture cursor behavior before Gradle rewriting. Start with byte array reads, then add short array, float array, and ByteBuffer overloads.

### Slice C - Bridge Entrypoints

Add `AiDebugMediaHookBridge` audio methods that accept the original `AudioRecord`, arguments, and `callSiteId`. Bridge methods should call runtime audio control when a rule matches and fallback to the original `AudioRecord` call otherwise.

### Slice D - ASM Rewrite

Rewrite AudioRecord read and lifecycle call sites for debuggable variants only. Verification should prove the debug artifact calls the bridge and release artifacts do not.

### Slice E - Sample Dogfood

Add a sample audio loop with normal `AudioRecord` code, stage a small WAV fixture, inject it, assert consumption, and generate a report.

## Ownership

- `ai-debug-protocol`: audio request/response models.
- `ai-debug-runtime`: audio controller, bridge entrypoints, WAV/PCM handling, history.
- `ai-debug-daemon`: audio MCP tools and scenario steps.
- `ai-debug-gradle-plugin`: AudioRecord ASM visitor and verification.
- `sample-app`: normal AudioRecord code path and fixture-driven branch.
- `scripts`: audio smoke and negative release leak checks.

## Validation

```bash
./gradlew :ai-debug-protocol:test :ai-debug-runtime:testDebugUnitTest :ai-debug-daemon:test
./gradlew :sample-app:verifyAiDebugDebugInstrumentation :sample-app:checkAiDebugReleaseSafety
scripts/spec013-audio-smoke.sh
scripts/spec013-negative-release-audio-leak.sh
```

## Risks

- AudioRecord read behavior differs across overloads; keep ByteArray first, then ShortArray/FloatArray/ByteBuffer.
- Device timing should not be over-modeled in MVP; focus on deterministic fixture consumption and platform-like return values.
- Format conversion should start small: PCM 16-bit mono/stereo and WAV PCM before broader resampling.
