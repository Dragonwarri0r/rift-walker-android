# Feature Specification: Dynamic Debugging Tools

**Feature Branch**: `004-dynamic-debugging`  
**Created**: 2026-04-24  
**Status**: Draft  
**Input**: Provide AI-callable runtime object search, eval, snippets, field/method probes, hooks, and memory inspection for debug apps.

## User Scenarios & Testing

### User Story 1 - Search Runtime Objects (Priority: P1)

As an AI agent exploring unknown app code, I want to search runtime objects by class name, field name, or value so I can locate the state that controls a branch.

**Acceptance Scenarios**:

1. **Given** selected app packages are enabled for debug search, **When** the agent searches `vip`, **Then** matching tracked objects, fields, and capabilities are returned.
2. **Given** a result id, **When** the agent reads a field, **Then** the runtime returns a JSON-safe representation or a redaction/error reason.

### User Story 2 - Evaluate Code In The App Process (Priority: P1)

As an AI agent, I want to run small app-process expressions or snippets to inspect and mutate state when no typed capability exists yet.

**Acceptance Scenarios**:

1. **Given** a debug session, **When** the agent calls `debug.eval`, **Then** the runtime evaluates the expression/snippet in app context and returns JSON-safe output.
2. **Given** an eval mutates state, **When** cleanup runs, **Then** registered cleanup hooks execute and audit records the mutation.

### User Story 3 - Probe Fields And Hook Methods (Priority: P1)

As an AI agent, I want to get/set selected fields and override method returns so I can quickly cover branches like feature flag or VIP checks.

## Requirements

- **FR-001**: The runtime MUST expose object search as a normal MCP tool.
- **FR-002**: The runtime MUST expose eval/snippet execution as normal MCP tools.
- **FR-003**: Search and eval tools MUST emit audit events.
- **FR-004**: Field read/write and method hooks MUST support compile-time instrumentation where possible.
- **FR-005**: The runtime MUST return JSON-safe values or structured reasons for unsupported values.
- **FR-006**: Mutating tools MUST provide cleanup hooks or report that cleanup is not guaranteed.
- **FR-007**: Deep heap/native search MAY be implemented after registry-assisted object search.

## Key Entities

- **ObjectHandle**: Stable session-local reference to a tracked object.
- **ObjectSearchResult**: Match result with class, field path, value preview, and read/write support.
- **EvalRequest**: Expression or snippet with language, timeout, side-effect policy, and expected output schema.
- **ProbeDescriptor**: Compile-time or runtime-discovered field/method probe.
- **HookRule**: Runtime method return/throw override.

## Success Criteria

- **SC-001**: AI can search for `vip`, find a relevant runtime object or probe, and read a field.
- **SC-002**: AI can run a minimal eval/snippet to inspect app state.
- **SC-003**: AI can set a selected field or override a selected method return in the sample app.
- **SC-004**: All debug actions appear in the audit log.
