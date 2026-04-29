# Feature Specification: Network Control

**Feature Branch**: `002-network-control`  
**Created**: 2026-04-24  
**Status**: Draft  
**Input**: Let AI agents record, mock, mutate, delay, fail, replay, and assert Android app network calls during debug self-test.

## User Scenarios & Testing

### User Story 1 - Mutate API Response Fields (Priority: P0)

As an AI agent testing an app branch, I want to change a response field such as `isVip` without editing backend mocks or rebuilding the app.

**Independent Test**: Call `network.mutateResponse` for `/profile`, reload the app route with any UI tool, and verify network history shows the patched response.

**Acceptance Scenarios**:

1. **Given** an OkHttp request to `/api/profile`, **When** the agent patches `$.data.isVip` to `false`, **Then** the app receives the modified body and the audit log records the mutation.
2. **Given** a patch rule with `times: 1`, **When** the request is made twice, **Then** only the first matching response is mutated.

### User Story 2 - Mock And Fail Network Calls (Priority: P0)

As an AI agent, I want to return success, error, timeout, delay, or disconnect responses to cover edge cases quickly.

**Independent Test**: Mock `/checkout/quote` as HTTP 500 and assert the request was called.

**Acceptance Scenarios**:

1. **Given** a static mock rule, **When** a matching request occurs, **Then** the app receives the configured status, headers, body, and delay.
2. **Given** a failure rule, **When** a matching request occurs, **Then** the interceptor simulates timeout or disconnect according to the rule.

### User Story 3 - Record To Mock (Priority: P1)

As a developer, I want the agent to capture a real response and turn it into a reusable mock template.

**Independent Test**: Execute a real request, call `network.recordToMock`, then replay with network disabled or backend unavailable.

## Requirements

- **FR-001**: The runtime MUST record request and response metadata for OkHttp calls.
- **FR-002**: The runtime MUST support matchers for method, URL regex, headers, body substring, and scenario scope.
- **FR-003**: The runtime MUST support static mock responses.
- **FR-004**: The runtime MUST support JSONPath/JSONPatch response mutation for JSON bodies.
- **FR-005**: The runtime MUST support delay, timeout, HTTP error, and disconnect simulation.
- **FR-006**: Rules MUST support call count limits and session scoping.
- **FR-007**: The daemon MUST expose network history, rule creation, rule clearing, and assertions through MCP.
- **FR-008**: Sensitive headers and bodies MUST be redactable.

## Key Entities

- **NetworkRule**: Match conditions plus action: mock, mutate, delay, fail, or assert.
- **NetworkRecord**: Captured request/response metadata, timing, body preview, redaction status, and matched rule ids.
- **ResponsePatch**: JSONPath/JSONPatch operation applied to a response body.
- **NetworkAssertion**: A check that a request matching criteria was called.

## Success Criteria

- **SC-001**: AI can switch `/profile` response between VIP and non-VIP without rebuilding the app.
- **SC-002**: AI can simulate checkout timeout and HTTP 500 through MCP.
- **SC-003**: Network history shows original and mutated response metadata.
- **SC-004**: Rules can be cleared by session cleanup.

## Assumptions

- MVP targets OkHttp and Retrofit-over-OkHttp.
- GraphQL, gRPC, protobuf, and WebSocket are future extensions unless the target app requires them immediately.
