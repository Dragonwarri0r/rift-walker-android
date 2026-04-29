# Feature Specification: Camera Input Control

**Feature Branch**: `014-camera-input-control`  
**Created**: 2026-04-29  
**Status**: Draft  
**Input**: Inject deterministic image fixtures into CameraX ImageAnalysis, ML Kit InputImage construction, and configured custom CV/OCR/QR frame processors without business source changes.

## User Scenarios & Testing

### User Story 1 - Inject CameraX Analyzer Frames (Priority: P1)

As an AI agent, I want CameraX `ImageAnalysis` analyzers in debug builds to receive deterministic fixture frames so I can drive scanner, OCR, card, face, or image-analysis branches.

**Acceptance Scenarios**:

1. **Given** business code calls `ImageAnalysis.setAnalyzer(...)`, **When** the debug variant is built, **Then** the call site is rewritten to a bridge that wraps the analyzer and records a `CAMERA_X_ANALYZER` target.
2. **Given** a camera fixture is active for the target, **When** a real frame arrives, **Then** `replace_on_real_frame` mode substitutes fixture-backed frame data or metadata before delegating to the analyzer.
3. **Given** deterministic CI mode is enabled, **When** `drive_analyzer` is used, **Then** the runtime invokes the analyzer on its executor with fixture-backed synthetic frames.
4. **Given** the expected frames are consumed, **When** `media.camera.assertConsumed` is called, **Then** the response proves frame consumption.

### User Story 2 - Cover ML Kit InputImage Paths (Priority: P1)

As an AI agent, I want ML Kit `InputImage.fromXxx(...)` factories to return fixture-backed inputs when active, because synthetic `ImageProxy` does not cover business code that requires `imageProxy.image != null`.

**Acceptance Scenarios**:

1. **Given** business code calls `InputImage.fromMediaImage(...)`, **When** a matching fixture is active, **Then** the bridge creates an `InputImage` from fixture-backed NV21, ByteArray, ByteBuffer, or Bitmap data.
2. **Given** no fixture matches, **When** the business code calls an ML Kit factory, **Then** the original ML Kit factory runs unchanged.
3. **Given** `imageProxy.image` is null for a synthetic proxy, **When** the business code later passes through a hooked `InputImage.fromXxx(...)` factory, **Then** fixture injection can still occur at that factory boundary.

### User Story 3 - Configure Custom Frame Hooks (Priority: P2)

As a developer, I want to declare self-owned CV/OCR/QR method signatures in Gradle so debug builds can substitute Bitmap, ByteArray, ByteBuffer, or NV21 arguments without changing business source code.

**Acceptance Scenarios**:

1. **Given** a custom frame hook is declared in Gradle, **When** a matching call site is built in debug, **Then** the plugin rewrites the call to a bridge with a stable target id.
2. **Given** a fixture rule matches the custom target, **When** the call site executes, **Then** the bridge replaces the configured frame argument and calls the original receiver/method with the substituted input.
3. **Given** no fixture matches, **When** the call site executes, **Then** the original call runs unchanged.

## Requirements

- **FR-001**: This spec depends on `012-media-input-foundation` for fixture staging, target discovery, shared protocol, audit, cleanup, and release safety foundation.
- **FR-002**: The feature MUST NOT require business source code changes.
- **FR-003**: The plugin MUST rewrite CameraX `ImageAnalysis.setAnalyzer(Executor, ImageAnalysis.Analyzer)` and `clearAnalyzer()` call sites when enabled.
- **FR-004**: CameraX analyzer wrapping MUST register targets and support fallback to the original analyzer.
- **FR-005**: Camera injection MUST support `replace_on_real_frame`.
- **FR-006**: Camera injection SHOULD support `drive_analyzer` after analyzer wrapping is stable.
- **FR-007**: The runtime MUST support PNG, JPEG, and NV21 fixtures at minimum.
- **FR-008**: The runtime MUST expose `media.camera.injectFrames`, `media.camera.clear`, `media.camera.history`, `media.camera.snapshot`, and `media.camera.assertConsumed`.
- **FR-009**: The plugin MUST rewrite these ML Kit factories when enabled:
  - `InputImage.fromMediaImage(Image, int)`
  - `InputImage.fromBitmap(Bitmap, int)`
  - `InputImage.fromByteArray(byte[], int, int, int, int)`
  - `InputImage.fromByteBuffer(ByteBuffer, int, int, int, int)`
  - `InputImage.fromFilePath(Context, Uri)`
- **FR-010**: ML Kit factory bridges MUST fallback to original factories when no fixture matches.
- **FR-011**: The Gradle DSL SHOULD support configured custom frame hooks for Bitmap, ByteArray, ByteBuffer, and NV21-like method arguments.
- **FR-012**: Release safety MUST reject CameraX, ML Kit, and custom frame bridge calls in release artifacts.

## Non-Goals

- Preview surface replacement.
- Camera2 Surface pipeline replacement.
- Fake camera HAL/provider.
- MediaRecorder video stream replacement.
- Arbitrary native SDK frame ingestion.
- Guaranteed support for code that only consumes `android.media.Image` directly and never passes through a hookable analyzer, ML Kit factory, or configured custom frame method.

## Key Entities

- **CameraTarget**: `MediaTarget` for CameraX analyzer, ML Kit factory, or custom frame hook.
- **CameraFrameFixture**: Image fixture decoded or converted to Bitmap, NV21, YUV-like planes, ByteArray, or ByteBuffer data.
- **CameraInjectionRule**: Rule binding one or more frames to a target.
- **CameraHistoryRecord**: Frame injection, fallback, analyzer clear, snapshot, and consumption record.
- **CustomFrameHook**: Gradle-declared method signature and argument mapping for self-owned frame processors.

## Success Criteria

- **SC-001**: A sample app with normal CameraX analyzer code and no AI debug media imports consumes a fixture frame and passes `media.camera.assertConsumed`.
- **SC-002**: A sample ML Kit-style path receives fixture-backed `InputImage` through a hooked factory.
- **SC-003**: `media.targets.list` returns CameraX and ML Kit targets after call sites are hit.
- **SC-004**: Debug instrumentation verification finds rewritten CameraX/ML Kit call sites.
- **SC-005**: Release safety fails if camera bridge calls appear in release artifacts.
