# Tasks: Network Control

**Input**: `spec.md`, `plan.md`, `contracts/network-tools.md`

## Phase 1: Models And Rule Engine

- [x] T001 Define `NetworkRule`, `NetworkMatcher`, `NetworkAction`, `NetworkRecord`, and `ResponsePatch` protocol models.
- [x] T002 Implement deterministic rule store with session scope, priority, call counts, and cleanup.
- [x] T003 [P] Implement JSONPath/JSONPatch response patcher for buffered JSON bodies.

## Phase 2: OkHttp Runtime

- [x] T004 Implement OkHttp application interceptor for request/response recording.
- [x] T005 Implement static mock response action.
- [x] T006 Implement response mutation action.
- [x] T007 Implement delay, timeout, HTTP error, and disconnect actions.
- [x] T008 Add redaction policy hooks for headers and bodies.

## Phase 3: MCP Tools

- [x] T009 Implement `network.history`.
- [x] T010 Implement `network.mutateResponse`.
- [x] T011 Implement `network.mock`.
- [x] T012 Implement `network.fail`.
- [x] T013 Implement `network.clearRules`.
- [x] T014 Implement `network.assertCalled`.

## Phase 4: Validation

- [x] T015 [P] Add MockWebServer tests for static mock and mutation.
- [x] T016 [P] Add tests for rule count and cleanup behavior.
- [x] T017 Add sample app VIP profile fixture and quickstart scenario.
