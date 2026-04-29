# Feature Specification: Runtime Instrumentation Smoke

**Feature Branch**: `010-runtime-instrumentation-smoke`  
**Created**: 2026-04-28  
**Status**: Draft  
**Input**: Close the remaining Android device validation gap for the runtime HTTP endpoint.

## User Scenarios & Testing

### User Story 1 - Verify Runtime Endpoint On Device (Priority: P1)

As an app developer, I want a tiny instrumented smoke test for the debug runtime endpoint so regressions in the Android socket path, session token boundary, and UTF-8 request parsing are caught before MCP clients depend on them.

**Acceptance Scenarios**:

1. **Given** the runtime is started in a debug Android test process, **When** `/runtime/ping` is called, **Then** the endpoint returns package identity, debuggable status, session id, and a session token.
2. **Given** no session token is provided, **When** a protected runtime endpoint is called, **Then** the endpoint rejects the call with HTTP 401.
3. **Given** a valid session token and a multibyte JSON body, **When** `/capabilities/list` is called, **Then** the body is read as UTF-8 bytes and the request succeeds.
4. **Given** a valid session token, **When** a mutable endpoint such as `/network/clearRules` is called, **Then** the request is accepted and returns JSON.

## Requirements

- **FR-001**: The runtime module MUST provide an Android instrumentation smoke test that can run on API 26+ debug devices.
- **FR-002**: The smoke test MUST start the in-app HTTP endpoint on an ephemeral localhost port to avoid port collisions.
- **FR-003**: The smoke test MUST verify that protected endpoints require `X-Ai-Debug-Token`.
- **FR-004**: The smoke test MUST send a UTF-8 JSON request containing non-ASCII characters to protect against byte/character body parsing regressions.
- **FR-005**: The smoke test MUST stop the runtime endpoint after execution.

## Non-Goals

- UIAutomator, Compose, or View hierarchy testing.
- Full daemon-to-device ADB tunnel validation.
- Full scenario runner execution.

## Key Entities

- **Runtime endpoint**: The in-app localhost HTTP server implemented by `RuntimeHttpEndpoint`.
- **Session token**: The per-session control-plane token returned by `runtime.ping` and required by protected endpoints.
- **Instrumentation smoke test**: An Android `androidTest` case that validates endpoint behavior inside a real Android runtime.

## Success Criteria

- **SC-001**: `:ai-debug-runtime:assembleDebugAndroidTest` compiles.
- **SC-002**: `:ai-debug-runtime:connectedDebugAndroidTest` can run on a connected API 26+ debug device or emulator.
- **SC-003**: Spec001 task `T013` is complete.
