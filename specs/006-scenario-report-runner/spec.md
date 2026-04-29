# Feature Specification: Scenario Report Runner

**Feature Branch**: `006-scenario-report-runner`  
**Created**: 2026-04-27  
**Status**: Draft  
**Input**: Compose existing runtime MCP tools into reproducible AI self-test scenarios and export reports with audit/network artifacts.

## User Scenarios & Testing

### User Story 1 - Run Reproducible Debug Scenarios (Priority: P1)

As an AI coding agent, I want to run a sequence of runtime debug tool calls so that network mocks, state mutations, hooks, assertions, and restores are executed deterministically.

**Acceptance Scenarios**:

1. **Given** a scenario with multiple valid tool steps, **When** `scenario.run` is called, **Then** each step is executed in order and the response contains per-step status, result, and duration.
2. **Given** a step fails and `continueOnError=false`, **When** the scenario runs, **Then** later steps are skipped and the scenario status is `failed`.
3. **Given** a step has `continueOnError=true`, **When** that step fails, **Then** later steps continue and the failed step is preserved in the report.

### User Story 2 - Generate Agent-Readable Reports (Priority: P1)

As an app developer, I want a report artifact containing scenario steps, audit events, network history, and errors so that I can inspect what an AI changed and replay the case.

**Acceptance Scenarios**:

1. **Given** a scenario has completed, **When** `report.generate` is called with its run id, **Then** a JSON report file is written under the daemon report directory.
2. **Given** audit or network artifact collection fails, **When** a report is generated, **Then** the report still writes and includes artifact errors.

## Requirements

- **FR-001**: The daemon MUST expose `scenario.run`.
- **FR-002**: The daemon MUST expose `report.generate`.
- **FR-003**: Scenario steps MUST call existing whitelisted daemon tools by name.
- **FR-004**: Scenario responses MUST include run id, scenario name, status, timestamps, duration, and per-step result/error.
- **FR-005**: Scenario execution MUST stop on the first failed step unless the step or request explicitly opts into continuing.
- **FR-006**: Reports MUST be written as JSON artifacts and include the scenario run when available.
- **FR-007**: Reports SHOULD collect `audit.history` and `network.history` through the same MCP tool layer rather than bypassing tool boundaries.
- **FR-008**: Recursive scenario execution MUST be rejected.

## Key Entities

- **ScenarioRunRequest**: A named ordered list of tool steps.
- **ScenarioStep**: A single tool name and JSON arguments payload.
- **ScenarioRunResponse**: Execution result for the whole scenario.
- **ScenarioStepResult**: Execution result for a single step.
- **ScenarioReport**: JSON artifact combining scenario, audit, network, and collection errors.

## Success Criteria

- **SC-001**: `scenario.run` can execute a two-step fake scenario in daemon tests.
- **SC-002**: A failing scenario stops by default and records the failure.
- **SC-003**: `report.generate` writes a JSON report for a stored run.
- **SC-004**: Existing runtime, daemon, and sample app verification commands still pass.
