# Tasks: Media Input Verification Suite

Task ordering is designed to prevent false confidence: build the harness first, prove foundation
media tools second, then prove real audio and camera fixture consumption.

## Phase 1: Spec And Contract

- [x] T001 Create media input verification spec, plan, report contract, quickstart, and tasks docs.
- [x] T002 Update specs index with `015-media-input-verification-suite`.
- [x] T003 Align `quickstart.md` with the finalized script modes, report path, and blocked/failed semantics.
- [x] T004 Align `contracts/media-verification-report.md` with the final report fields used by the script.

## Phase 2: Script Harness And Report Writer

- [x] T005 Add `scripts/spec015-media-input-verification.sh` with `--foundation`, `--audio`, `--camera`, and default full-suite modes.
- [x] T006 Add common shell helpers for logging, failure capture, status aggregation, and section timing.
- [x] T007 Add ADB helpers for serial selection, app install, app launch with extras, permission grant, file push, and file cleanup.
- [x] T008 Add runtime helpers for port forwarding, `runtime.ping`, authenticated POST calls, polling, and response capture.
- [x] T009 Add JSON report writer under `build/ai-debug/media-verification/`.
- [x] T010 Add section status support for `passed`, `failed`, `skipped`, and `blocked`.
- [x] T011 Add trap-based cleanup that runs after success, failure, or interruption.

## Phase 3: Fixtures And Staging

- [x] T012 Add or generate a small deterministic WAV/PCM fixture for audio verification.
- [x] T013 Add or generate a small deterministic NV21 fixture for camera verification.
- [x] T014 Add fixture sha256 calculation and report evidence.
- [x] T015 Push fixtures to `/data/local/tmp/ai-debug-media/015/` before registration.
- [x] T016 Register fixtures with `media.fixture.register` using `devicePath`, `sha256`, `mimeType`, metadata, and tags.
- [x] T017 Verify fixture list/delete behavior and idempotent cleanup for registered fixtures.

## Phase 4: Foundation Verification

- [x] T018 Build and install the debug sample APK.
- [x] T019 Launch the sample app and wait for `runtime.ping`.
- [x] T020 Verify `media.capabilities` includes audio and camera MIME types.
- [x] T021 Verify `media.capabilities` includes `AUDIO_RECORD_READ`, `CAMERA_X_ANALYZER`, and `MLKIT_INPUT_IMAGE_FACTORY`.
- [x] T022 Verify `media.targets.list` response shape before media triggers run.
- [x] T023 Verify structured error handling by attempting audio injection into a missing target.
- [x] T024 Verify structured error handling by attempting camera injection into a missing target.
- [x] T025 Verify media tool events appear in `audit.history`.
- [x] T026 Make foundation mode fail on tool/protocol failures and never claim audio/camera end-to-end success.

## Phase 5: Sample Audio Dogfood Path

- [x] T027 Add `runAudioMediaDogfood=true` handling in `sample-app` intent processing.
- [x] T028 Drive a normal `AudioRecord.read(byte[], int, int)` path from that trigger without AI debug media imports.
- [x] T029 Grant or request `RECORD_AUDIO` safely for the sample debug path.
- [x] T030 Record a simple visible/debug state for the last audio dogfood result when `renderMediaDogfoodState=true`.
- [x] T031 Ensure debug instrumentation report includes rewritten AudioRecord call-site evidence.
- [x] T032 Ensure release safety still rejects audio bridge/runtime leakage.

## Phase 6: Audio End-To-End Verification

- [x] T033 Launch the sample with `runAudioMediaDogfood=true` and poll `media.targets.list(kind=AUDIO_RECORD_READ)`.
- [x] T034 Mark audio `blocked` with target list and instrumentation evidence when no `AUDIO_RECORD_READ` target appears.
- [x] T035 Register the audio fixture and call `media.audio.inject` against the discovered target.
- [x] T036 Trigger the audio path again after injection.
- [x] T037 Call `media.audio.assertConsumed` with `minBytes >= 1` and `minReads >= 1`.
- [x] T038 Verify `media.audio.history` contains records matching the asserted `targetId` and `fixtureId`.
- [x] T039 Clear audio rules with `media.audio.clear` and verify no active rule remains.
- [x] T040 Record audio rule id, restore token, consumed bytes, read count, and history record ids in the report.

