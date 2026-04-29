# Feature Specification: Dogfood Self-Test Flow

**Feature Branch**: `011-dogfood-self-test`  
**Created**: 2026-04-28  
**Status**: Draft  
**Input**: Prove the AI debugging extension works as a real agent self-test loop, not only as independent modules.

## User Scenarios & Testing

### User Story 1 - Run A Sample AI Self-Test (Priority: P1)

As an app developer, I want one reproducible dogfood command that drives the sample app through network mock, state mutation, method hook, ADB app launch, assertion, cleanup, and report generation so I can validate the whole loop before connecting an AI coding tool.

**Acceptance Scenarios**:

1. **Given** the sample app is installed on a connected device, **When** `dogfood-sample` runs, **Then** the daemon forwards the runtime port, launches the app, waits for runtime readiness, and executes a scenario.
2. **Given** the scenario installs a profile network mock, **When** the sample app fetches the profile, **Then** `network.assertCalled` passes without a fixed sleep.
3. **Given** the scenario mutates `user.isVip`, storage, overrides, and a method hook, **When** the scenario completes, **Then** cleanup steps clear the installed mock, override, hook, and sample state.
4. **Given** the scenario finishes, **When** a report is generated, **Then** the report includes ordered step results, audit history, and network history.

## Requirements

- **FR-001**: The daemon MUST expose ADB-backed tools for listing devices, forwarding ports, force-stopping apps, and launching an activity with simple extras.
- **FR-002**: The daemon MUST expose `runtime.waitForPing` to poll runtime readiness after app launch.
- **FR-003**: `network.assertCalled` MUST support an optional timeout to wait for asynchronous app requests.
- **FR-004**: The daemon MUST include a built-in sample dogfood scenario that combines app, runtime, network, state, storage, override, hook, assertion, and cleanup tools.
- **FR-005**: A script MUST build and install the sample app, run the dogfood scenario, and fail if the scenario report status is not `passed`.

## Non-Goals

- UIAutomator or screenshot validation.
- General-purpose YAML scenario file parsing.
- Full CI emulator provisioning.

## Key Entities

- **App tools**: Daemon MCP tools that wrap safe ADB operations.
- **Dogfood scenario**: Built-in sample scenario for `com.riftwalker.sample`.
- **Dogfood report**: JSON report generated after the scenario run.

## Success Criteria

- **SC-001**: `:ai-debug-daemon:test` proves the dogfood scenario shape and wait behavior.
- **SC-002**: `scripts/spec011-dogfood.sh` passes on a connected API 26+ device with the sample app.
- **SC-003**: Existing runtime, daemon, sample instrumentation, and release-safety checks still pass.
