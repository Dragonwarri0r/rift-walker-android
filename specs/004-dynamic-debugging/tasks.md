# Tasks: Dynamic Debugging Tools

## Phase 1: Object Search MVP

- [x] T001 Define object handle, search result, probe descriptor, eval request, and hook rule models.
- [x] T002 Implement runtime object registry with session-local handles.
- [x] T003 Implement package-scoped reflection search for tracked objects.
- [x] T004 Implement JSON-safe value serialization and redaction/error reasons.

## Phase 2: Eval And Snippets

- [x] T005 Implement `debug.eval` contract with timeout and side-effect metadata.
- [x] T006 Implement minimal expression/snippet environment exposing `env.state`, `env.network`, `env.probe`, and `env.audit`.
- [x] T007 Add cleanup token support for mutating eval calls.

## Phase 3: Probe And Hook

- [x] T008 Implement `probe.getField` for object handles.
- [x] T009 Implement `probe.setField` with restore tokens.
- [x] T010 Implement hook rule store.
- [x] T011 Integrate with Gradle-inserted method hooks when spec `005` is available.

## Phase 4: Validation

- [x] T012 Add sample app object searchable by `vip`.
- [x] T013 Add eval smoke test.
- [x] T014 Add field get/set smoke test.
- [x] T015 Add hook override smoke test once instrumentation is available.
