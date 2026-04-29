# Feature Specification: Media Input Foundation

**Feature Branch**: `012-media-input-foundation`  
**Created**: 2026-04-29  
**Status**: Draft  
**Input**: Provide the shared business-transparent media infrastructure used by audio and camera input control: debug-only dependency wiring, stable call-site target discovery, fixture staging, runtime bridge foundation, MCP fixture tools, audit, cleanup, and release safety baselines.

## User Scenarios & Testing

### User Story 1 - Discover Media Targets (Priority: P1)

As an AI agent, I want runtime-discovered media targets so I can inject fixtures into business code without requiring source annotations or AI debug imports.

**Acceptance Scenarios**:

1. **Given** a debug build rewrites a supported media call site, **When** the app executes that call site, **Then** the runtime registers a `MediaTarget` with stable `targetId`, `callSiteId`, kind, API signature, hit count, and observed metadata.
2. **Given** one or more media call sites have been hit, **When** the agent calls `media.targets.list`, **Then** the matching targets are returned and can be filtered by kind or query.
3. **Given** the app has not hit any media call site, **When** `media.targets.list` is called, **Then** the response is empty and recoverable.

### User Story 2 - Stage Media Fixtures (Priority: P1)

As an AI agent, I want to register audio and image fixtures without embedding large binary payloads in JSON.

**Acceptance Scenarios**:

1. **Given** a host fixture file, **When** `media.fixture.register` is called through the daemon, **Then** the daemon computes sha256, pushes the file to the device, and registers a runtime fixture record.
2. **Given** a fixture is registered, **When** runtime validates it, **Then** sha256, size, MIME type, and declared metadata are stored and exposed through `media.fixture.list`.
3. **Given** a session cleanup runs, **When** fixture cleanup hooks execute, **Then** session-scoped fixture records are removed and staged files can be deleted.

### User Story 3 - Share Release Safety Across Media Features (Priority: P1)

As a developer, I want release builds to reject media runtime leakage before audio or camera feature-specific checks run.

**Acceptance Scenarios**:

1. **Given** a release variant is built, **When** `checkAiDebugReleaseSafety` runs, **Then** no media runtime foundation classes, fixture staging code, or media bridge base classes are present.
2. **Given** a release artifact intentionally leaks a media runtime foundation class, **When** the safety task runs, **Then** the build fails and the JSON report lists the leak.

## Requirements

- **FR-001**: The foundation MUST NOT require business source code to import AI debug APIs, implement AI debug interfaces, or branch on debug state.
- **FR-002**: The foundation MUST provide common protocol models for `MediaTarget`, `MediaFixture`, media history, fixture registration, and media assertion responses.
- **FR-003**: Every media bridge hit MUST register or update a target with a stable `targetId`.
- **FR-004**: `targetId` SHOULD be derived from owner class, method name, method descriptor, bytecode instruction index, and API signature.
- **FR-005**: The daemon MUST stage large fixture files with ADB file transfer rather than JSON payloads.
- **FR-006**: Runtime fixture registration MUST validate sha256 and preserve declared metadata.
- **FR-007**: The runtime MUST expose `media.capabilities`, `media.targets.list`, `media.fixture.register`, `media.fixture.list`, and `media.fixture.delete`.
- **FR-008**: The daemon MUST expose matching MCP tools and route fixture staging through the selected ADB device.
- **FR-009**: Fixture and target operations MUST emit audit events and cleanup hooks where appropriate.
- **FR-010**: The Gradle plugin MUST expose a shared `mediaInputControl` DSL root used by later audio and camera specs.
- **FR-011**: Release safety defaults MUST include media foundation forbidden classes and staged fixture logic.

## Non-Goals

- Implementing AudioRecord read substitution. See `013-audio-input-control`.
- Implementing CameraX, ML Kit, or custom frame substitution. See `014-camera-input-control`.
- Replacing preview surfaces, MediaRecorder streams, Camera2 Surface pipelines, fake HAL providers, or arbitrary native SDK ingestion.
- Defining source adapters as an MVP requirement.

## Key Entities

- **MediaTarget**: Runtime-discovered hook target produced by a media bridge hit.
- **MediaFixture**: Staged media asset with id, device path, sha256, MIME type, size, metadata, and lifecycle state.
- **MediaHistoryRecord**: Shared audit/history shape used by audio and camera records.
- **MediaCapabilities**: Runtime feature descriptor reporting which media hook families are available.
- **MediaInputControlExtension**: Gradle DSL root for enabling audio/camera/custom media rewriting in debuggable variants.

## Success Criteria

- **SC-001**: `media.fixture.register`, `media.fixture.list`, and `media.fixture.delete` work through daemon MCP and runtime HTTP.
- **SC-002**: A synthetic bridge unit test can register a `MediaTarget`, and `media.targets.list` returns it.
- **SC-003**: Fixture registration rejects sha256 mismatches and missing staged files.
- **SC-004**: Release safety fails when media foundation runtime classes or fixture staging code leak into release artifacts.
