# Feature Specification: Audio Input Control

**Feature Branch**: `013-audio-input-control`  
**Created**: 2026-04-29  
**Status**: Draft  
**Input**: Inject deterministic audio fixtures into app code that uses `AudioRecord` without business source changes, using debug-only ASM call-site rewrite, `AiDebugMediaHookBridge`, media target discovery, and MCP audio controls.

## User Scenarios & Testing

### User Story 1 - Inject AudioRecord Reads (Priority: P1)

As an AI agent, I want `AudioRecord.read(...)` calls in debug builds to consume a WAV/PCM fixture so I can drive audio-driven branches such as wake word, voice command, audio classification, or recording validation.

**Acceptance Scenarios**:

1. **Given** business code calls a supported `AudioRecord.read(...)` overload, **When** the debug variant is built, **Then** that call site is rewritten to `AiDebugMediaHookBridge` with a stable `callSiteId`.
2. **Given** the rewritten call site is hit, **When** the agent calls `media.targets.list`, **Then** an `AUDIO_RECORD` target appears with observed sample rate, channel count, encoding, and hit count.
3. **Given** a WAV or PCM fixture is registered, **When** `media.audio.inject` targets the call site, **Then** subsequent reads fill the requested buffer from the fixture or generated stream.
4. **Given** the expected audio has been consumed, **When** `media.audio.assertConsumed` is called, **Then** it reports the consumed bytes/frames and matching read records.

### User Story 2 - Preserve Real Audio Behavior When No Rule Matches (Priority: P1)

As a developer, I want audio hook instrumentation to be transparent unless a fixture rule is active.

**Acceptance Scenarios**:

1. **Given** no audio injection rule matches a target, **When** app code calls `AudioRecord.read(...)`, **Then** the bridge calls the original `AudioRecord.read(...)`.
2. **Given** a rule expires by `times`, EOF, or cleanup, **When** the app reads again, **Then** fallback behavior resumes.

### User Story 3 - Record Audio Lifecycle And Errors (Priority: P1)

As an AI agent, I want audio read and lifecycle history so I can debug why an injected fixture was or was not consumed.

**Acceptance Scenarios**:

1. **Given** app code calls `startRecording`, `stop`, or `release`, **When** those calls are instrumented, **Then** lifecycle events appear in `media.audio.history`.
2. **Given** error injection is configured, **When** the fixture rule reaches the error condition, **Then** the bridge returns the configured platform-style error code and records it.

## Requirements

- **FR-001**: This spec depends on `012-media-input-foundation` for fixture staging, target discovery, shared protocol, audit, cleanup, and release safety foundation.
- **FR-002**: The feature MUST NOT require business source code changes.
- **FR-003**: The plugin MUST rewrite these overloads:
  - `AudioRecord.read(byte[], int, int)`
  - `AudioRecord.read(byte[], int, int, int)`
  - `AudioRecord.read(short[], int, int)`
  - `AudioRecord.read(short[], int, int, int)`
  - `AudioRecord.read(float[], int, int, int)`
  - `AudioRecord.read(ByteBuffer, int)`
  - `AudioRecord.read(ByteBuffer, int, int)`
- **FR-004**: The plugin SHOULD rewrite `AudioRecord.startRecording()`, `stop()`, and `release()` for lifecycle history.
- **FR-005**: The runtime MUST support WAV and raw PCM fixtures.
- **FR-006**: The runtime MUST account for consumed bytes and frames per target, fixture, and rule.
- **FR-007**: The bridge MUST preserve AudioRecord fallback behavior when no injection rule matches.
- **FR-008**: The bridge MUST model blocking and non-blocking read modes where the overload exposes a read mode.
- **FR-009**: The bridge MUST handle EOF, loop, silence, sine, noise, short read, and injected error modes.
- **FR-010**: The bridge MUST model key platform semantics where practical, including frame-size truncation, non-direct ByteBuffer handling, and delayed `ERROR_DEAD_OBJECT` after successful transfer.
- **FR-011**: `media.audio.history` and `media.audio.assertConsumed` MUST support optional target and fixture filters.
- **FR-012**: Release safety MUST reject `AiDebugMediaHookBridge` audio entrypoints and rewritten AudioRecord call sites in release artifacts.

## Non-Goals

- MediaRecorder encoded audio stream replacement.
- Native audio stack / Oboe / AAudio interception.
- Speech-recognition SDK-specific APIs unless they use AudioRecord or are configured as a later custom hook.
- Perfect device-timing simulation beyond the documented MVP read behavior.

## Key Entities

- **AudioRecordTarget**: `MediaTarget` with observed AudioRecord format metadata.
- **AudioFixtureCursor**: Per-rule cursor over PCM frames.
- **AudioInjectionRule**: Rule binding a fixture or generated stream to an AudioRecord target.
- **AudioHistoryRecord**: Read, lifecycle, fallback, EOF, and error event record.

## Success Criteria

- **SC-001**: Unit tests cover all supported read overload families.
- **SC-002**: A sample app with normal AudioRecord code and no AI debug media imports consumes a fixture and passes `media.audio.assertConsumed`.
- **SC-003**: When no rule matches, fallback calls real `AudioRecord.read(...)`.
- **SC-004**: Debug instrumentation verification finds rewritten AudioRecord call sites.
- **SC-005**: Release safety fails if rewritten AudioRecord bridge calls appear in release artifacts.
