# Feature Specification: Media Input Verification Suite

**Feature Branch**: `015-media-input-verification-suite`  
**Created**: 2026-04-29  
**Status**: Draft  
**Input**: Add a focused verification suite that proves `013-audio-input-control` and `014-camera-input-control` work end-to-end on a connected Android device, including target discovery, fixture injection, consumption assertions, cleanup, reports, and release safety.

## User Scenarios & Testing

### User Story 1 - Verify Audio Input Control End-To-End (Priority: P1)

As an AI agent, I want one guided device test that drives normal sample app `AudioRecord` code, discovers the rewritten audio target, injects a fixture, and proves the fixture was consumed.

**Acceptance Scenarios**:

1. **Given** the debug sample app contains a normal `AudioRecord.read(...)` path with no AI debug media imports, **When** the audio dogfood trigger runs, **Then** `media.targets.list` returns an `AUDIO_RECORD_READ` target with observed audio metadata.
2. **Given** a WAV or PCM fixture is staged and registered, **When** `media.audio.inject` targets the discovered call site, **Then** the next audio trigger consumes fixture bytes instead of falling back to real input.
3. **Given** fixture reads complete, **When** `media.audio.assertConsumed` is called, **Then** it returns `passed=true`, consumed byte/read counts, and record ids that appear in `media.audio.history`.
4. **Given** the verification finishes, **When** cleanup runs, **Then** audio rules, fixtures, overrides, hooks, and session-scoped state are removed or reported as already absent.

### User Story 2 - Verify Camera Input Control End-To-End (Priority: P1)

As an AI agent, I want one guided device test that drives normal sample app CameraX or ML Kit-like image code, discovers camera targets, injects a frame fixture, and proves frame consumption.

**Acceptance Scenarios**:

1. **Given** the debug sample app contains normal CameraX analyzer and/or ML Kit `InputImage` factory code with no AI debug media imports, **When** the camera dogfood trigger runs, **Then** `media.targets.list` returns `CAMERA_X_ANALYZER` and/or `MLKIT_INPUT_IMAGE_FACTORY` targets.
2. **Given** a PNG/JPEG/NV21 fixture is staged and registered, **When** `media.camera.injectFrames` targets a discovered camera target, **Then** a subsequent trigger consumes at least one fixture frame.
3. **Given** fixture frames are consumed, **When** `media.camera.assertConsumed` is called, **Then** it returns `passed=true`, consumed frame count, and record ids visible in `media.camera.history`.
4. **Given** the target has active or historical camera state, **When** `media.camera.snapshot` is called, **Then** it returns active rules and discovered target metadata useful to an AI agent.

### User Story 3 - Generate A Media Verification Report (Priority: P1)

As a developer, I want a single report artifact that explains exactly which 013 and 014 capabilities passed, failed, or were skipped so that partial media implementations are not misrepresented.

**Acceptance Scenarios**:

1. **Given** the suite runs on a connected device, **When** it completes, **Then** it writes a JSON report under `build/ai-debug/media-verification/`.
2. **Given** only the tool layer is available, **When** no audio/camera call-site target is discovered, **Then** the report marks the corresponding end-to-end section as `skipped` or `blocked` with actionable evidence.
3. **Given** a capability fails, **When** the report is written, **Then** it includes the failing tool call, response/error body, and cleanup result.

### User Story 4 - Preserve Release Safety (Priority: P2)

As a developer, I want the verification suite to prove media input control remains debug-only.

**Acceptance Scenarios**:

1. **Given** the sample release artifact is built, **When** media verification runs release checks, **Then** release safety passes and reports no media runtime/bridge leaks.
2. **Given** an intentional audio or camera bridge leak fixture is enabled, **When** the negative release check runs, **Then** the build fails with the forbidden class or bridge signature.

## Requirements

- **FR-001**: The suite MUST provide one primary script, `scripts/spec015-media-input-verification.sh`, for connected-device validation of audio and camera media input controls.
- **FR-002**: The suite MUST build and install the debug sample app, launch it, forward the runtime port, and wait for `runtime.ping`.
- **FR-003**: The suite MUST stage media fixtures through daemon/runtime paths, not inline large binary payloads in JSON.
- **FR-004**: The suite MUST verify `media.capabilities`, `media.targets.list`, `media.fixture.register`, `media.fixture.list`, and `media.fixture.delete` before running audio/camera-specific checks.
- **FR-005**: The audio verification MUST prove target discovery, injection, history, assertion, fallback or cleanup behavior, and audit evidence for `media.audio.*`.
- **FR-006**: The camera verification MUST prove target discovery, injection, snapshot, history, assertion, fallback or cleanup behavior, and audit evidence for `media.camera.*`.
- **FR-007**: The suite MUST distinguish `passed`, `failed`, `skipped`, and `blocked` sections in its report.
- **FR-008**: The suite MUST fail the process if a required P1 verification section fails.
- **FR-009**: The suite SHOULD allow running only audio or only camera verification for faster iteration.
- **FR-010**: The sample app triggers used by the suite MUST be normal app/media code and MUST NOT import AI debug media APIs from business code.
- **FR-011**: The suite MUST run Gradle instrumentation verification and release safety checks, or explicitly include their latest artifact paths in the report.
- **FR-012**: Cleanup MUST be idempotent and MUST run even after partial failures.

## Non-Goals

- Implementing new audio injection semantics. That belongs to `013-audio-input-control`.
- Implementing new camera frame conversion or analyzer behavior. That belongs to `014-camera-input-control`.
- Replacing preview surfaces, fake HAL providers, native camera/audio stacks, or external SDK-specific media ingestion.
- CI emulator provisioning beyond a script that can run when a compatible device is already connected.

## Key Entities

- **MediaVerificationRun**: A complete validation run with environment, device, sections, artifacts, and cleanup results.
- **MediaVerificationSection**: A named verification slice such as foundation, audio target discovery, audio injection, camera target discovery, or camera injection.
- **MediaVerificationEvidence**: Tool responses, history records, report paths, fixture metadata, and Gradle report references.
- **Sample Audio Dogfood Path**: Normal sample app code that exercises `AudioRecord.read(...)` after debug ASM rewriting.
- **Sample Camera Dogfood Path**: Normal sample app code that exercises CameraX analyzer and/or ML Kit-like image input after debug ASM rewriting.

## Success Criteria

- **SC-001**: `scripts/spec015-media-input-verification.sh --audio` passes on a connected debug device after audio dogfood sample support is implemented.
- **SC-002**: `scripts/spec015-media-input-verification.sh --camera` passes on a connected debug device after camera dogfood sample support is implemented.
- **SC-003**: `scripts/spec015-media-input-verification.sh` writes a report that clearly marks audio and camera status.
- **SC-004**: The suite passes `:sample-app:verifyAiDebugDebugInstrumentation` and `:sample-app:checkAiDebugReleaseSafety`.
- **SC-005**: When audio or camera call-site targets are unavailable, the suite reports `blocked` with missing target evidence instead of claiming end-to-end success.
