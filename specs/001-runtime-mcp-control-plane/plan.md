# Implementation Plan: Runtime MCP Control Plane

**Branch**: `001-runtime-mcp-control-plane`  
**Date**: 2026-04-24  
**Spec**: `specs/001-runtime-mcp-control-plane/spec.md`

## Summary

Create the shared control plane that lets AI agents connect to a debuggable Android app through a local MCP daemon. This feature owns sessions, runtime identity, capability discovery, audit events, cleanup hooks, and the base app-runtime transport.

## Technical Context

| Area | Decision |
| --- | --- |
| Runtime | Android AAR, API 26+ |
| Daemon | Kotlin/JVM preferred; TypeScript acceptable if MCP SDK pressure wins |
| Transport | ADB local tunnel + in-app HTTP/WebSocket endpoint |
| Protocol | JSON schemas shared by daemon and runtime |
| MCP | `runtime.*`, `capabilities.*`, `audit.*`, `session.*` tools |
| Testing | JVM unit tests, Android instrumentation tests, sample app smoke test |

## Project Structure

```text
ai-debug-protocol/
ai-debug-runtime/
ai-debug-runtime-noop/
ai-debug-daemon/
sample-app/
specs/001-runtime-mcp-control-plane/
```

## Implementation Notes

- Keep the runtime endpoint minimal: health, capabilities, invoke tool, audit export, cleanup.
- Use session ids and short-lived tokens even though the default transport is local.
- Do not bind the app endpoint to public device interfaces by default.
- Define error codes early so agents can recover from expected failures.

## Risks

- MCP SDK maturity on JVM may push the daemon toward TypeScript. Keep protocol models independent of SDK choice.
- ADB tunnel setup can fail on multi-device machines. Device selection must be explicit.
- Cleanup hooks need best-effort behavior and clear failure reporting.
