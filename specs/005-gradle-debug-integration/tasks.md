# Tasks: Gradle Debug Integration

## Phase 1: Plugin Skeleton

- [x] T001 Create Gradle plugin module and extension model.
- [x] T002 Add variant-aware debug runtime/no-op dependency wiring.
- [x] T003 Add sample app plugin configuration.

## Phase 2: Codegen

- [x] T004 Define annotations `@AiState`, `@AiSetter`, `@AiAction`, and `@AiProbe`.
- [x] T005 Implement generated registry for annotated capabilities.
- [x] T006 Export `debug-capabilities.json`.

## Phase 3: ASM Instrumentation

- [x] T007 Implement method trace insertion for configured methods.
- [x] T008 Implement method return/throw hook point insertion.
- [x] T009 Implement field probe metadata generation.
- [x] T010 Export `probe-symbol-index.json`.

## Phase 4: Release Safety

- [x] T011 Implement forbidden class/resource/manifest scanner.
- [x] T012 Emit `release-safety-report.json`.
- [x] T013 Add negative test proving release build fails when debug runtime leaks.
