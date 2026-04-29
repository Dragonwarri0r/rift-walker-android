# Tasks: Audio Input Control

Task ordering should keep runtime behavior ahead of ASM: implement and test controller/read semantics first, then wire call-site rewriting.

## Phase 1: Spec

- [x] T001 Create audio spec, plan, contract, quickstart, and tasks docs.
- [x] T002 Update specs index with `013-audio-input-control`.

## Phase 2: Protocol And MCP

- [x] T003 Add audio injection, clear, history, assertion, and read behavior protocol models.
- [x] T004 Register runtime HTTP routes for `media.audio.*`.
- [x] T005 Register daemon MCP tools for `media.audio.inject`, `media.audio.clear`, `media.audio.history`, and `media.audio.assertConsumed`.
- [ ] T006 Add protocol and daemon tests for audio tool shapes.

## Phase 3: Runtime Audio Controller

- [x] T007 Implement `AudioInputController` and rule store.
- [x] T008 Implement WAV PCM and raw PCM fixture parsing.
- [ ] T009 Implement audio fixture cursor accounting by byte and frame.
- [ ] T010 Implement generated silence, sine, and noise streams.
- [x] T011 Implement audio history records and cleanup hooks.

## Phase 4: AudioRecord Bridge

- [x] T012 Add bridge entrypoints for byte array read overloads.
- [x] T013 Add bridge entrypoints for short array read overloads.
- [x] T014 Add bridge entrypoints for float array read overload.
- [x] T015 Add bridge entrypoints for ByteBuffer read overloads.
- [x] T016 Add bridge entrypoints for `startRecording`, `stop`, and `release`.
- [x] T017 Implement fallback behavior when no rule matches.

## Phase 5: Gradle ASM

- [x] T018 Add audio DSL flags under `mediaInputControl.audio`.
- [x] T019 Rewrite AudioRecord read overload call sites for debuggable variants.
- [x] T020 Rewrite AudioRecord lifecycle call sites for debuggable variants.
- [ ] T021 Add audio call-site id generation and instrumentation report entries.
- [ ] T022 Add verification tests for rewritten AudioRecord call sites.

## Phase 6: Read Semantics

- [ ] T023 Implement blocking and non-blocking mode behavior.
- [ ] T024 Implement EOF, loop, short read, and fallback-on-EOF modes.
- [ ] T025 Implement injected error behavior including delayed `ERROR_DEAD_OBJECT`.
- [ ] T026 Implement frame-size truncation and non-direct ByteBuffer behavior.
- [ ] T027 Add runtime unit tests for read overload semantics and edge cases.

## Phase 7: Sample And Release Safety

- [x] T028 Add sample app AudioRecord path with no AI debug media imports.
- [ ] T029 Add sample audio fixtures.
- [ ] T030 Add built-in audio dogfood scenario.
- [x] T031 Extend release safety to reject audio bridge calls and AudioRecord rewrite leakage.
- [ ] T032 Add `scripts/spec013-audio-smoke.sh`.
- [ ] T033 Add `scripts/spec013-negative-release-audio-leak.sh`.

## Phase 8: Validation

- [x] T034 Run protocol, runtime, daemon, plugin, sample, and release checks.
- [ ] T035 Run audio smoke on a connected device.
