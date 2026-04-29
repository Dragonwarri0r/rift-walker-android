# Feature Specification: Gradle Hook E2E

**Feature Branch**: `009-gradle-hook-e2e`  
**Created**: 2026-04-28  
**Status**: Draft  
**Input**: Close the loop between Gradle ASM instrumentation and runtime `hook.*` tools.

## User Scenarios & Testing

### User Story 1 - Override A Configured Method (Priority: P1)

As an AI agent, I want `hook.overrideReturn` to affect methods configured in the Gradle plugin so that I can force branch conditions such as feature flags without editing source.

**Acceptance Scenarios**:

1. **Given** `aiDebug.overrideMethod("com.example.FeatureFlags#isEnabled()")`, **When** the debug variant is built, **Then** the transformed class contains an `AiDebugHookBridge` call for that method.
2. **Given** a hook return rule is installed, **When** the bridge resolves the method id, **Then** the configured return value is returned before the original method body runs.
3. **Given** a hook throw rule is installed, **When** the bridge resolves the method id, **Then** the configured exception path is triggered.

### User Story 2 - Verify Trace Instrumentation (Priority: P2)

As a developer, I want a build-time verification task that checks debug classes were actually instrumented so instrumentation regressions are caught before manual device testing.

**Acceptance Scenarios**:

1. **Given** `aiDebug.traceMethod(...)` is configured, **When** `verifyAiDebugDebugInstrumentation` runs, **Then** the task reports trace bridge calls for the configured method.
2. **Given** an expected method is missing from transformed classes, **When** verification runs, **Then** the task fails the build with a targeted message.

## Requirements

- **FR-001**: Debug ASM instrumentation MUST emit hook bridge calls for configured override methods.
- **FR-002**: Debug ASM instrumentation MUST emit trace bridge calls for configured trace methods.
- **FR-003**: Release variants MUST remain guarded by release safety and must not include debug runtime classes.
- **FR-004**: The Gradle plugin MUST provide a verification task for enabled debug variants.
- **FR-005**: Runtime bridge calls MUST resolve return and throw hook rules through the same `HookStore` used by MCP tools.

## Key Entities

- **Hook bridge**: `AiDebugHookBridge`, the static bridge called from instrumented app bytecode.
- **Instrumentation verification task**: Gradle task that scans transformed class files for bridge references and method ids.
- **Hook rule**: Runtime return/throw override installed by `hook.overrideReturn` or `hook.throw`.

## Success Criteria

- **SC-001**: Runtime unit tests prove bridge return and throw rules resolve correctly.
- **SC-002**: Sample debug build passes instrumentation verification for configured hook and trace methods.
- **SC-003**: Existing runtime, daemon, sample, and release-safety verification commands pass.
