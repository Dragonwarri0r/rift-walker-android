# Implementation Plan: Scenario Report Runner

**Branch**: `006-scenario-report-runner`  
**Date**: 2026-04-27  
**Spec**: `specs/006-scenario-report-runner/spec.md`

## Summary

Add a daemon-side scenario runner that invokes existing MCP tools by name, captures ordered step results, stores recent runs in memory, and emits JSON report artifacts. This keeps orchestration outside the app process while preserving runtime audit logs and session-token enforcement.

## Technical Context

| Area | Decision |
| --- | --- |
| Execution location | `ai-debug-daemon` |
| Step execution | Existing `DaemonToolRegistry.invoke` |
| Scenario format | JSON model first; YAML can be a CLI/parser follow-up |
| Report format | JSON file under `build/ai-debug/reports` by default |
| Artifact collection | Use `audit.history` and `network.history` tools |
| Persistence | In-memory run store for MVP |

## Implementation Notes

- Register scenario/report tools after runtime tools so the runner can invoke the full tool registry.
- Keep recursion out of MVP by rejecting `scenario.run` as a scenario step.
- Avoid special casing network/state/hook steps. Scenario semantics should come from existing tool contracts.
- Generate report ids and run ids with UUIDs.
- Keep report generation best-effort: artifact collection errors should not prevent a report file from being written.

## Risks

- Long-running scenarios may eventually need cancellation and timeouts.
- YAML support should use a real parser rather than ad hoc indentation parsing.
- Persistent run storage may be needed for CI artifacts across daemon restarts.
