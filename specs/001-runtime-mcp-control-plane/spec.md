# Feature Specification: Runtime MCP Control Plane

**Feature Branch**: `001-runtime-mcp-control-plane`  
**Created**: 2026-04-24  
**Status**: Draft  
**Input**: Build the local daemon and in-app runtime connection that all AI debugging tools depend on.

## User Scenarios & Testing

### User Story 1 - Connect AI Agent To Debug App (Priority: P0)

As an app developer using an AI coding tool, I want the AI to connect to my running debug app through a local MCP daemon so it can call debug tools without manual device setup.

**Why this priority**: No other feature can work until the daemon can discover and communicate with the app runtime.

**Independent Test**: Start the sample app, start the daemon, establish an ADB local tunnel, call `runtime.ping`, and receive app identity plus runtime version.

**Acceptance Scenarios**:

1. **Given** a debuggable Android app with the runtime AAR installed, **When** the daemon starts a session through an ADB local tunnel, **Then** `runtime.ping` returns package name, process id, app debuggable status, runtime version, and session id.
2. **Given** a non-debuggable app or release build, **When** the daemon attempts to connect, **Then** the runtime refuses to expose mutable tools.

### User Story 2 - Discover Capabilities (Priority: P0)

As an AI agent, I want to list available runtime capabilities before mutating anything so I can choose stable typed tools or lower-level debug tools as needed.

**Independent Test**: Register one state capability and one debug capability in the sample app, then call `capabilities.list`.

**Acceptance Scenarios**:

1. **Given** registered capabilities, **When** the agent calls `capabilities.list`, **Then** the response includes path, kind, type/schema, mutability, restore behavior, and audit policy.
2. **Given** no app-specific capabilities, **When** the agent calls `capabilities.list`, **Then** built-in runtime tools still appear.

### User Story 3 - Audit Runtime Actions (Priority: P1)

As a developer, I want every AI-initiated mutation or inspection to be recorded so I can understand what the agent changed during self-test.

**Independent Test**: Call one read tool and one mutation tool, then export the audit log.

**Acceptance Scenarios**:

1. **Given** a running session, **When** the AI calls a mutating tool, **Then** the audit log records tool name, arguments summary, affected capability, result, restore token if any, and timestamp.
2. **Given** a session ends, **When** cleanup runs, **Then** session-scoped rules and restore hooks are executed.

## Requirements

- **FR-001**: The daemon MUST expose MCP tools for session setup, runtime ping, capability discovery, audit export, and cleanup.
- **FR-002**: The runtime MUST only start mutable tools when `ApplicationInfo.FLAG_DEBUGGABLE` is true.
- **FR-003**: The daemon MUST connect through local transport by default, using `adb forward` for host-to-app endpoint calls and `adb reverse` for app-to-daemon callbacks.
- **FR-004**: Every tool response MUST include success/failure status and a stable error code on failure.
- **FR-005**: Every mutating tool MUST emit an audit event.
- **FR-006**: The runtime MUST support session-scoped cleanup hooks.
- **FR-007**: Capability descriptors MUST be JSON-serializable and versioned.

## Key Entities

- **DebugSession**: A daemon-to-runtime connection with session id, package, device, token, start time, and cleanup hooks.
- **CapabilityDescriptor**: Describes a tool or app capability the AI can inspect or invoke.
- **AuditEvent**: Records AI tool calls, side effects, restore tokens, and cleanup results.
- **RuntimeIdentity**: Package name, process id, app version, runtime version, API level, and debuggable status.

## Success Criteria

- **SC-001**: A sample app can answer `runtime.ping` in under 1 second on a local emulator after an ADB local tunnel is established.
- **SC-002**: `capabilities.list` returns built-in and app-registered capabilities with stable schemas.
- **SC-003**: At least one read call and one mutation call appear in `audit.history`.
- **SC-004**: Release/no-op runtime does not expose mutable endpoints.

## Assumptions

- The first daemon implementation may be Kotlin/JVM to share Android-oriented models and tooling.
- MCP protocol support can use a Java/Kotlin-compatible SDK if mature enough, or a thin MCP server adapter otherwise.
- Authentication is local session token based for MVP.
