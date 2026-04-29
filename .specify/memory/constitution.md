# Project Constitution

Last updated: 2026-04-24

## Principles

### 1. Debug-Build First

All runtime control features are designed for Android debug or internal test builds with source access. Release builds must use no-op APIs and must fail verification if debug endpoints, sidecar classes, or mutable runtime hooks leak into production artifacts.

### 2. AI-Callable By Design

Every capability exposed to agents must be discoverable through MCP with a typed schema, description, expected side effects, rollback behavior, and audit metadata. Avoid human-only UI workflows as the primary interface.

### 3. Dynamic Debugging Is A Core Capability

Runtime object search, eval, snippet execution, field mutation, method return override, and memory inspection are first-class AI debugging tools. They are not isolated behind a special mode. They still need structured tool contracts, session scoping, audit logs, and cleanup hooks.

### 4. Prefer Stable Semantics, Allow Low-Level Escape Hatches

Typed state, DI overrides, and compile-time probes should be preferred because they are reproducible and explainable. Lower-level eval/object/memory tools must remain available for unknown code paths, exploratory debugging, and AI self-test discovery.

### 5. Reversible Where Possible

Mutations should support snapshot/restore or explicit cleanup. If a tool cannot guarantee restore, its contract must say so and report the residual risk in the audit log.

### 6. Local-Only Control Plane

The default control path is local MCP daemon to app runtime through an ADB local tunnel or equivalent local transport. Use `adb forward` for host-to-app calls and `adb reverse` for app-to-daemon callbacks. Do not expose app debug endpoints on public networks.

### 7. Specs Drive Implementation

New features should start from `specs/NNN-feature/spec.md`, then `plan.md`, then `tasks.md`. Contracts and data models should be captured next to the feature that owns them.

## Governance

- Roadmap changes that alter product scope must update `ROADMAP.md` and any affected feature specs.
- Implementation work should reference task IDs from the relevant `tasks.md`.
- If code behavior and spec diverge, update the spec or fix the code before marking the task complete.
- Security-sensitive tools must include contract-level audit and cleanup requirements before implementation.
