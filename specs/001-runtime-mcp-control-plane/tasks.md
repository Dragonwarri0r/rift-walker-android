# Tasks: Runtime MCP Control Plane

**Input**: `spec.md`, `plan.md`, `contracts/mcp-tools.md`

## Phase 1: Setup

- [x] T001 Create repository module skeletons for `ai-debug-protocol`, `ai-debug-runtime`, `ai-debug-runtime-noop`, `ai-debug-daemon`, and `sample-app`.
- [x] T002 Define shared protocol models for runtime identity, debug session, capability descriptor, audit event, and error response.
- [x] T003 [P] Add sample app with debug runtime dependency placeholder.

## Phase 2: Runtime Transport

- [x] T004 Implement in-app debug endpoint with `runtime.ping`.
- [x] T005 Implement runtime debuggable check and mutable-tool disablement for non-debuggable builds.
- [x] T006 Implement session token creation and validation.
- [x] T007 Implement cleanup hook registry.

## Phase 3: Daemon And MCP

- [x] T008 Implement daemon device selection and ADB local tunnel setup.
- [x] T009 Implement MCP tool `runtime.ping`.
- [x] T010 Implement MCP tool `capabilities.list`.
- [x] T011 Implement MCP tool `audit.history`.

## Phase 4: Validation

- [x] T012 Add JVM unit tests for protocol serialization and error shapes.
- [x] T013 Add Android instrumentation smoke test for runtime endpoint.
- [x] T014 Add sample-app quickstart command that starts app, daemon, establishes an ADB local tunnel, and calls `runtime.ping`.
