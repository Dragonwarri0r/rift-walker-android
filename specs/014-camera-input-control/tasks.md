# Tasks: Camera Input Control

Task ordering should prove the known SDK boundaries before custom hooks: CameraX first, ML Kit factories second, configured custom frame hooks third.

## Phase 1: Spec

- [x] T001 Create camera spec, plan, contract, quickstart, and tasks docs.
- [x] T002 Update specs index with `014-camera-input-control`.

## Phase 2: Protocol And MCP

- [x] T003 Add camera injection, clear, history, snapshot, assertion, and frame metadata protocol models.
- [ ] T004 Add custom frame hook protocol/metadata models.
- [x] T005 Register runtime HTTP routes for `media.camera.*`.
- [x] T006 Register daemon MCP tools for `media.camera.injectFrames`, `media.camera.clear`, `media.camera.history`, `media.camera.snapshot`, and `media.camera.assertConsumed`.
- [ ] T007 Add protocol and daemon tests for camera tool shapes.

## Phase 3: Runtime Camera Controller

- [x] T008 Implement `CameraInputController` and rule store.
- [ ] T009 Implement PNG and JPEG fixture decoding.
- [ ] T010 Implement NV21 fixture loading and metadata validation.
- [ ] T011 Implement frame sequence, loop, times, fps, and metadata override behavior.
- [x] T012 Implement camera history and consumption accounting.

## Phase 4: CameraX Bridge

- [x] T013 Add bridge entrypoint for `ImageAnalysis.setAnalyzer`.
- [x] T014 Add bridge entrypoint for `ImageAnalysis.clearAnalyzer`.
- [x] T015 Implement analyzer wrapper and target registration.
- [ ] T016 Implement `replace_on_real_frame` fixture substitution.
- [ ] T017 Implement synthetic ImageProxy for planes, size, crop, format, timestamp, and rotation metadata.
- [ ] T018 Implement `drive_analyzer` mode after wrapper tests pass.

## Phase 5: ML Kit And Custom Frame Hooks

- [x] T019 Add bridge entrypoints for `InputImage.fromMediaImage`, `fromBitmap`, `fromByteArray`, `fromByteBuffer`, and `fromFilePath`.
- [x] T020 Implement fixture-backed `InputImage` creation and fallback behavior.
- [x] T021 Add camera DSL flags under `mediaInputControl.camera`.
- [ ] T022 Add custom frame hook DSL and metadata export.
- [ ] T023 Add bridge behavior for configured Bitmap / ByteArray / ByteBuffer / NV21 frame hooks.

## Phase 6: Gradle ASM

- [x] T024 Rewrite CameraX analyzer call sites for debuggable variants.
- [x] T025 Rewrite ML Kit `InputImage.fromXxx` factory call sites for debuggable variants.
- [ ] T026 Rewrite configured custom frame hook call sites for debuggable variants.
- [ ] T027 Add camera call-site id generation and instrumentation report entries.
- [ ] T028 Add verification tests for rewritten CameraX/ML Kit/custom frame call sites.

## Phase 7: Sample And Release Safety

- [ ] T029 Add sample app CameraX analyzer path with no AI debug media imports.
- [ ] T030 Add sample ML Kit-like image processing path with no AI debug media imports.
- [ ] T031 Add sample camera fixtures.
- [ ] T032 Add built-in camera dogfood scenario.
- [x] T033 Extend release safety to reject camera bridge calls and CameraX/ML Kit rewrite leakage.
- [ ] T034 Add `scripts/spec014-camera-smoke.sh`.
- [ ] T035 Add `scripts/spec014-negative-release-camera-leak.sh`.

## Phase 8: Validation

- [x] T036 Run protocol, runtime, daemon, plugin, sample, and release checks.
- [ ] T037 Run camera smoke on a connected device.
