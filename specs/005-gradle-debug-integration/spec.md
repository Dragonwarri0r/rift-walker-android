# Feature Specification: Gradle Debug Integration

**Feature Branch**: `005-gradle-debug-integration`  
**Created**: 2026-04-24  
**Status**: Draft  
**Input**: Provide source-build integration through a Gradle plugin, debug AAR injection, KSP/codegen, ASM instrumentation, and release safety checks.

## User Scenarios & Testing

### User Story 1 - One-Line Debug Integration (Priority: P1)

As an app developer, I want to apply a Gradle plugin and get the debug runtime wired into debug builds while release builds get no-op APIs.

**Acceptance Scenarios**:

1. **Given** an Android app module applies the plugin, **When** it builds a debug variant, **Then** the runtime AAR is available.
2. **Given** the app builds a release variant, **When** the safety check runs, **Then** debug runtime classes and endpoints are absent.

### User Story 2 - Generate Capabilities From Source (Priority: P1)

As a developer, I want annotations such as `@AiState`, `@AiAction`, and `@AiProbe` to generate runtime registries and capability schema.

### User Story 3 - Insert Probe And Hook Points (Priority: P1)

As an AI agent, I want field/method probes and method override hooks to exist at runtime without hand-editing every class.

## Requirements

- **FR-001**: The plugin MUST support AGP 8.12/8.13 first.
- **FR-002**: The plugin MUST wire debug runtime and release no-op dependencies.
- **FR-003**: The plugin MUST export capability schema artifacts.
- **FR-004**: The plugin MUST support KSP/codegen for annotations.
- **FR-005**: The plugin MUST support AGP Instrumentation API / ASM for trace and hook insertion.
- **FR-006**: The plugin MUST fail release checks if debug runtime leaks into release artifacts.
- **FR-007**: The plugin SHOULD allow package-level and annotation-level instrumentation selection.

## Key Entities

- **GradleExtension**: User configuration for packages, probes, hooks, generated schema, and safety checks.
- **GeneratedRegistry**: Generated source that registers annotated state/action/probe capabilities.
- **SymbolIndex**: Build artifact listing searchable classes, fields, methods, and annotations.
- **ReleaseCheckReport**: Build report for forbidden runtime artifacts.

## Success Criteria

- **SC-001**: A sample app can apply the plugin and build debug/release variants.
- **SC-002**: `@AiState` generates a capability visible to `capabilities.list`.
- **SC-003**: An instrumented method can be traced or overridden through MCP.
- **SC-004**: A release build fails if debug runtime classes are intentionally injected.
