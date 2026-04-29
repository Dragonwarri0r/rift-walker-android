# Feature Specification: Network Record To Mock

**Feature Branch**: `008-network-record-to-mock`  
**Created**: 2026-04-28  
**Status**: Draft  
**Input**: Let AI agents turn a captured successful network response into a reusable static mock rule.

## User Scenarios & Testing

### User Story 1 - Capture A Real Response As A Mock (Priority: P1)

As an AI agent, I want to call a real endpoint once and convert the captured response into a mock rule so that later self-test steps can replay the same branch without backend setup.

**Acceptance Scenarios**:

1. **Given** a successful HTTP response has been captured, **When** the agent calls `network.recordToMock` with the record id, **Then** a static mock rule is installed using the captured status and body.
2. **Given** no explicit target matcher is provided, **When** a record is converted, **Then** the target matcher defaults to the captured method and exact URL.
3. **Given** a target matcher is provided, **When** a record is converted, **Then** the installed mock uses the explicit matcher instead of the exact captured URL.

### User Story 2 - Preserve Safety And Auditability (Priority: P1)

As an app developer, I want generated mocks to use the same redaction and cleanup path as normal network rules so that agents do not accidentally replay raw sensitive data.

**Acceptance Scenarios**:

1. **Given** the captured body contains sensitive fields, **When** a mock is generated, **Then** the generated mock body uses the redacted history body.
2. **Given** `network.recordToMock` installs a rule, **When** cleanup runs, **Then** the generated rule is removed by its restore token.

## Requirements

- **FR-001**: `network.history` SHOULD capture safe response body previews for successful real responses without consuming the response returned to app code.
- **FR-002**: `network.recordToMock` MUST accept a source `recordId` or source matcher.
- **FR-003**: `network.recordToMock` MUST install a normal static mock rule and return its rule id and restore token.
- **FR-004**: Generated mocks MUST use redacted/capped captured bodies, not hidden raw response bodies.
- **FR-005**: Generated mocks MUST support explicit target matchers and default to a derived matcher when omitted.
- **FR-006**: Generated mocks MUST preserve GraphQL and gRPC protocol metadata in the derived matcher when available.

## Key Entities

- **NetworkRecordToMockRequest**: Selects a source record and optional target matcher.
- **NetworkRecordToMockResponse**: Reports the created rule, source record id, derived matcher, status, and body capture/redaction state.
- **Derived NetworkMatcher**: The matcher generated from the source record when the agent does not provide one.

## Success Criteria

- **SC-001**: Unit tests prove a real response can be replayed through a generated mock.
- **SC-002**: Unit tests prove response body capture does not consume the response body returned to app code.
- **SC-003**: Unit tests prove generated mocks use redacted captured bodies.
- **SC-004**: Runtime, daemon, sample, and release-safety verification commands pass.
