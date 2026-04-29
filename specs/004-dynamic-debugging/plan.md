# Implementation Plan: Dynamic Debugging Tools

**Branch**: `004-dynamic-debugging`  
**Date**: 2026-04-24  
**Spec**: `specs/004-dynamic-debugging/spec.md`

## Summary

Make dynamic debugging an AI-callable product surface. The first implementation should be registry-assisted and compile-time-assisted because the app is built from source. Deeper heap/native inspection can follow.

## Technical Context

| Area | Decision |
| --- | --- |
| Object search MVP | Registry-assisted object handles + reflection for selected packages |
| Eval MVP | Small expression/snippet runner with timeout and JSON-safe result |
| Probe MVP | Compile-time symbol index plus selected field get/set |
| Hook MVP | Method return/throw override through inserted hook points |
| Deep instrumentation | JVMTI/Frida/native-agent later |

## Implementation Notes

- Object search should initially track objects registered by runtime, DI helpers, lifecycle hooks, and instrumentation.
- Reflection should be package-scoped and should report inaccessible fields rather than crashing.
- Eval should start with a constrained environment but still be exposed as a first-class MCP tool.
- Snippet runner can compile Kotlin/Java to dex later; initial eval may use a simpler expression engine if faster.
- Field writes and method hooks should always create audit events and best-effort restore tokens.

## Risks

- Full heap walking can be slow and difficult on ART. Do not make full heap search block MVP.
- Eval can create non-restorable mutations. Tool responses must surface cleanup status.
- Kotlin property names and backing fields need mapping from metadata or symbol index.
