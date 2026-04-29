# Implementation Plan: Network Control

**Branch**: `002-network-control`  
**Date**: 2026-04-24  
**Spec**: `specs/002-network-control/spec.md`

## Summary

Implement the first high-value AI debugging loop: record app network calls and let AI mutate or replace responses at runtime. Start with OkHttp application interceptors and JSON body mutation.

## Technical Context

| Area | Decision |
| --- | --- |
| Client stack | OkHttp first, Retrofit through OkHttp |
| Rule engine | Runtime in app process, controlled by daemon |
| Body mutation | JSONPath/JSONPatch for JSON responses |
| Storage | In-memory session store for MVP, file export later |
| Testing | MockWebServer, sample app, instrumentation smoke tests |

## Project Structure

```text
ai-debug-runtime/src/main/.../network/
ai-debug-daemon/src/main/.../network/
ai-debug-protocol/src/main/.../network/
sample-app/src/debug/.../NetworkFixture.kt
specs/002-network-control/contracts/
```

## Implementation Notes

- Provide both manual OkHttp builder API and later plugin-assisted insertion.
- Capture body safely with size limits.
- Preserve content type and encoding when mutating bodies.
- Make rule matching deterministic: session scope, priority, created order, remaining count.
- Record original and final response summaries for reports.

## Risks

- Response body consumption must not break app code.
- Compressed/streaming bodies need careful handling; MVP may cap support to buffered JSON bodies.
- Multiple OkHttp clients in one app require either manual installation or plugin-assisted discovery.