## Phase 7: Sample Camera Dogfood Path

- [x] T041 Add `runCameraMediaDogfood=true` handling in `sample-app` intent processing.
- [x] T042 Add the first deterministic camera path using normal ML Kit-like `InputImage` factory code or CameraX analyzer code.
- [x] T043 Avoid AI debug media imports in the sample camera business path.
- [x] T044 Record a simple visible/debug state for the last camera dogfood result when `renderMediaDogfoodState=true`.
- [x] T045 Ensure debug instrumentation report includes rewritten camera call-site evidence.
- [x] T046 Ensure release safety still rejects camera bridge/runtime leakage.

## Phase 8: Camera End-To-End Verification

- [x] T047 Launch the sample with `runCameraMediaDogfood=true` and poll for `CAMERA_X_ANALYZER` or `MLKIT_INPUT_IMAGE_FACTORY`.
- [x] T048 Mark camera `blocked` with target list and instrumentation evidence when no camera target appears.
- [x] T049 Register the image fixture and call `media.camera.injectFrames` against the discovered target.
- [x] T050 Trigger the camera path again after injection.
- [x] T051 Call `media.camera.assertConsumed` with `minFrames >= 1`.
- [x] T052 Verify `media.camera.history` contains records matching the asserted `targetId` and `fixtureId`.
- [x] T053 Verify `media.camera.snapshot` contains target and active or recently cleared rule evidence.
- [x] T054 Clear camera rules with `media.camera.clear` and verify no active rule remains.
- [x] T055 Record camera rule id, restore token, consumed frames, snapshot data, and history record ids in the report.

## Phase 9: Build Safety And Negative Coverage

- [x] T056 Run `:sample-app:verifyAiDebugDebugInstrumentation` from the suite and link its report path.
- [x] T057 Run `:sample-app:checkAiDebugReleaseSafety` from the suite and link its report path.
- [ ] T058 Add optional negative audio bridge leak check if 013 does not already provide one.
- [ ] T059 Add optional negative camera bridge leak check if 014 does not already provide one.
- [x] T060 Make build safety failures fail the full suite even when media consumption passes.

## Phase 10: Optional Daemon Scenario

- [ ] T061 Add `DogfoodScenarios.mediaInputVerification(...)` after the shell script behavior is stable.
- [ ] T062 Add a daemon CLI command or scenario hook for `media-input-verification`.
- [ ] T063 Add daemon unit tests for scenario step order, continue-on-error behavior, and report generation.

## Phase 11: Agent Guidance

- [x] T064 Update `testCase/013-audio-input-control.md` to point agents to `scripts/spec015-media-input-verification.sh --audio`.
- [x] T065 Update `testCase/014-camera-input-control.md` to point agents to `scripts/spec015-media-input-verification.sh --camera`.
- [x] T066 Update `testCase/README.md` with 015 as the serious 013/014 validation entry point.
- [x] T067 Add troubleshooting notes for `blocked` vs `failed`, missing targets, permissions, fixture staging, and release safety failures.

## Phase 12: Validation

- [x] T068 Run `./gradlew :ai-debug-runtime:testDebugUnitTest :ai-debug-daemon:test`.
- [x] T069 Run `./gradlew :sample-app:assembleDebug :sample-app:verifyAiDebugDebugInstrumentation :sample-app:checkAiDebugReleaseSafety`.
- [x] T070 Run `bash scripts/spec015-media-input-verification.sh --foundation` on a connected device.
- [x] T071 Run `bash scripts/spec015-media-input-verification.sh --audio` on a connected device.
- [x] T072 Run `bash scripts/spec015-media-input-verification.sh --camera` on a connected device.
- [x] T073 Run `bash scripts/spec015-media-input-verification.sh` on a connected device.
- [x] T074 Attach the latest media verification report path and summarize passed, blocked, failed, and skipped sections.
